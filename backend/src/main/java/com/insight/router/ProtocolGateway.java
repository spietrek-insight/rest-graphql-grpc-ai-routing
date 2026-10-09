package com.insight.router;

import static com.insight.router.ChatModels.*;

import com.google.protobuf.util.JsonFormat;
import com.insight.router.proto.*;
import io.grpc.ManagedChannel;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

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
        var op = decision.operation();
        if (decision.protocol() == Protocol.REST) {
            String path = op.restPath(decision.target());
            String endpoint = base + path;
            var reply = http.send("GET", endpoint, null, Map.of());
            return new Exchange(
                    "GET " + path,
                    endpoint,
                    "JSON (captured HTTP body)",
                    mapper.valueToTree(Map.of("method", "GET", "url", endpoint)),
                    reply.body(),
                    elapsed(start),
                    reply.bytes());
        }
        String endpoint = base + "/graphql";
        JsonNode request =
                mapper.valueToTree(
                        Map.of(
                                "query",
                                op.query(),
                                "variables",
                                Map.of("target", decision.target())));
        var reply = http.send("POST", endpoint, request, Map.of());
        if (reply.body().has("errors")) throw new IllegalStateException("GraphQL returned errors.");
        long durationMs = elapsed(start);
        return new Exchange(
                op.queryName(),
                endpoint,
                "JSON (captured HTTP body)",
                request,
                reply.body(),
                durationMs,
                reply.bytes(),
                restEquivalent(base, op.restEquivalent(decision.target())),
                null);
    }

    // Real calls, measured separately, so the comparison is evidence rather than a claim.
    private Comparison restEquivalent(String base, List<String> paths) {
        long start = System.nanoTime();
        int bytes = 0;
        for (var path : paths) bytes += http.send("GET", base + path, null, Map.of()).bytes();
        return new Comparison(paths.stream().map(p -> "GET " + p).toList(), bytes, elapsed(start));
    }

    private Exchange track(String target, Consumer<Event> events, long start) {
        ManagedChannel channel = channels.createChannel(TRACKER_CHANNEL);
        var responses = mapper.createArrayNode();
        var request = WatchRequest.newBuilder().setTarget(target).build();
        // Cancel upstream on completion, interruption, or a failed browser write.
        try (var call = io.grpc.Context.current().withCancellation()) {
            return call.call(
                    () -> {
                        var snapshots =
                                StarshipTrackerGrpc.newBlockingStub(channel)
                                        .withDeadlineAfter(115, TimeUnit.SECONDS)
                                        .watch(request);
                        int responseBytes = 0;
                        var stream = new ArrayList<StreamMessage>();
                        long streamStart = System.nanoTime();
                        while (snapshots.hasNext()) {
                            if (Thread.currentThread().isInterrupted())
                                throw new InterruptedException();
                            var value = snapshots.next();
                            var decoded =
                                    mapper.readTree(
                                            JsonFormat.printer()
                                                    .alwaysPrintFieldsWithNoPresence()
                                                    .print(value));
                            responses.add(decoded);
                            var wire =
                                    new StreamMessage(
                                            value.getSequence(),
                                            value.getSerializedSize(),
                                            elapsed(streamStart));
                            stream.add(wire);
                            responseBytes += wire.bytes();
                            events.accept(new Event("snapshot", decoded, wire));
                        }
                        if (responses.isEmpty())
                            throw new IllegalStateException("Stream ended without an update");
                        return new Exchange(
                                "catalog.StarshipTracker/Watch",
                                TRACKER_CHANNEL,
                                "Protobuf over HTTP/2 · decoded JSON",
                                mapper.readTree(JsonFormat.printer().print(request)),
                                responses,
                                elapsed(start),
                                responseBytes,
                                null,
                                List.copyOf(stream));
                    });
        } catch (java.io.UncheckedIOException e) {
            throw e;
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
