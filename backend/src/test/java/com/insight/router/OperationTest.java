package com.insight.router;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class OperationTest {
    @Test
    void everyOperationFixesItsProtocolAndGraphqlSelection() {
        for (var op : Operation.values()) {
            assertThat(op.description()).isNotBlank();
            assertThat(op.reason()).isNotBlank();
            assertThat(op.query() != null).isEqualTo(op.protocol() == Protocol.GRAPHQL);
        }
        assertThat(Operation.FILM_SHIPS_CREW.queryName()).isEqualTo("query FilmShipsCrew");
        assertThat(Operation.routableValues()).doesNotContain(Operation.CREW_RECORDS);
        // A cast-only question can no longer fetch ships, specs or crew.
        assertThat(Operation.SHIPS_IN_FILM.query()).doesNotContain("crew", "cast", "maxSpeed");
    }

    @Test
    void mismatchedTargetsAreCorrectedStructurallyOrRejected() {
        assertThat(Operation.SHIP_CREW.normalize("ship:5")).isEqualTo("crew:5:all");
        assertThat(Operation.FILM_SHIPS_CREW.normalize("crew:5:1")).isEqualTo("film:1");
        assertThat(Operation.STARSHIP.normalize("crew:5:1")).isEqualTo("ship:5");
        assertThat(Operation.TRACK.normalize("crew:5:1")).isEqualTo("crew:5:1");
        assertThat(Operation.FILM.normalize("film:2")).isEqualTo("film:2");
        assertThatThrownBy(() -> Operation.FILM.normalize("crew:5:all"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Operation.FILM.normalize("cast:1"))
                .hasMessageContaining("does not fit FILM");
        assertThat(Operation.TRACK.offers("crew:5:1")).isFalse();
        assertThat(Operation.TRACK.accepts("film:1")).isTrue();
    }

    @Test
    void presenterOverridesMapToOneOperationPerProtocolAndTarget() {
        assertThat(Operation.forOverride(Protocol.REST, "crew:5:1", true))
                .isEqualTo(Operation.CREW_RECORDS);
        assertThat(Operation.forOverride(Protocol.REST, "film:1", false)).isEqualTo(Operation.FILM);
        assertThat(Operation.forOverride(Protocol.REST, "cast:1", false))
                .isEqualTo(Operation.CHARACTER);
        assertThat(Operation.forOverride(Protocol.REST, "all", false))
                .isEqualTo(Operation.STARSHIP);
        assertThat(Operation.forOverride(Protocol.GRAPHQL, "crew:5:1", true))
                .isEqualTo(Operation.SHIP_CREW);
        assertThat(Operation.forOverride(Protocol.GRAPHQL, "film:1", true))
                .isEqualTo(Operation.FILM_SHIPS_CREW);
        assertThat(Operation.forOverride(Protocol.GRAPHQL, "film:1", false))
                .isEqualTo(Operation.SHIPS_IN_FILM);
        assertThat(Operation.forOverride(Protocol.GRAPHQL, "cast:1", false))
                .isEqualTo(Operation.CHARACTER_FILMS);
        assertThat(Operation.forOverride(Protocol.GRAPHQL, "ship:5", false))
                .isEqualTo(Operation.SHIP_FILMS);
        assertThat(Operation.forOverride(Protocol.GRAPHQL, "none", false))
                .isEqualTo(Operation.COMPARE_STARSHIPS);
        assertThat(Operation.forOverride(Protocol.GRPC, "film:1", false))
                .isEqualTo(Operation.TRACK);
    }

    @Test
    void restEquivalentsAreRealResourcePaths() {
        assertThat(Operation.FILM_SHIPS_CREW.restEquivalent("film:1"))
                .containsExactly("/api/catalog/films/1", "/api/catalog/starships/film:1");
        assertThat(Operation.SHIP_CREW.restEquivalent("crew:5:1"))
                .containsExactly("/api/catalog/starships/ship:5");
        assertThat(Operation.CHARACTER_FILMS.restEquivalent("cast:1")).hasSize(3);
        assertThat(Operation.SHIP_FILMS.restEquivalent("none"))
                .containsExactly("/api/catalog/starships/none");
        assertThat(Operation.STARSHIP.restEquivalent("ship:5")).isEmpty();
        assertThat(Operation.FILM.restPath("film:1")).isEqualTo("/api/catalog/films/1");
        assertThat(Operation.CHARACTER.restPath("cast:3")).isEqualTo("/api/catalog/cast/3");
        assertThat(Operation.CREW_RECORDS.restPath("crew:5:1"))
                .isEqualTo("/api/catalog/starships/crew:5:1");
    }
}
