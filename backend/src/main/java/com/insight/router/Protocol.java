package com.insight.router;

public enum Protocol {
    REST("Single-resource lookup with a fixed, cacheable response."),
    GRAPHQL("A nested query brings starships, films and producers together in one request."),
    GRPC("A bidirectional Protobuf stream exchanges repeated starship snapshots over HTTP/2.");
    private final String rationale;

    Protocol(String rationale) {
        this.rationale = rationale;
    }

    public String rationale() {
        return rationale;
    }
}
