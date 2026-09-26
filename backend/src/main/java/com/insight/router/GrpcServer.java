package com.insight.router;

import io.grpc.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class GrpcServer {
    private final Server server;

    public GrpcServer(RouterProperties properties, TrackerService service) {
        server = ServerBuilder.forPort(properties.grpcPort()).addService(service).build();
    }

    @PostConstruct
    void start() throws IOException {
        server.start();
    }

    public int port() {
        return server.getPort();
    }

    @PreDestroy
    void stop() throws InterruptedException {
        server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }
}
