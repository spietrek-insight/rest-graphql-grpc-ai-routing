package com.insight.router;

import static com.insight.router.ChatModels.*;

public interface IntentClassifier {
    Decision classify(Request request);
}
