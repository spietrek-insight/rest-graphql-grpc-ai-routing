package com.insight.router;

import static com.insight.router.ChatModels.*;
import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"router.grpc-port=0", "router.warmup=false"})
class ProtocolIntegrationTest {
    @Autowired ChatService service;
    @Autowired ObjectMapper mapper;
    @Autowired JsonHttp jsonHttp;
    @LocalServerPort int port;

    @Test
    void standaloneFilmAndUnlinkedProducerLookupsUseRealRestResources() {
        var producer =
                service.chat(
                        new Request("Tell me about George Lucas", Protocol.REST, List.of()),
                        e -> {});
        assertThat(producer.answer()).contains("George Lucas", "producer #4");
        assertThat(producer.insight().exchange().operation())
                .isEqualTo("GET /api/catalog/producers/4");
        var film =
                service.chat(
                        new Request("Tell me about A New Hope", Protocol.REST, List.of()), e -> {});
        assertThat(film.answer()).contains("A New Hope", "Gary Kurtz", "Rick McCallum");
    }

    @Test
    void filmQueriesAndUnknownEntitiesStayGrounded() {
        var films =
                service.chat(
                        new Request(
                                "Which starships appear in A New Hope?",
                                Protocol.GRAPHQL,
                                List.of()),
                        e -> {});
        assertThat(films.insight().exchange().response().path("data").path("starships")).hasSize(5);
        var unknown = service.chat(new Request("Enterprise", Protocol.REST, List.of()), e -> {});
        assertThat(unknown.answer()).contains("No matching starships");
    }

    @Test
    void allThreeRealTransportsProduceGroundedAnswers() {
        for (Protocol protocol : Protocol.values()) {
            var events = new ArrayList<Event>();
            var result =
                    service.chat(
                            new Request("Tell me about the X-wing", protocol, List.of()),
                            events::add);
            assertThat(result.answer()).contains("X-wing", "The Empire Strikes Back", "Gary Kurtz");
            assertThat(result.insight().exchange().responseBytes()).isPositive();
            assertThat(result.insight().timings().routingMs()).isNotNegative();
            assertThat(result.insight().timings().totalMs())
                    .isGreaterThanOrEqualTo(result.insight().exchange().durationMs());
            assertThat(events.getFirst().type()).isEqualTo("decision");
            if (protocol == Protocol.GRPC) {
                assertThat(events.stream().filter(e -> e.type().equals("snapshot"))).hasSize(3);
                assertThat(result.insight().exchange().response()).hasSize(3);
            }
        }
        assertThat(
                        service.chat(new Request("compare both", Protocol.REST, List.of()), e -> {})
                                .answer())
                .contains("19 starships", "Millennium Falcon");
    }

    @Test
    void restGraphqlAndValidationSurfacesWork() throws Exception {
        for (String path :
                List.of(
                        "/api/status",
                        "/api/catalog/films",
                        "/api/catalog/producers",
                        "/api/catalog/starships/all",
                        "/actuator/health")) {
            assertThat(get(path).statusCode()).isEqualTo(200);
        }
        assertThat(get("/api/catalog/starships/missing").statusCode()).isEqualTo(400);
        var graphql =
                jsonHttp.send(
                        "POST",
                        url("/graphql"),
                        mapper.readTree("{\"query\":\"{ films { title } producers { name } }\"}"),
                        Map.of());
        assertThat(graphql.body().path("data").path("films")).hasSize(6);
        assertThat(post("/api/chat", "{\"message\":\" \",\"history\":[]}").statusCode())
                .isEqualTo(400);
        assertThat(
                        post(
                                        "/api/chat",
                                        "{\"message\":\"hi\",\"history\":[],\"provider\":\"unknown\"}")
                                .statusCode())
                .isEqualTo(400);
        assertThat(
                        post(
                                        "/api/chat",
                                        "{\"message\":\"hi\",\"override\":\"INVALID\",\"history\":[]}")
                                .statusCode())
                .isEqualTo(400);
        assertThat(
                        post(
                                        "/api/chat",
                                        "{\"message\":\"hi\",\"history\":[{\"role\":\"system\",\"content\":\"bad\"}]}")
                                .statusCode())
                .isEqualTo(400);
    }

    @Test
    void ndjsonEndpointStreamsDecisionAndResult() throws Exception {
        var response =
                post("/api/chat", "{\"message\":\"falcon\",\"override\":\"REST\",\"history\":[]}");
        assertThat(response.statusCode()).isEqualTo(200);
        var lines = response.body().lines().toList();
        assertThat(lines).hasSize(2);
        assertThat(mapper.readTree(lines.get(0)).path("type").asText()).isEqualTo("decision");
        assertThat(mapper.readTree(lines.get(1)).path("data").path("answer").asText())
                .contains("Millennium Falcon");
    }

    @Test
    void httpErrorsAndUnavailableServicesAreBounded() {
        assertThatThrownBy(() -> jsonHttp.send("GET", url("/missing"), null, Map.of()))
                .hasMessageContaining("HTTP 404");
        assertThatThrownBy(() -> jsonHttp.send("GET", "http://127.0.0.1:1", null, Map.of()))
                .hasMessageContaining("Cannot reach");
    }

    String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    HttpResponse<String> get(String path) throws Exception {
        return HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create(url(path))).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> post(String path, String body) throws Exception {
        return HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create(url(path)))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(body))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
    }
}
