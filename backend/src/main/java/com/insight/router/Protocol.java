package com.insight.router;

public enum Protocol {
    REST("Single-resource lookup with a fixed, cacheable response."),
    GRAPHQL(
            "A nested query brings starships, films, cast and flight crew together in one request."),
    GRPC(
            "One subscription delivers changing position and pilot updates over a server-streaming Protobuf connection. Current location, current pilot and tracking requests use the clearly labeled flight simulation.");
    private final String rationale;

    Protocol(String rationale) {
        this.rationale = rationale;
    }

    public String rationale() {
        return rationale;
    }
}
