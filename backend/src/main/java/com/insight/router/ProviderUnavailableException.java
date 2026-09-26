package com.insight.router;

/** A safe, actionable configuration error that may be shown to the user. */
public class ProviderUnavailableException extends IllegalStateException {
    public ProviderUnavailableException() {
        super(
                "Jev is not configured. Add TYPESAFE_API_KEY to the backend configuration, or select Ollama in AI preferences.");
    }
}
