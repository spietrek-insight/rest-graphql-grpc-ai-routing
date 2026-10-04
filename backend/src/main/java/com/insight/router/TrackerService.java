package com.insight.router;

import com.insight.router.proto.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.time.Instant;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
public class TrackerService extends StarshipTrackerGrpc.StarshipTrackerImplBase {
    private final Catalog catalog;

    public TrackerService(Catalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public StreamObserver<TrackRequest> track(StreamObserver<TrackSnapshot> response) {
        return new StreamObserver<>() {
            private boolean closed;

            @Override
            public void onNext(TrackRequest request) {
                if (closed) return;
                try {
                    var snapshot =
                            TrackSnapshot.newBuilder()
                                    .setSequence(request.getSequence())
                                    .setObservedAt(Instant.now().toString())
                                    .setStatus("Catalog snapshot · demonstration data");
                    for (var ship : catalog.starships(request.getTarget())) {
                        snapshot.addStarships(
                                TrackedShip.newBuilder()
                                        .setId(ship.id())
                                        .setName(ship.name())
                                        .setMaxSpeed(ship.maxSpeed())
                                        .setLength(ship.length())
                                        .addAllFilms(
                                                ship.films().stream()
                                                        .map(Catalog.Film::title)
                                                        .toList())
                                        .addAllCast(
                                                ship.films().stream()
                                                        .flatMap(f -> f.cast().stream())
                                                        .map(Catalog.CastMember::name)
                                                        .distinct()
                                                        .toList())
                                        .addAllCrew(
                                                ship.crew().stream()
                                                        .map(
                                                                c ->
                                                                        FlightCrew.newBuilder()
                                                                                .setFilmId(
                                                                                        c.filmId())
                                                                                .setFilmTitle(
                                                                                        c
                                                                                                .filmTitle())
                                                                                .setRole(c.role())
                                                                                .setSource(
                                                                                        c.source())
                                                                                .setMember(
                                                                                        CastPerson
                                                                                                .newBuilder()
                                                                                                .setId(
                                                                                                        c.member()
                                                                                                                .id())
                                                                                                .setName(
                                                                                                        c.member()
                                                                                                                .name()))
                                                                                .build())
                                                        .toList()));
                    }
                    response.onNext(snapshot.build());
                } catch (IllegalArgumentException e) {
                    closed = true;
                    response.onError(
                            Status.INVALID_ARGUMENT
                                    .withDescription(e.getMessage())
                                    .asRuntimeException());
                }
            }

            @Override
            public void onError(Throwable error) {
                closed = true;
            }

            @Override
            public void onCompleted() {
                if (!closed) {
                    closed = true;
                    response.onCompleted();
                }
            }
        };
    }
}
