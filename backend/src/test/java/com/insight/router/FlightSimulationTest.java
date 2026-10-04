package com.insight.router;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.insight.router.proto.*;
import io.grpc.stub.ServerCallStreamObserver;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class FlightSimulationTest {
    final JsonMapper mapper = JsonMapper.builder().build();
    final Catalog catalog = new Catalog(mapper);

    @Test
    void scenarioIsRepeatableBoundedAndSeparateFromHistoricalCrew() {
        var flight = new FlightSimulation(catalog, mapper, 2000);
        assertThat(flight.supports("ship:5")).isTrue();
        assertThat(flight.supports("crew:5:1")).isFalse();
        assertThat(flight.supports("ship:7")).isFalse();
        assertThat(flight.frame(0)).isEqualTo(flight.frame(0));
        assertThat(flight.frame(20).getDurationMs()).isEqualTo(40000);
        assertThat(flight.frame(20).getPhase()).isEqualTo("Arrived");
        assertThat(flight.frame(5).getPilot().getName()).isEqualTo("Chewbacca");
        assertThat(catalog.starships("crew:5:1").getFirst().crew().getFirst().member().name())
                .isEqualTo("Han Solo");
        assertThatThrownBy(() -> new FlightSimulation(catalog, mapper, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FlightSimulation(catalog, mapper, 5001))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(StarshipTrackerGrpc.getWatchMethod().getType())
                .isEqualTo(io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING);
        assertThat(StarshipTrackerGrpc.getTrackMethod().getType())
                .isEqualTo(io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING);
    }

    @Test
    void cancellingDuringPacingStopsTheWorkerWithoutMoreUpdates() throws Exception {
        @SuppressWarnings("unchecked")
        ServerCallStreamObserver<TrackSnapshot> output = mock(ServerCallStreamObserver.class);
        var first = new CountDownLatch(1);
        var handler = new AtomicReference<Runnable>();
        var worker = new AtomicReference<Thread>();
        var cancelled = new AtomicBoolean();
        when(output.isReady()).thenReturn(true);
        when(output.isCancelled()).thenAnswer(i -> cancelled.get());
        doAnswer(
                        i -> {
                            handler.set(i.getArgument(0));
                            return null;
                        })
                .when(output)
                .setOnCancelHandler(any());
        doAnswer(
                        i -> {
                            worker.set(Thread.currentThread());
                            first.countDown();
                            return null;
                        })
                .when(output)
                .onNext(any());
        new TrackerService(catalog, new FlightSimulation(catalog, mapper, 2000))
                .watch(WatchRequest.newBuilder().setTarget("ship:5").build(), output);
        assertThat(first.await(2, TimeUnit.SECONDS)).isTrue();
        cancelled.set(true);
        handler.get().run();
        worker.get().join(1000);
        assertThat(worker.get().isAlive()).isFalse();
        verify(output, times(1)).onNext(any());
        verify(output, never()).onCompleted();
    }

    @Test
    void unsupportedTargetsFinishWithCatalogOnlyAndInvalidTargetsFail() throws Exception {
        @SuppressWarnings("unchecked")
        ServerCallStreamObserver<TrackSnapshot> output = mock(ServerCallStreamObserver.class);
        when(output.isReady()).thenReturn(true);
        var tracker = new TrackerService(catalog, new FlightSimulation(catalog, mapper, 20));
        tracker.watch(WatchRequest.newBuilder().setTarget("none").build(), output);
        verify(output, timeout(2000)).onCompleted();
        verify(output)
                .onNext(
                        argThat(
                                s ->
                                        !s.hasFlight()
                                                && s.getTotalSnapshots() == 1
                                                && s.getStarshipsCount() == 0));
        tracker.watch(WatchRequest.newBuilder().setTarget("missing").build(), output);
        verify(output, timeout(2000)).onError(any());
    }
}
