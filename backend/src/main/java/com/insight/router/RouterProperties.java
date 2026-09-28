package com.insight.router;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties("router")
public record RouterProperties(
        String provider,
        String ollamaUrl,
        String model,
        String typesafeUrl,
        String typesafeKey,
        String ollamaModel,
        String jevModel,
        String clmUrl,
        String clmKey,
        String clmModel) {
    @ConstructorBinding
    public RouterProperties {
        clmUrl = clmUrl == null ? "" : clmUrl.strip().replaceAll("/+$", "");
        clmKey = clmKey == null ? "" : clmKey;
    }

    public RouterProperties(
            String provider,
            String ollamaUrl,
            String model,
            String typesafeUrl,
            String typesafeKey,
            String ollamaModel,
            String jevModel) {
        this(
                provider,
                ollamaUrl,
                model,
                typesafeUrl,
                typesafeKey,
                ollamaModel,
                jevModel,
                "",
                "",
                "");
    }

    public RouterProperties(
            String provider,
            String ollamaUrl,
            String model,
            String typesafeUrl,
            String typesafeKey) {
        this( provider, ollamaUrl, model, typesafeUrl, typesafeKey, "", "");
    }

    public String modelFor(String selectedProvider) {
        String configured =
                switch (selectedProvider) {
                    case "ollama" -> ollamaModel;
                    case "jev" -> jevModel;
                    case "clm" -> clmModel;
                    default ->
                            throw new IllegalArgumentException(
                                    "Unknown AI provider: " + selectedProvider);
                };
        if (configured != null && !configured.isBlank()) return configured;
        if (selectedProvider.equals(provider) && model != null && !model.isBlank()) return model;
        return switch (selectedProvider) {
            case "jev" -> "jev-latest";
            case "clm" -> "clm-latest";
            default -> "qwen3:4b-instruct-2507-q4_K_M";
        };
    }
}
