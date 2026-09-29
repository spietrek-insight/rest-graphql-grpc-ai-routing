package com.insight.router;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ModelWarmupTest {
    final IntentClassifier classifier = mock(IntentClassifier.class);

    RouterProperties properties(String provider) {
        return new RouterProperties(provider, "", "model", "", "");
    }

    @Test
    void startupWarmsLocalInferenceAsynchronously() throws Exception {
        var invoked = new CountDownLatch(1);
        when(classifier.classify(any()))
                .thenAnswer(
                        call -> {
                            invoked.countDown();
                            return null;
                        });
        new ModelWarmup(classifier, properties("ollama"), true).onReady();
        assertThat(invoked.await(2, TimeUnit.SECONDS)).isTrue();
        verify(classifier).classify(argThat(r -> r.override() == null && r.history().isEmpty()));
    }

    @Test
    void skipsHostedProvidersAndExplicitlyDisabledWarmup() {
        new ModelWarmup(classifier, properties("jev"), true).onReady();
        new ModelWarmup(classifier, properties("ollama"), false).onReady();
        verifyNoInteractions(classifier);
    }

    @Test
    void missingModelDoesNotBreakApplicationStartup() {
        when(classifier.classify(any()))
                .thenThrow(new IllegalStateException("model not yet installed"));
        assertThatCode(() -> new ModelWarmup(classifier, properties("ollama"), true).warm())
                .doesNotThrowAnyException();
    }
}
