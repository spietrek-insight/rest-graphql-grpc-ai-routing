package com.insight.router;

import static com.insight.router.ChatModels.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ClmClassifierTest {
    final JsonMapper mapper = JsonMapper.builder().build();
    final JsonHttp http = mock(JsonHttp.class);

    RouterProperties properties(String url, String key) {
        return new RouterProperties(
                "clm",
                "http://ollama",
                "legacy",
                "http://jev",
                "jev-secret",
                "local-model",
                "jev-model",
                url,
                key,
                "clm-test");
    }

    ModelClassifier classifier(String url, String key) {
        return new ModelClassifier(
                properties(url, key), http, mapper, new Catalog(JsonMapper.builder().build()));
    }

    @Test
    void sendsTypedQuestionsWithBoundedHistoryAndPreservesDistributions() throws Exception {
        var response =
                mapper.readTree(
                        """
                {"model":"clm-test","answers":{
                  "operation":{"choice":"SHIP_FILMS","confidence":0.8,"probabilities":{"SHIP_FILMS":0.9,"STARSHIP":0.1}},
                  "target":{"choice":"ship:5","confidence":0.95}}}
                """);
        when(http.send(eq("POST"), eq("http://clm/v1/systemone"), any(), eq(Map.of())))
                .thenReturn(new JsonHttp.Reply(response, 1));
        var history =
                java.util.stream.IntStream.range(0, 20)
                        .mapToObj(i -> new Turn("user", "turn-" + i + ":" + "x".repeat(1000)))
                        .toList();
        var result =
                classifier("http://clm/", "")
                        .classify(new Request("Which films does it appear in?", null, history));
        assertThat(result.protocol()).isEqualTo(Protocol.GRAPHQL);
        assertThat(result.target()).isEqualTo("ship:5");
        assertThat(result.source()).isEqualTo("CLM · clm-test");
        assertThat(result.classification()).isEqualTo(response.path("answers"));
        var capture = ArgumentCaptor.forClass(JsonNode.class);
        verify(http)
                .send(eq("POST"), eq("http://clm/v1/systemone"), capture.capture(), eq(Map.of()));
        var body = capture.getValue();
        assertThat(body.path("model").asString()).isEqualTo("clm-test");
        assertThat(body.path("questions").path("operation").path("type").asString())
                .isEqualTo("choice");
        assertThat(body.path("questions").path("operation").path("criteria"))
                .hasSize(Operation.routableValues().size());
        // Typed providers get the same routing rules as the Ollama tools.
        assertThat(body.path("questions").path("operation").path("instructions").asString())
                .contains("SHIP_CREW", "Cast means fictional characters");
        assertThat(
                        body.path("questions")
                                .path("operation")
                                .path("criteria")
                                .path("SHIP_CREW")
                                .asString())
                .contains("flew", "GRAPHQL");
        assertThat(body.path("questions").path("target").path("criteria").has("none")).isTrue();
        assertThat(body.path("state").asString())
                .contains("turn-16:", "turn-19:")
                .doesNotContain("turn-15:", "x".repeat(257))
                .endsWith("Latest question: Which films does it appear in?");
        verifyNoMoreInteractions(http);
    }

    @Test
    void optionalClmKeyIsSeparateFromJevAndExplicitSelectionWorks() throws Exception {
        when(http.send(anyString(), anyString(), any(), anyMap()))
                .thenReturn(
                        new JsonHttp.Reply(
                                mapper.readTree(
                                        "{\"answers\":{\"operation\":{\"choice\":\"FILM\"},\"target\":{\"choice\":\"film:1\"}}}"),
                                1));
        var props =
                new RouterProperties(
                        "ollama",
                        "http://ollama",
                        "",
                        "http://jev",
                        "jev-secret",
                        "",
                        "",
                        "http://clm",
                        "clm-secret",
                        "");
        var result =
                new ModelClassifier(props, http, mapper, new Catalog(JsonMapper.builder().build()))
                        .classify(new Request("A New Hope", null, List.of(), "clm"));
        assertThat(result.source()).isEqualTo("CLM · clm-latest");
        verify(http)
                .send(
                        eq("POST"),
                        eq("http://clm/v1/systemone"),
                        any(),
                        eq(Map.of("Authorization", "Bearer clm-secret")));
    }

    @Test
    void missingConfigurationNeverFallsBackAndOverridesRemainOffline() {
        var classifier = classifier("", "");
        assertThatThrownBy(() -> classifier.classify(new Request("Falcon", null, List.of())))
                .isInstanceOf(ProviderUnavailableException.class)
                .hasMessageContaining("CLM_URL");
        assertThat(
                        classifier
                                .classify(new Request("Falcon", Protocol.REST, List.of(), "clm"))
                                .source())
                .isEqualTo("Presenter override");
        verifyNoInteractions(http);
    }

    @Test
    void invalidDecisionsAndUnreachableClmNeverFallBack() throws Exception {
        for (String payload :
                List.of(
                        "{}",
                        "{\"answers\":{\"operation\":{\"choice\":\"INVALID\"},\"target\":{\"choice\":\"ship:5\"}}}",
                        "{\"answers\":{\"operation\":{\"choice\":\"STARSHIP\"},\"target\":{\"choice\":\"ship:999\"}}}",
                        "{\"answers\":{\"operation\":{\"choice\":\"FILM\"},\"target\":{\"choice\":\"cast:1\"}}}")) {
            when(http.send(anyString(), anyString(), any(), anyMap()))
                    .thenReturn(new JsonHttp.Reply(mapper.readTree(payload), 1));
            assertThatThrownBy(
                            () ->
                                    classifier("http://clm", "")
                                            .classify(new Request("Falcon", null, List.of())))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        when(http.send(anyString(), anyString(), any(), anyMap()))
                .thenThrow(new IllegalStateException("unreachable"));
        assertThatThrownBy(
                        () ->
                                classifier("http://clm", "")
                                        .classify(new Request("Falcon", null, List.of())))
                .hasMessage("unreachable");
        verify(http, times(5)).send(eq("POST"), eq("http://clm/v1/systemone"), any(), eq(Map.of()));
        verifyNoMoreInteractions(http);
    }
}
