package com.insight.router;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class JsonHttp {
    public record Reply(JsonNode body, int bytes) {}

    private final JsonMapper mapper;
    private final HttpClient client =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public JsonHttp(JsonMapper mapper) {
        this.mapper = mapper;
    }

    public Reply send(String method, String url, JsonNode body, Map<String, String> headers) {
        try {
            var builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(120));
            headers.forEach(builder::header);
            if (body == null) builder.GET();
            else
                builder.header("Content-Type", "application/json")
                        .method(
                                method,
                                HttpRequest.BodyPublishers.ofString(
                                        mapper.writeValueAsString(body)));
            var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 400)
                throw new IllegalStateException("Upstream returned HTTP " + response.statusCode());
            return new Reply(mapper.readTree(response.body()), response.body().length);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Request interrupted", e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(
                    "Cannot reach service. Check the local model and backend configuration.", e);
        }
    }
}
