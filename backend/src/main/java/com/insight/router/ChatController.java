package com.insight.router;

import static com.insight.router.ChatModels.*;

import jakarta.validation.Valid;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api")
public class ChatController {
    private final ChatService service;
    private final RouterProperties properties;
    private final JsonMapper mapper;

    public ChatController(ChatService service, RouterProperties properties, JsonMapper mapper) {
        this.service = service;
        this.properties = properties;
        this.mapper = mapper;
    }

    @GetMapping("/status")
    Map<String, Object> status() {
        return Map.of(
                "service",
                "ready",
                "provider",
                properties.provider(),
                "model",
                properties.modelFor(properties.provider()),
                "local",
                properties.provider().equals("ollama"),
                "providers",
                List.of(
                        Map.of(
                                "id",
                                "ollama",
                                "model",
                                properties.modelFor("ollama"),
                                "local",
                                true,
                                "available",
                                true),
                        Map.of(
                                "id",
                                "jev",
                                "model",
                                properties.modelFor("jev"),
                                "local",
                                false,
                                "available",
                                !properties.typesafeKey().isBlank()),
                        Map.of(
                                "id",
                                "clm",
                                "model",
                                properties.modelFor("clm"),
                                "local",
                                false,
                                "available",
                                !properties.clmUrl().isBlank())));
    }

    @PostMapping(value = "/chat", produces = "application/x-ndjson")
    StreamingResponseBody chat(@Valid @RequestBody Request request) {
        return output -> {
            java.util.function.Consumer<Event> send =
                    event -> {
                        try {
                            output.write(mapper.writeValueAsBytes(event));
                            output.write('\n');
                            output.flush();
                        } catch (java.io.IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    };
            try {
                send.accept(new Event("result", service.chat(request, send)));
            } catch (java.io.UncheckedIOException e) {
                throw e;
            } catch (Exception e) {
                send.accept(
                        new Event(
                                "error",
                                Map.of(
                                        "message",
                                        e instanceof ProviderUnavailableException
                                                ? e.getMessage()
                                                : "Unable to complete this request. Check your model is running and downloaded, or select a presenter override.")));
            }
        };
    }
}
