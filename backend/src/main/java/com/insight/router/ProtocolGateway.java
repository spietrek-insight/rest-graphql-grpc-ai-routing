package com.insight.router;

import static com.insight.router.ChatModels.*;

import com.google.protobuf.util.JsonFormat;
import com.insight.router.proto.*;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Service;

@Service
public class ProtocolGateway {
    private final JsonHttp http;
    private final JsonMapper mapper;
    private final WebServerApplicationContext context;
	private static final String TRACKER_CHANNEL = "tracker";
	private final GrpcChannelFactory channels;

    public ProtocolGateway(
            JsonHttp http,
            JsonMapper mapper,
            WebServerApplicationContext context,
			GrpcChannelFactory channels) {
        this.http = http;
        this.mapper = mapper;
        this.context = context;
		this.channels = channels;
    }

    public Exchange invoke(Decision decision, Consumer<Event> events) {
        long start = System.nanoTime();
        String base = "http://127.0.0.1:" + context.getWebServer().getPort();
        if (decision.protocol() == Protocol.GRPC) return track(decision.target(), events, start);
        String endpoint;
        String operation;
        JsonNode request;
        JsonHttp.Reply reply;
        if (decision.protocol() == Protocol.REST) {
            String path =
                    decision.target().startsWith("producer:")
                            ? "/api/catalog/producers/" + decision.target().substring(9)
                            : decision.target().startsWith("film:")
                                    ? "/api/catalog/films/" + decision.target().substring(5)
                                    : "/api/catalog/starships/" + decision.target();
            endpoint = base + path;
            operation = "GET " + path;
            request = mapper.valueToTree(Map.of("method", "GET", "url", endpoint));
            reply = http.send("GET", endpoint, null, Map.of());
        } else {
            endpoint = base + "/graphql";
            operation = "query ExploreStarships";
            request =
                    mapper.valueToTree(
                            Map.of(
                                    "query",
                                    "query ExploreStarships($target: String!) { starships(target: $target) { id name maxSpeed length films { title producers { name } } } }",
                                    "variables",
                                    Map.of("target", decision.target())));
            reply = http.send("POST", endpoint, request, Map.of());
            if (reply.body().has("errors"))
                throw new IllegalStateException("GraphQL returned errors.");
        }
        return new Exchange(
                operation,
                endpoint,
                "JSON (captured HTTP body)",
                request,
                reply.body(),
                elapsed(start),
                reply.bytes());
    }

    private Exchange track(String target, Consumer<Event> events, long start) {
        ManagedChannel channel =
				channels.createChannel(TRACKER_CHANNEL);
        var responses = mapper.createArrayNode();
        var requests = mapper.createArrayNode();
        var completed = new CompletableFuture<Void>();
        var bytes = new AtomicInteger();
        try {
            var sender =
                    StarshipTrackerGrpc.newStub(channel)
                            .withDeadlineAfter(10, TimeUnit.SECONDS)
                            .track(
                                    new StreamObserver<>() {
                                        @Override
                                        public void onNext(TrackSnapshot value) {
                                            try {
                                                var decoded =
                                                        mapper.readTree(
                                                                JsonFormat.printer()
                                                                        .alwaysPrintFieldsWithNoPresence()
                                                                        .print(value));
                                                responses.add(decoded);
                                                bytes.addAndGet(value.getSerializedSize());
                                                events.accept(new Event("snapshot", decoded));
                                            } catch (Exception e) {
                                                completed.completeExceptionally(e);
                                            }
                                        }

                                        @Override
                                        public void onError(Throwable error) {
                                            completed.completeExceptionally(error);
                                        }

                                        @Override
                                        public void onCompleted() {
                                            completed.complete(null);
                                        }
                                    });
            for (int i = 1; i <= 3; i++) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                var request = TrackRequest.newBuilder().setTarget(target).setSequence(i).build();
                requests.add(mapper.readTree(JsonFormat.printer().print(request)));
                sender.onNext(request);
                if (i < 3)
                    Thread.sleep(
                            350); // Deliberate demo pacing; the transport itself is a real duplex
                // stream.
            }
            sender.onCompleted();
            completed.get(11, TimeUnit.SECONDS);
            return new Exchange(
                    "catalog.StarshipTracker/Track",
					TRACKER_CHANNEL,
                    "Protobuf over HTTP/2 · decoded JSON",
                    requests,
                    responses,
                    elapsed(start),
                    bytes.get());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Stream cancelled", e);
        } catch (Exception e) {
            throw new IllegalStateException("gRPC stream failed", e);
        } finally {
            channel.shutdownNow();
        }
    }

    private static long elapsed(long start) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }
}
