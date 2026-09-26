package com.insight.router;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public final class ChatModels {
    private ChatModels() {}

    public record Turn(
            @Pattern(regexp = "user|assistant") @NotNull String role,
            @NotBlank @Size(max = 4000) String content) {}

    public record Request(
            @NotBlank @Size(max = 2000) String message,
            Protocol override,
            @NotNull @Size(max = 20) List<@NotNull @Valid Turn> history,
            @Pattern(regexp = "ollama|jev") String provider) {
        public Request(String message, Protocol override, List<Turn> history) {
            this(message, override, history, null);
        }
    }

    public record Decision(
            Protocol protocol,
            String target,
            String source,
            String reason,
            JsonNode classification) {
        public Decision {
            if (target == null
                    || !target.matches("all|none|ship:(?:[1-9]|1[0-9])|film:[1-6]|producer:[1-4]"))
                throw new IllegalArgumentException("Model returned an unsupported target.");
        }
    }

    public record Exchange(
            String operation,
            String endpoint,
            String encoding,
            JsonNode request,
            JsonNode response,
            long durationMs,
            int responseBytes) {}

    public record Timings(long routingMs, long totalMs) {}

    public record Insight(Decision decision, Exchange exchange, Timings timings) {}

    public record Result(String answer, Insight insight) {}

    public record Event(String type, Object data) {}
}
