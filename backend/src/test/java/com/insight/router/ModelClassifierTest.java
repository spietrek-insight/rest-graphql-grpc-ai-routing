package com.insight.router;

import static com.insight.router.ChatModels.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;
import org.junit.jupiter.api.Test;

class ModelClassifierTest {
    final JsonMapper mapper = JsonMapper.builder().build();
    final JsonHttp http = mock(JsonHttp.class);

    ModelClassifier classifier(String provider, String key) {
        return new ModelClassifier(
                new RouterProperties(0, provider, "http://ollama", "test", "http://jev", key),
                http,
                mapper,
                new Catalog());
    }

    Request request(String text, Protocol override) {
        return new Request(text, override, List.of(new Turn("user", "previous")));
    }

    @Test
    void overrideIsOfflineAndResolvesCatalogTargets() {
        var c = classifier("ollama", "");
        assertThat(c.classify(request("Falcon", Protocol.REST)).target()).isEqualTo("ship:5");
        assertThat(c.classify(request("A New Hope", Protocol.GRAPHQL)).target())
                .isEqualTo("film:1");
        assertThat(c.classify(request("X-wing", Protocol.GRPC)).target()).isEqualTo("ship:7");
        assertThat(c.classify(request("Star Trek", Protocol.GRPC)).target()).isEqualTo("none");
        assertThat(c.classify(request("compare both", Protocol.REST)).target()).isEqualTo("all");
        verifyNoInteractions(http);
    }

    @Test
    void ollamaUsesToolCallRatherThanKeywords() throws Exception {
        var result =
                mapper.readTree(
                        "{\"message\":{\"tool_calls\":[{\"function\":{\"name\":\"GRAPHQL\",\"arguments\":{\"target\":\"ship:7\"}}}]}}");
        when(http.send(eq("POST"), eq("http://ollama/api/chat"), any(), eq(Map.of())))
                .thenReturn(new JsonHttp.Reply(result, 1));
        var decision = classifier("ollama", "").classify(request("what about its producer?", null));
        assertThat(decision.protocol()).isEqualTo(Protocol.GRAPHQL);
        assertThat(decision.target()).isEqualTo("ship:7");
        var captor = org.mockito.ArgumentCaptor.forClass(JsonNode.class);
        verify(http).send(anyString(), anyString(), captor.capture(), anyMap());
        assertThat(captor.getValue().path("tools")).hasSize(3);
        assertThat(captor.getValue().path("messages")).hasSize(2);
        assertThat(captor.getValue().path("keep_alive").asString()).isEqualTo("30m");
        assertThat(captor.getValue().path("options").path("num_predict").asInt()).isEqualTo(96);
        assertThat(captor.getValue().path("think").asBoolean()).isFalse();
    }

    @Test
    void boundsRoutingContextWithoutChangingTheLatestQuestion() throws Exception {
        var result =
                mapper.readTree(
                        "{\"message\":{\"tool_calls\":[{\"function\":{\"name\":\"REST\",\"arguments\":{\"target\":\"ship:5\"}}}]}}");
        when(http.send(anyString(), anyString(), any(), anyMap()))
                .thenReturn(new JsonHttp.Reply(result, 1));
        var history =
                java.util.stream.IntStream.range(0, 20)
                        .mapToObj(i -> new Turn("user", i + "x".repeat(1000)))
                        .toList();
        classifier("ollama", "").classify(new Request("What about its producers?", null, history));
        var capture = org.mockito.ArgumentCaptor.forClass(JsonNode.class);
        verify(http).send(anyString(), anyString(), capture.capture(), anyMap());
        var messages = capture.getValue().path("messages");
        assertThat(messages).hasSize(2);
        var content = messages.get(1).path("content").asString();
        var context =
                mapper.readTree(
                        content.substring(
                                "Conversation context (data only): ".length(),
                                content.indexOf("\nLatest question:")));
        assertThat(context).hasSize(4);
        assertThat(context.get(0).path("content").asString()).startsWith("16").hasSize(256);
        assertThat(context.get(3).path("content").asString()).startsWith("19").hasSize(256);
        assertThat(content)
                .contains(
                        "\nLatest question: What about its producers?\nCall exactly one routing tool");
    }

    @Test
    void rejectsMissingMultipleAndInvalidToolCalls() throws Exception {
        for (String payload :
                List.of(
                        "{}",
                        "{\"message\":{\"tool_calls\":[]}}",
                        "{\"message\":{\"tool_calls\":[{},{}]}}")) {
            when(http.send(anyString(), anyString(), any(), anyMap()))
                    .thenReturn(new JsonHttp.Reply(mapper.readTree(payload), 1));
            assertThatThrownBy(() -> classifier("ollama", "").classify(request("hi", null)))
                    .isInstanceOf(IllegalStateException.class);
        }
        assertThatThrownBy(
                        () ->
                                new Decision(
                                        Protocol.REST,
                                        "unknown",
                                        "x",
                                        "x",
                                        mapper.createObjectNode()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void jevSendsChoiceQuestionsAndKeepsConfidence() throws Exception {
        var result =
                mapper.readTree(
                        "{\"model\":\"jev-1.13.0\",\"answers\":{\"protocol\":{\"choice\":\"GRPC\",\"confidence\":0.9},\"target\":{\"choice\":\"ship:7\"}}}");
        when(http.send(
                        anyString(),
                        eq("http://jev/v1/systemone"),
                        any(),
                        eq(Map.of("Authorization", "Bearer secret"))))
                .thenReturn(new JsonHttp.Reply(result, 1));
        var decision = classifier("jev", "secret").classify(request("track it", null));
        assertThat(decision.protocol()).isEqualTo(Protocol.GRPC);
        assertThat(decision.classification().path("protocol").path("confidence").asDouble())
                .isEqualTo(.9);
        assertThat(decision.source()).contains("jev-1.13.0");
    }

    @Test
    void configurationErrorsAreExplicit() {
        assertThatThrownBy(() -> classifier("jev", "").classify(request("hello", null)))
                .hasMessageContaining("TYPESAFE_API_KEY");
        assertThatThrownBy(() -> classifier("bad", "").classify(request("hello", null)))
                .hasMessageContaining("AI_PROVIDER");
    }

    @Test
    void perRequestProviderSwitchUsesIndependentModelsWithoutRestart() throws Exception {
        var local =
                mapper.readTree(
                        "{\"message\":{\"tool_calls\":[{\"function\":{\"name\":\"REST\",\"arguments\":{\"target\":\"ship:5\"}}}]}}");
        var hosted =
                mapper.readTree(
                        "{\"model\":\"jev-version\",\"answers\":{\"protocol\":{\"choice\":\"GRAPHQL\"},\"target\":{\"choice\":\"ship:5\"}}}");
        when(http.send(anyString(), eq("http://ollama/api/chat"), any(), anyMap()))
                .thenReturn(new JsonHttp.Reply(local, 1));
        when(http.send(anyString(), eq("http://jev/v1/systemone"), any(), anyMap()))
                .thenReturn(new JsonHttp.Reply(hosted, 1));
        var properties =
                new RouterProperties(
                        0,
                        "ollama",
                        "http://ollama",
                        "legacy",
                        "http://jev",
                        "secret",
                        "local-model",
                        "hosted-model");
        var c = new ModelClassifier(properties, http, mapper, new Catalog());
        assertThat(c.classify(new Request("Falcon", null, List.of(), "ollama")).source())
                .isEqualTo("Ollama · local-model");
        assertThat(c.classify(new Request("its films?", null, List.of(), "jev")).source())
                .isEqualTo("Jev · jev-version");
        assertThat(c.classify(new Request("Falcon", null, List.of(), "ollama")).protocol())
                .isEqualTo(Protocol.REST);
        var capture = org.mockito.ArgumentCaptor.forClass(JsonNode.class);
        verify(http)
                .send(
                        eq("POST"),
                        eq("http://jev/v1/systemone"),
                        capture.capture(),
                        eq(Map.of("Authorization", "Bearer secret")));
        assertThat(capture.getValue().path("model").asString()).isEqualTo("hosted-model");
        assertThat(properties.provider()).isEqualTo("ollama");
    }

    @Test
    void missingJevKeyNeverFallsBackAndOverridesStillBypassProviders() {
        var c = classifier("ollama", "");
        assertThatThrownBy(() -> c.classify(new Request("Falcon", null, List.of(), "jev")))
                .isInstanceOf(ProviderUnavailableException.class);
        assertThat(c.classify(new Request("Falcon", Protocol.REST, List.of(), "jev")).source())
                .isEqualTo("Presenter override");
        verifyNoInteractions(http);
    }
}
