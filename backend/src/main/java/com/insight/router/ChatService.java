package com.insight.router;

import static com.insight.router.ChatModels.*;

import java.util.function.Consumer;
import org.springframework.stereotype.Service;

@Service
public class ChatService {
    private final IntentClassifier classifier;
    private final ProtocolGateway gateway;
    private final AnswerComposer composer;

    public ChatService(
            IntentClassifier classifier, ProtocolGateway gateway, AnswerComposer composer) {
        this.classifier = classifier;
        this.gateway = gateway;
        this.composer = composer;
    }

    public Result chat(Request request, Consumer<Event> events) {
        long start = System.nanoTime();
        var decision = classifier.classify(request);
        long routingMs = elapsed(start);
        events.accept(new Event("decision", decision));
        var exchange = gateway.invoke(decision, events);
        var answer = composer.compose(decision, exchange);
        return new Result(
                answer, new Insight(decision, exchange, new Timings(routingMs, elapsed(start))));
    }

    private static long elapsed(long start) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }
}
