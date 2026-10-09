package com.insight.router;

import com.insight.router.proto.*;
import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
public class TrackerService extends StarshipTrackerGrpc.StarshipTrackerImplBase {
    private final Catalog catalog;

    private final FlightSimulation simulation;

    @Autowired
    public TrackerService(Catalog catalog, FlightSimulation simulation) {
        this.catalog = catalog;
        this.simulation = simulation;
    }

    @Override
    public void watch(WatchRequest request, StreamObserver<TrackSnapshot> output) {
        var response = (ServerCallStreamObserver<TrackSnapshot>) output;
        // One writer per call. A virtual thread keeps paced work off gRPC callback threads.
        var worker =
                Thread.ofVirtual()
                        .unstarted(
                                () -> {
                                    try {
                                        boolean simulated =
                                                simulation.supports(request.getTarget());
                                        int count = simulated ? simulation.count() : 1;
                                        for (int i = 0; i < count; i++) {
                                            if (i > 0) Thread.sleep(simulation.intervalMs());
                                            if (response.isCancelled()) return;
                                            // Honor flow control, with the caller's deadline
                                            // bounding a stalled reader.
                                            while (!response.isReady()) {
                                                if (response.isCancelled()) return;
                                                Thread.sleep(20);
                                            }
                                            // The first message identifies the ship;
                                            // later flight messages carry only what
                                            // changes, keeping each message small.
                                            var snapshot =
                                                    (i == 0 || !simulated
                                                                    ? snapshot(
                                                                            request.getTarget(),
                                                                            i + 1)
                                                                    : TrackSnapshot.newBuilder()
                                                                            .setSequence(i + 1)
                                                                            .setObservedAt(
                                                                                    Instant.now()
                                                                                            .toString()))
                                                            .setTotalSnapshots(count);
                                            if (simulated)
                                                snapshot.setFlight(simulation.frame(i))
                                                        .setStatus(
                                                                "Simulated flight · fictional presentation scenario");
                                            else
                                                snapshot.setStatus(
                                                        "Catalog only · no simulated flight for this target. Try tracking the Millennium Falcon.");
                                            response.onNext(snapshot.build());
                                        }
                                        if (!response.isCancelled()) response.onCompleted();
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                    } catch (IllegalArgumentException e) {
                                        if (!response.isCancelled())
                                            response.onError(
                                                    Status.INVALID_ARGUMENT
                                                            .withDescription(e.getMessage())
                                                            .asRuntimeException());
                                    } catch (Exception e) {
                                        if (!response.isCancelled())
                                            response.onError(
                                                    Status.INTERNAL
                                                            .withDescription("Flight stream failed")
                                                            .asRuntimeException());
                                    }
                                });
        response.setOnCancelHandler(worker::interrupt);
        worker.start();
    }

    @Override
    public StreamObserver<TrackRequest> track(StreamObserver<TrackSnapshot> response) {
        return new StreamObserver<>() {
            private boolean closed;

            @Override
            public void onNext(TrackRequest request) {
                if (closed) return;
                try {
                    var snapshot = snapshot(request.getTarget(), request.getSequence());
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

    private TrackSnapshot.Builder snapshot(String target, int sequence) {
        var snapshot =
                TrackSnapshot.newBuilder()
                        .setSequence(sequence)
                        .setObservedAt(Instant.now().toString())
                        .setStatus("Catalog snapshot · demonstration data");
        for (var ship : catalog.starships(target)) {
            snapshot.addStarships(
                    TrackedShip.newBuilder()
                            .setId(ship.id())
                            .setName(ship.name())
                            .setMaxSpeed(ship.maxSpeed())
                            .setLength(ship.length())
                            .addAllFilms(ship.films().stream().map(Catalog.Film::title).toList())
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
                                                                    .setFilmId(c.filmId())
                                                                    .setFilmTitle(c.filmTitle())
                                                                    .setRole(c.role())
                                                                    .setSource(c.source())
                                                                    .setMember(
                                                                            CastPerson.newBuilder()
                                                                                    .setId(
                                                                                            c.member()
                                                                                                    .id())
                                                                                    .setName(
                                                                                            c.member()
                                                                                                    .name()))
                                                                    .build())
                                            .toList()));
        }
        return snapshot;
    }
}
