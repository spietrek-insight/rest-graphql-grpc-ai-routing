package com.insight.router;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChatControllerTest {
    @Test
    void statusExposesOnlySafeProviderMetadataAndMissingKeyErrorIsActionable() throws Exception {
        var service = mock(ChatService.class);
        when(service.chat(any(), any())).thenThrow(new ProviderUnavailableException());
        var mapper = new ObjectMapper();
        var controller =
                new ChatController(
                        service, new RouterProperties(0, "ollama", "", "", "", ""), mapper);
        var status = mapper.valueToTree(controller.status());
        assertThat(status.path("providers").get(1).path("available").asBoolean()).isFalse();
        assertThat(status.path("providers").get(2).path("available").asBoolean()).isFalse();
        var out = new ByteArrayOutputStream();
        controller.chat(new ChatModels.Request("hi", null, List.of(), "jev")).writeTo(out);
        assertThat(out.toString()).contains("Jev is not configured", "AI preferences");
        var configured =
                new ChatController(
                        service,
                        new RouterProperties(0, "ollama", "", "", "", "secret-key"),
                        mapper);
        var configuredStatus = mapper.valueToTree(configured.status());
        assertThat(configuredStatus.path("providers").get(1).path("available").asBoolean())
                .isTrue();
        assertThat(configuredStatus.toString()).doesNotContain("secret-key");
    }

    @Test
    void clmStatusAndConfigurationErrorsNeverExposeServerSecrets() throws Exception {
        var service = mock(ChatService.class);
        var mapper = new ObjectMapper();
        var props =
                new RouterProperties(
                        0,
                        "clm",
                        "",
                        "",
                        "",
                        "jev-secret",
                        "",
                        "",
                        "http://private-clm:8700",
                        "clm-secret",
                        "custom-clm");
        var controller = new ChatController(service, props, mapper);
        var status = mapper.valueToTree(controller.status());
        assertThat(status.path("provider").asText()).isEqualTo("clm");
        assertThat(status.path("model").asText()).isEqualTo("custom-clm");
        assertThat(status.path("providers").get(2).path("available").asBoolean()).isTrue();
        assertThat(status.toString()).doesNotContain("clm-secret", "jev-secret", "private-clm");
        when(service.chat(any(), any()))
                .thenThrow(new ProviderUnavailableException("CLM is not configured. Add CLM_URL."));
        var out = new ByteArrayOutputStream();
        controller.chat(new ChatModels.Request("hi", null, List.of(), "clm")).writeTo(out);
        assertThat(out.toString()).contains("CLM_URL");
    }

    @Test
    void modelFailuresProduceActionableErrorEvent() throws Exception {
        var service = mock(ChatService.class);
        when(service.chat(any(), any()))
                .thenThrow(new IllegalStateException("secret upstream detail"));
        var controller =
                new ChatController(
                        service,
                        new RouterProperties(0, "jev", "", "jev-latest", "", ""),
                        new ObjectMapper());
        assertThat(controller.status().get("local")).isEqualTo(false);
        var out = new ByteArrayOutputStream();
        controller.chat(new ChatModels.Request("hello", null, List.of())).writeTo(out);
        assertThat(out.toString()).contains("error", "presenter override").doesNotContain("secret");
    }

    @Test
    void disconnectedClientStopsWriting() {
        var service = mock(ChatService.class);
        var controller =
                new ChatController(
                        service,
                        new RouterProperties(0, "ollama", "", "x", "", ""),
                        new ObjectMapper());
        var stream = controller.chat(new ChatModels.Request("hello", null, List.of()));
        assertThatThrownBy(
                        () ->
                                stream.writeTo(
                                        new OutputStream() {
                                            public void write(int b) throws IOException {
                                                throw new IOException("closed");
                                            }
                                        }))
                .isInstanceOf(UncheckedIOException.class);
    }
}
