package com.insight.router;

import java.util.*;

/**
 * A reviewed catalog operation. Routers choose one of these plus a target; they never supply paths
 * or query text. Each operation fixes its protocol, the target kinds it accepts, and for GraphQL
 * the exact selection set, so a request fetches only what the question needs.
 */
public enum Operation {
    STARSHIP(
            Protocol.REST,
            "Details of one starship: speed, length and the films it appears in. Use all for broad listings and none for anything outside the catalog.",
            "Single-resource lookup: one URL, one cacheable JSON response.",
            Set.of("ship", "all", "none"),
            null),
    FILM(
            Protocol.REST,
            "One film and its cast of characters, for example who is in the cast of A New Hope.",
            "Single-resource lookup: the film resource already includes its cast.",
            Set.of("film"),
            null),
    CHARACTER(
            Protocol.REST,
            "One character's record, for example tell me about Luke Skywalker.",
            "Single-resource lookup: one character, one URL.",
            Set.of("cast"),
            null),
    SHIPS_IN_FILM(
            Protocol.GRAPHQL,
            "Which starships appear in a film.",
            "One GraphQL query selects only the film title and ship names.",
            Set.of("film", "none"),
            "query ShipsInFilm($target: String!) { films(target: $target) { title } starships(target: $target) { name } }"),
    FILM_SHIPS_CREW(
            Protocol.GRAPHQL,
            "Which starships appear in a film and who flew them in that film.",
            "One GraphQL query joins the film, its ships and their crew, selecting only names and roles.",
            Set.of("film", "none"),
            "query FilmShipsCrew($target: String!) { films(target: $target) { id title } starships(target: $target) { name crew { filmId role member { name } } } }"),
    SHIP_FILMS(
            Protocol.GRAPHQL,
            "Which films a starship appears in and which characters are in those films.",
            "One GraphQL query walks starship to films to cast, selecting only names.",
            Set.of("ship", "none"),
            "query ShipFilms($target: String!) { starships(target: $target) { name films { title cast { name } } } }"),
    SHIP_CREW(
            Protocol.GRAPHQL,
            "Who flew or piloted a starship in the films, in one film or across films. Not for who is flying it now.",
            "One GraphQL query selects only the crew records for this ship and film.",
            Set.of("crew", "none"),
            "query ShipCrew($target: String!) { starships(target: $target) { name crew { filmTitle role source member { name } } } }"),
    CHARACTER_FILMS(
            Protocol.GRAPHQL,
            "Which films a character appears in and which starships appear in those films.",
            "One GraphQL query returns the character, their films and ships together.",
            Set.of("cast", "none"),
            "query CharacterFilms($target: String!) { castMember(target: $target) { name } films(target: $target) { title } starships(target: $target) { name } }"),
    COMPARE_STARSHIPS(
            Protocol.GRAPHQL,
            "Compare starships across the whole catalog, for example the fastest or largest ships.",
            "One GraphQL query selects only name, speed and length for every ship.",
            Set.of("all", "none"),
            "query CompareStarships($target: String!) { starships(target: $target) { name maxSpeed length } }"),
    TRACK(
            Protocol.GRPC,
            "Track a starship, or ask where it is now or who is flying it currently. Starts a clearly labeled simulation.",
            Protocol.GRPC.rationale(),
            Set.of("ship"),
            null),
    // Presenter overrides only: never offered to a model.
    CREW_RECORDS(
            Protocol.REST,
            "Crew records for one starship, filtered by film.",
            "Presenter-selected REST lookup of crew records for one starship.",
            Set.of("crew"),
            null);

    private final Protocol protocol;
    private final String description;
    private final String reason;
    private final Set<String> kinds;
    private final String query;

    Operation(
            Protocol protocol, String description, String reason, Set<String> kinds, String query) {
        this.protocol = protocol;
        this.description = description;
        this.reason = reason;
        this.kinds = kinds;
        this.query = query;
    }

    public Protocol protocol() {
        return protocol;
    }

    /** What the operation answers; this is the routing guidance every provider sees. */
    public String description() {
        return description;
    }

    /** Why this protocol fits, shown to the audience. */
    public String reason() {
        return reason;
    }

    public String query() {
        return query;
    }

    /** "query ShipsInFilm" for GraphQL operations. */
    public String queryName() {
        return query.substring(0, query.indexOf('('));
    }

    public boolean routable() {
        return this != CREW_RECORDS;
    }

    public static List<Operation> routableValues() {
        return Arrays.stream(values()).filter(Operation::routable).toList();
    }

    /** Target kinds offered to a model for this operation. */
    public boolean offers(String target) {
        return kinds.contains(kind(target));
    }

    /** Overrides may stream any catalog target over gRPC; the tracker handles each one. */
    public boolean accepts(String target) {
        return this == TRACK || offers(target);
    }

    /**
     * Applies structural corrections when a router's operation and target disagree in kind, for
     * example SHIP_CREW with ship:5. Anything else is rejected rather than guessed.
     */
    public String normalize(String target) {
        if (accepts(target)) return target;
        var parts = target.split(":");
        var kind = kind(target);
        if (kind.equals("crew") && kinds.contains("ship")) return "ship:" + parts[1];
        if (kind.equals("crew") && kinds.contains("film") && !parts[2].equals("all"))
            return "film:" + parts[2];
        if (kind.equals("ship") && kinds.contains("crew")) return "crew:" + parts[1] + ":all";
        throw new IllegalArgumentException(
                "The router chose a target that does not fit " + name() + ".");
    }

    public String restPath(String target) {
        return switch (this) {
            case FILM -> "/api/catalog/films/" + id(target);
            case CHARACTER -> "/api/catalog/cast/" + id(target);
            default -> "/api/catalog/starships/" + target;
        };
    }

    /** The REST calls a client would make to fetch the same data, measured for comparison. */
    public List<String> restEquivalent(String target) {
        if (target.equals("none")) return List.of("/api/catalog/starships/none");
        return switch (this) {
            case SHIPS_IN_FILM, FILM_SHIPS_CREW ->
                    List.of("/api/catalog/films/" + id(target), "/api/catalog/starships/" + target);
            case SHIP_FILMS, COMPARE_STARSHIPS -> List.of("/api/catalog/starships/" + target);
            case SHIP_CREW -> List.of("/api/catalog/starships/ship:" + target.split(":")[1]);
            case CHARACTER_FILMS ->
                    List.of(
                            "/api/catalog/cast/" + id(target),
                            "/api/catalog/films",
                            "/api/catalog/starships/" + target);
            default -> List.of();
        };
    }

    /** Deterministic mapping for presenter overrides, which bypass the model. */
    public static Operation forOverride(Protocol protocol, String target, boolean mentionsCrew) {
        var kind = kind(target);
        return switch (protocol) {
            case REST ->
                    switch (kind) {
                        case "crew" -> CREW_RECORDS;
                        case "film" -> FILM;
                        case "cast" -> CHARACTER;
                        default -> STARSHIP;
                    };
            case GRAPHQL ->
                    switch (kind) {
                        case "crew" -> SHIP_CREW;
                        case "film" -> mentionsCrew ? FILM_SHIPS_CREW : SHIPS_IN_FILM;
                        case "cast" -> CHARACTER_FILMS;
                        case "ship" -> SHIP_FILMS;
                        default -> COMPARE_STARSHIPS;
                    };
            case GRPC -> TRACK;
        };
    }

    static String kind(String target) {
        int colon = target.indexOf(':');
        return colon < 0 ? target : target.substring(0, colon);
    }

    private static String id(String target) {
        return target.substring(target.indexOf(':') + 1);
    }
}
