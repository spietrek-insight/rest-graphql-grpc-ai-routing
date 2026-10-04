package com.insight.router;

import static com.insight.router.ChatModels.*;
import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"router.warmup=false"})
class CastCrewTest {
    @Autowired Catalog catalog;
    @Autowired ChatService service;
    @Autowired JsonMapper mapper;

    @Test
    void crewIsFilmSpecificAndNeverInferredFromCast() {
        assertThat(catalog.starships("crew:5:1").getFirst().crew())
                .extracting(c -> c.member().name())
                .containsExactly("Han Solo", "Chewbacca");
        assertThat(catalog.starships("crew:5:3").getFirst().crew())
                .extracting(c -> c.member().name())
                .containsExactly("Lando Calrissian", "Nien Nunb");
        assertThat(catalog.starships("crew:5:2").getFirst().crew()).isEmpty();
        assertThat(catalog.film("1").cast())
                .extracting(Catalog.CastMember::name)
                .contains("Luke Skywalker", "Leia Organa");
        assertThat(catalog.starships("crew:5:all").getFirst().crew()).hasSize(4);
        assertThat(catalog.starships("crew:5:6").getFirst().crew()).isEmpty();
        assertThat(catalog.manualTarget("who flew the millennium falcon in new hope"))
                .isEqualTo("crew:5:1");
        assertThat(catalog.manualTarget("Who piloted the Falcon in Return of the Jedi?"))
                .isEqualTo("crew:5:3");
        assertThatThrownBy(() -> catalog.starships("crew:20:1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Decision(Protocol.GRAPHQL, "producer:1", "test", "", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalizedSeedResolvesEveryReferenceAndIsImmutable() {
        for (var ship : catalog.starships("all")) {
            for (var film : ship.films()) assertThat(film).isSameAs(catalog.film(film.id()));
            for (var crew : ship.crew()) {
                assertThat(catalog.film(crew.filmId()).cast()).contains(crew.member());
                assertThat(crew.source()).startsWith("https://www.starwars.com/");
            }
        }
        assertThatThrownBy(() -> catalog.film("1").cast().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(mapper.valueToTree(catalog.films()).toString())
                .doesNotContain("Hamill", "Ford", "actor", "producer");
    }

    @Test
    void everyTransportReturnsGroundedCrewAndHandlesMissingCoverage() {
        for (var protocol : Protocol.values()) {
            var events = new ArrayList<Event>();
            var result =
                    service.chat(
                            new Request(
                                    "Who flew the Millennium Falcon in A New Hope?",
                                    protocol,
                                    List.of()),
                            events::add);
            assertThat(result.answer())
                    .contains("Han Solo", "Chewbacca", "co-pilot", "A New Hope")
                    .doesNotContain("Lando", "Luke", "Leia", "Harrison", "Peter");
            assertThat(result.insight().decision().target()).isEqualTo("crew:5:1");
            if (protocol == Protocol.GRPC)
                assertThat(events.stream().filter(e -> e.type().equals("snapshot"))).hasSize(3);
            var missing =
                    service.chat(
                            new Request("Who flew the X-wing in A New Hope?", protocol, List.of()),
                            e -> {});
            assertThat(missing.answer())
                    .contains("No verified flight-crew records")
                    .doesNotContain("Luke Skywalker");
        }
    }
}
