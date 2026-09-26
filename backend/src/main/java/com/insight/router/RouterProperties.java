package com.insight.router;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties("router")
public record RouterProperties(
        int grpcPort,
        String provider,
        String ollamaUrl,
        String model,
        String typesafeUrl,
        String typesafeKey,
        String ollamaModel,
        String jevModel) {
    @ConstructorBinding
    public RouterProperties {}

    public RouterProperties(
            int grpcPort,
            String provider,
            String ollamaUrl,
            String model,
            String typesafeUrl,
            String typesafeKey) {
        this(grpcPort, provider, ollamaUrl, model, typesafeUrl, typesafeKey, "", "");
    }

    public String modelFor(String selectedProvider) {
        String configured = selectedProvider.equals("jev") ? jevModel : ollamaModel;
        if (configured != null && !configured.isBlank()) return configured;
        if (selectedProvider.equals(provider) && model != null && !model.isBlank()) return model;
        return selectedProvider.equals("jev") ? "jev-latest" : "qwen3:4b-instruct-2507-q4_K_M";
    }
}
