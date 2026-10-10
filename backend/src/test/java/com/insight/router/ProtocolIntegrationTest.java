package com.insight.router;

import static com.insight.router.ChatModels.*;
import static org.assertj.core.api.Assertions.*;

import java.net.URI;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"router.warmup=false", "demo.flight.interval-ms=20"})
class ProtocolIntegrationTest {
    @Autowired ChatService service;
    @Autowired JsonMapper mapper;
    @Autowired JsonHttp jsonHttp;
    @LocalServerPort int port;

    @Test
    void oneSubscriptionStreamsACompleteChangingFlightAndCanReplayIt() {
        for (int replay = 0; replay < 2; replay++) {
            var events = new ArrayList<Event>();
            var result =
                    service.chat(
                            new Request("Track the Millennium Falcon", Protocol.GRPC, List.of()),
                            events::add);
            var response = result.insight().exchange().response();
            assertThat(result.insight().exchange().operation()).endsWith("/Watch");
            assertThat(result.insight().exchange().request().path("target").asString())
                    .isEqualTo("ship:5");
            assertThat(events.stream().filter(e -> e.type().equals("snapshot"))).hasSize(21);
            assertThat(response).hasSize(21);
            var locations = new HashSet<String>();
            var pilots = new HashSet<String>();
            for (int i = 0; i < 21; i++) {
                var update = response.get(i);
                assertThat(update.path("sequence").asInt()).isEqualTo(i + 1);
                assertThat(update.path("totalSnapshots").asInt()).isEqualTo(21);
                assertThat(update.path("flight").path("simulated").asBoolean()).isTrue();
                locations.add(update.path("flight").path("location").asString());
                pilots.add(update.path("flight").path("pilot").path("name").asString());
            }
            // Slim stream: only the first message repeats the ship record.
            assertThat(response.get(0).path("starships")).hasSize(1);
            for (int i = 1; i < 21; i++) assertThat(response.get(i).path("starships")).isEmpty();
            var stream = result.insight().exchange().stream();
            assertThat(stream).hasSize(21);
            assertThat(stream.stream().mapToInt(StreamMessage::bytes).sum())
                    .isEqualTo(result.insight().exchange().responseBytes());
            assertThat(stream.get(1).bytes()).isLessThan(stream.get(0).bytes() / 2);
            for (int i = 1; i < 21; i++) {
                assertThat(stream.get(i).sequence()).isEqualTo(i + 1);
                assertThat(stream.get(i).elapsedMs())
                        .isGreaterThanOrEqualTo(stream.get(i - 1).elapsedMs());
            }
            var wire =
                    events.stream()
                            .filter(e -> e.type().equals("snapshot"))
                            .map(Event::meta)
                            .toList();
            assertThat(wire).containsExactlyElementsOf(stream);
            assertThat(locations.size()).isGreaterThan(10);
            assertThat(pilots).containsExactlyInAnyOrder("Han Solo", "Chewbacca");
            assertThat(response.get(7).path("flight").path("shieldsPercent").asInt())
                    .isLessThan(100);
            assertThat(response.get(0).path("flight").path("progressPercent").asInt()).isZero();
            assertThat(response.get(20).path("flight").path("progressPercent").asInt())
                    .isEqualTo(100);
            assertThat(result.answer())
                    .contains(
                            "Simulated flight complete",
                            "Yavin 4",
                            "21 updates",
                            "not canon history");
        }
    }

    @Test
    void standaloneFilmAndCastLookupsUseRealRestResources() {
        var character =
                service.chat(
                        new Request("Tell me about Luke Skywalker", Protocol.REST, List.of()),
                        e -> {});
        assertThat(character.answer()).contains("Luke Skywalker", "character");
        assertThat(character.insight().exchange().operation()).isEqualTo("GET /api/catalog/cast/1");
        var film =
                service.chat(
                        new Request("Tell me about A New Hope", Protocol.REST, List.of()), e -> {});
        assertThat(film.answer()).contains("A New Hope", "Luke Skywalker", "Han Solo");
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

    @Autowired ProtocolGateway gateway;
    @Autowired AnswerComposer composer;

    Result run(Operation operation, String target) {
        var decision = new Decision(operation, target, "test", mapper.createObjectNode());
        var exchange = gateway.invoke(decision, e -> {});
        return new Result(
                composer.compose(decision, exchange),
                new Insight(decision, exchange, new Timings(0, 0)));
    }

    @Test
    void graphqlOperationsSelectOnlyWhatTheyAnswerAndMeasureTheRestEquivalent() {
        var cast = run(Operation.FILM, "film:1");
        assertThat(cast.answer()).contains("Luke Skywalker").doesNotContain("CR90", "Falcon");
        assertThat(cast.insight().exchange().comparison()).isNull();

        var ships = run(Operation.SHIPS_IN_FILM, "film:1");
        assertThat(ships.answer())
                .startsWith("Starships in A New Hope:")
                .contains("Millennium Falcon", "Death Star")
                .doesNotContain("Luke Skywalker", "Maximum speed");
        var shipData = ships.insight().exchange().response().path("data");
        assertThat(shipData.path("starships").get(0).propertyNames()).containsExactly("name");
        var comparison = ships.insight().exchange().comparison();
        assertThat(comparison.calls()).containsExactly("GET /api/catalog/starships/film:1");
        // Count precisely the one real body that already contains the film and ship names.
        assertThat(comparison.responseBytes())
                .isEqualTo(
                        jsonHttp.send("GET", url("/api/catalog/starships/film:1"), null, Map.of())
                                .bytes());
        // The point of the demo: REST returns far more for the same answer.
        assertThat(comparison.responseBytes())
                .isGreaterThan(ships.insight().exchange().responseBytes() * 5);

        var crew = run(Operation.FILM_SHIPS_CREW, "film:1");
        assertThat(crew.answer())
                .contains("Millennium Falcon — Han Solo (pilot), Chewbacca (co-pilot)")
                .contains("CR90 corvette — no verified crew records")
                .doesNotContain("Lando", "Luke Skywalker (");

        assertThat(run(Operation.SHIP_FILMS, "ship:5").answer())
                .contains("Millennium Falcon appears in A New Hope", "Leia Organa");
        var filteredCrew = run(Operation.SHIP_CREW, "crew:5:3");
        assertThat(filteredCrew.answer())
                .contains("Lando Calrissian", "Nien Nunb")
                .doesNotContain("Han Solo");
        assertThat(filteredCrew.insight().exchange().comparison().calls())
                .containsExactly("GET /api/catalog/starships/crew:5:3");
        assertThat(filteredCrew.insight().exchange().comparison().responseBytes())
                .isEqualTo(
                        jsonHttp.send("GET", url("/api/catalog/starships/crew:5:3"), null, Map.of())
                                .bytes());
        var character = run(Operation.CHARACTER_FILMS, "cast:1");
        assertThat(character.answer())
                .startsWith("Luke Skywalker appears in")
                .contains("Starships in those films", "X-wing");
        assertThat(character.insight().exchange().comparison().calls()).hasSize(3);
        assertThat(run(Operation.COMPARE_STARSHIPS, "all").answer())
                .contains("Fastest of 19 starships", "Largest: Death Star");
        for (var op :
                List.of(
                        Operation.SHIPS_IN_FILM,
                        Operation.FILM_SHIPS_CAST,
                        Operation.FILM_SHIPS_CREW,
                        Operation.SHIP_FILMS,
                        Operation.SHIP_CREW,
                        Operation.CHARACTER_FILMS,
                        Operation.COMPARE_STARSHIPS))
            assertThat(run(op, "none").answer()).contains("No matching starships");
    }

    @Test
    void combinedFilmShipsAndCastAnswerIncludesBothWithoutCrewOrSpecs() {
        var result =
                service.chat(
                        new Request(
                                "Which starships appear in A New Hope, and who is in its cast?",
                                Protocol.GRAPHQL,
                                List.of()),
                        e -> {});
        assertThat(result.insight().decision().operation()).isEqualTo(Operation.FILM_SHIPS_CAST);
        assertThat(result.answer())
                .contains(
                        "Starships in A New Hope:",
                        "Millennium Falcon",
                        "Death Star",
                        "Luke Skywalker",
                        "Leia Organa",
                        "Han Solo",
                        "Selected cast")
                .doesNotContain("Maximum speed", "(pilot)", "Mark Hamill");
        var data = result.insight().exchange().response().path("data");
        assertThat(data.path("starships").get(0).propertyNames()).containsExactly("name");
        assertThat(data.path("films").get(0).propertyNames()).containsExactly("title", "cast");
        assertThat(result.insight().exchange().comparison().calls())
                .containsExactly("GET /api/catalog/starships/film:1");
    }

    @Test
    void allThreeRealTransportsProduceGroundedAnswers() {
        for (Protocol protocol : Protocol.values()) {
            var events = new ArrayList<Event>();
            var result =
                    service.chat(
                            new Request("Tell me about the X-wing", protocol, List.of()),
                            events::add);
            assertThat(result.answer())
                    .contains("X-wing", "The Empire Strikes Back", "Luke Skywalker");
            assertThat(result.insight().exchange().responseBytes()).isPositive();
            assertThat(result.insight().timings().routingMs()).isNotNegative();
            assertThat(result.insight().timings().totalMs())
                    .isGreaterThanOrEqualTo(result.insight().exchange().durationMs());
            assertThat(events.getFirst().type()).isEqualTo("decision");
            if (protocol == Protocol.GRPC) {
                assertThat(events.stream().filter(e -> e.type().equals("snapshot"))).hasSize(1);
                assertThat(result.insight().exchange().response()).hasSize(1);
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
                        "/api/catalog/cast",
                        "/api/catalog/starships/all",
                        "/actuator/health")) {
            assertThat(get(path).statusCode()).isEqualTo(200);
        }
        assertThat(get("/api/catalog/starships/missing").statusCode()).isEqualTo(400);
        var graphql =
                jsonHttp.send(
                        "POST",
                        url("/graphql"),
                        mapper.readTree("{\"query\":\"{ films { title } cast { name } }\"}"),
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
    void clmIsAcceptedByTheHttpApiAndPresenterOverridesBypassItsConfiguration() throws Exception {
        var response =
                post(
                        "/api/chat",
                        "{\"message\":\"falcon\",\"override\":\"REST\",\"history\":[],\"provider\":\"clm\"}");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Presenter override", "Millennium Falcon");
    }

    @Test
    void ndjsonEndpointStreamsDecisionAndResult() throws Exception {
        var response =
                post("/api/chat", "{\"message\":\"falcon\",\"override\":\"REST\",\"history\":[]}");
        assertThat(response.statusCode()).isEqualTo(200);
        var lines = response.body().lines().toList();
        assertThat(lines).hasSize(2);
        assertThat(mapper.readTree(lines.get(0)).path("type").asString()).isEqualTo("decision");
        // Wire metadata is only sent on gRPC snapshot events.
        assertThat(mapper.readTree(lines.get(0)).has("meta")).isFalse();
        assertThat(mapper.readTree(lines.get(1)).path("data").path("answer").asString())
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
