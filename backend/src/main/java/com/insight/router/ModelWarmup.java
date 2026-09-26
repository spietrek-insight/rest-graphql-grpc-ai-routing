package com.insight.router;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Preloads weights and the actual tool prompt without delaying HTTP startup or calling a protocol.
 */
@Component
public class ModelWarmup {
    private static final Logger LOG = LoggerFactory.getLogger(ModelWarmup.class);
    private final IntentClassifier classifier;
    private final RouterProperties properties;
    private final boolean enabled;

    public ModelWarmup(
            IntentClassifier classifier,
            RouterProperties properties,
            @Value("${router.warmup:true}") boolean enabled) {
        this.classifier = classifier;
        this.properties = properties;
        this.enabled = enabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (enabled && properties.provider().equals("ollama")) {
            Thread.startVirtualThread(this::warm);
        }
    }

    void warm() {
        try {
            classifier.classify(
                    new ChatModels.Request("Tell me about the Millennium Falcon", null, List.of()));
            LOG.info("Local routing model warmed and retained for 30 minutes");
        } catch (RuntimeException error) {
            // Model download/startup may still be in progress. Normal chat can retry later.
            LOG.info("Local model warmup deferred; the next automatic request will load it");
        }
    }
}
