package com.insight.router;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.insight.router.proto.*;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class CatalogTest {
    final Catalog catalog = new Catalog(JsonMapper.builder().build());

    @Test
    void missingFilmAndCastIdsAreRejected() {
        assertThatThrownBy(() -> catalog.castMember("99"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> catalog.film("99")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void preservesShipsAndLinksCharactersToFilms() {
        assertThat(catalog.starships("film:1")).hasSize(5);
        assertThat(catalog.starships("cast:6")).hasSize(19);
        assertThat(catalog.starships("cast:1")).hasSize(8);
        assertThat(catalog.starships("none")).isEmpty();
        var falcon = catalog.starships("ship:5").getFirst();
        assertThat(falcon.maxSpeed()).isEqualTo(1050);
        assertThat(falcon.length()).isEqualTo(34.37);
        assertThat(falcon.films()).extracting(Catalog.Film::id).containsExactly("1", "2", "3");
        assertThat(catalog.films().get(2).cast()).hasSize(12);
        assertThatThrownBy(() -> catalog.starships("all").clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(catalog.manualTarget("Which films feature Luke Skywalker?")).isEqualTo("cast:1");
        assertThat(catalog.manualTarget("Enterprise")).isEqualTo("none");
    }

    @Test
    void canonicalRelationshipsAndUnknownTargets() {
        assertThat(catalog.starships("all")).hasSize(19);
        assertThat(
                        catalog.starships("ship:5")
                                .getFirst()
                                .films()
                                .getFirst()
                                .cast()
                                .getFirst()
                                .name())
                .isEqualTo("Luke Skywalker");
        assertThat(catalog.films()).hasSize(6);
        assertThat(catalog.cast()).hasSize(18);
        assertThatThrownBy(() -> catalog.starships("bad"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void trackerHandlesCompletionCancellationAndBadTargets() {
        @SuppressWarnings("unchecked")
        StreamObserver<TrackSnapshot> output = mock(StreamObserver.class);
        var tracker =
                new TrackerService(
                        catalog, new FlightSimulation(catalog, JsonMapper.builder().build(), 20));
        var stream = tracker.track(output);
        stream.onNext(TrackRequest.newBuilder().setTarget("ship:7").setSequence(1).build());
        stream.onCompleted();
        stream.onCompleted();
        stream.onNext(TrackRequest.getDefaultInstance());
        verify(output, times(1)).onNext(any());
        verify(output, times(1)).onCompleted();
        var bad = tracker.track(output);
        bad.onNext(TrackRequest.newBuilder().setTarget("missing").build());
        bad.onCompleted();
        verify(output).onError(any());
        var cancelled = tracker.track(output);
        cancelled.onError(new RuntimeException());
        cancelled.onNext(TrackRequest.getDefaultInstance());
        verify(output, times(1)).onNext(any());
    }
}
