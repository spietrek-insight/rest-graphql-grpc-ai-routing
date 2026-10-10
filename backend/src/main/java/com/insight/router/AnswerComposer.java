package com.insight.router;

import static com.insight.router.ChatModels.*;

import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Composes answers from the response alone, so each answer matches what its request selected. */
@Component
public class AnswerComposer {
    private static final String NO_MATCH =
            "No matching starships are linked to this target in the Star Wars demo catalog. Try the Millennium Falcon or A New Hope. Star Trek and USS Enterprise are not in this dataset.";

    public String compose(Decision decision, Exchange exchange) {
        var response = exchange.response();
        var data = response.path("data");
        return switch (decision.operation()) {
            case TRACK -> track(decision.target(), response);
            case CHARACTER ->
                    response.path("name").asString()
                            + " is a character in the curated demo cast. Ask which films feature this character and which ships appear in those films to explore cast relationships.";
            case FILM -> filmCast(response);
            case STARSHIP -> starships(decision.target(), response);
            case CREW_RECORDS -> crew(response);
            case SHIP_CREW -> crew(data.path("starships"));
            case SHIPS_IN_FILM -> shipsInFilm(data);
            case FILM_SHIPS_CAST -> filmShipsCast(data);
            case FILM_SHIPS_CREW -> filmShipsCrew(data);
            case SHIP_FILMS -> shipFilms(data.path("starships"));
            case CHARACTER_FILMS -> characterFilms(data);
            case COMPARE_STARSHIPS -> compare(data.path("starships"));
        };
    }

    private String track(String target, JsonNode response) {
        var last = response.get(response.size() - 1);
        if (last.has("flight")) {
            var flight = last.path("flight");
            return "Simulated flight complete — "
                    + flight.path("shipName").asString()
                    + " is at "
                    + flight.path("location").asString()
                    + ". Final pilot: "
                    + flight.path("pilot").path("name").asString()
                    + "; co-pilot: "
                    + flight.path("coPilot").path("name").asString()
                    + ".\n\nReceived "
                    + response.size()
                    + " updates from one subscription. "
                    + "The location, pilot handoff and alert were scripted presentation events, not canon history or live telemetry. Ask again to replay the same flight.";
        }
        var ships = last.path("starships");
        var answer = target.startsWith("crew:") ? crew(ships) : starships(target, ships);
        return answer
                + "\n\nCatalog only: no simulated flight is available for this target. Try tracking the Millennium Falcon. This is catalog data, not live vessel telemetry.";
    }

    private String starships(String target, JsonNode ships) {
        var lines = new ArrayList<String>();
        for (var ship : ships) lines.add(shipLine(ship));
        if (lines.isEmpty()) return NO_MATCH;
        if (target.equals("all"))
            lines.add(
                    "These are the 19 starships in the original demo catalog, enriched with selected film cast. Cast and flight-crew coverage is partial.");
        return String.join("\n\n", lines);
    }

    private String shipLine(JsonNode ship) {
        var films = new ArrayList<String>();
        var cast = new LinkedHashSet<String>();
        for (var film : ship.path("films")) {
            films.add(film.isString() ? film.asString() : film.path("title").asString());
            film.path("cast").forEach(c -> cast.add(c.path("name").asString()));
        }
        ship.path("cast").forEach(c -> cast.add(c.asString()));
        return ship.path("name").asString()
                + " — Maximum speed: "
                + ship.path("maxSpeed").asInt()
                + "; length: "
                + ship.path("length").asDouble()
                + " (source units unspecified). Appears in "
                + String.join(", ", films)
                + ". Selected cast of linked films (not ship crew): "
                + String.join(", ", cast)
                + ".";
    }

    private String crew(JsonNode ships) {
        var lines = new ArrayList<String>();
        for (var ship : ships) {
            var crew = ship.path("crew");
            if (crew.isEmpty()) {
                lines.add(
                        "No verified flight-crew records for "
                                + ship.path("name").asString()
                                + " in the requested film are included in this demo. Missing data does not mean nobody flew it; film cast is not evidence of piloting.");
                continue;
            }
            for (var c : crew)
                lines.add(
                        ship.path("name").asString()
                                + " in "
                                + c.path("filmTitle").asString()
                                + ": "
                                + c.path("member").path("name").asString()
                                + " — "
                                + c.path("role").asString()
                                + ". Source: "
                                + c.path("source").asString());
            lines.add(
                    "These are the documented crew assignments in this curated demo, not an exhaustive flight history.");
        }
        return lines.isEmpty() ? NO_MATCH : String.join("\n\n", lines);
    }

    private String shipsInFilm(JsonNode data) {
        var names = names(data.path("starships"));
        if (names.isEmpty()) return NO_MATCH;
        return "Starships in "
                + title(data)
                + ": "
                + String.join(", ", names)
                + ". These are the ships linked to this film in the demo catalog.";
    }

    private String filmShipsCast(JsonNode data) {
        var ships = names(data.path("starships"));
        var film = data.path("films").path(0);
        if (ships.isEmpty() && film.isMissingNode()) return NO_MATCH;
        return "Starships in "
                + title(data)
                + ": "
                + String.join(", ", ships)
                + ".\n\n"
                + filmCast(film)
                + " Film cast membership does not establish who flew these ships.";
    }

    private String filmShipsCrew(JsonNode data) {
        var ships = data.path("starships");
        if (ships.isEmpty()) return NO_MATCH;
        var filmId = data.path("films").path(0).path("id").asString();
        var lines = new ArrayList<String>();
        lines.add("Starships in " + title(data) + " and their documented crew:");
        for (var ship : ships) {
            var crew = new ArrayList<String>();
            for (var c : ship.path("crew"))
                if (c.path("filmId").asString().equals(filmId))
                    crew.add(
                            c.path("member").path("name").asString()
                                    + " ("
                                    + c.path("role").asString()
                                    + ")");
            lines.add(
                    ship.path("name").asString()
                            + " — "
                            + (crew.isEmpty()
                                    ? "no verified crew records in this demo"
                                    : String.join(", ", crew)));
        }
        lines.add(
                "Crew coverage is partial: a missing record does not mean nobody flew the ship, and film cast is not evidence of piloting.");
        return String.join("\n\n", lines);
    }

    private String shipFilms(JsonNode ships) {
        var lines = new ArrayList<String>();
        for (var ship : ships) {
            var films = new ArrayList<String>();
            var cast = new LinkedHashSet<String>();
            for (var film : ship.path("films")) {
                films.add(film.path("title").asString());
                film.path("cast").forEach(c -> cast.add(c.path("name").asString()));
            }
            lines.add(
                    ship.path("name").asString()
                            + " appears in "
                            + String.join(", ", films)
                            + ". Selected cast of those films (not ship crew): "
                            + String.join(", ", cast)
                            + ".");
        }
        return lines.isEmpty() ? NO_MATCH : String.join("\n\n", lines);
    }

    private String characterFilms(JsonNode data) {
        var films = new ArrayList<String>();
        data.path("films").forEach(f -> films.add(f.path("title").asString()));
        if (films.isEmpty()) return NO_MATCH;
        return data.path("castMember").path("name").asString()
                + " appears in "
                + String.join(", ", films)
                + ".\n\nStarships in those films: "
                + String.join(", ", names(data.path("starships")))
                + ". Appearing in the same film does not mean the character flew these ships.";
    }

    private String compare(JsonNode ships) {
        var list = new ArrayList<JsonNode>();
        ships.forEach(list::add);
        if (list.isEmpty()) return NO_MATCH;
        list.sort(Comparator.comparingInt((JsonNode s) -> s.path("maxSpeed").asInt()).reversed());
        var fastest = new ArrayList<String>();
        for (var ship : list.subList(0, Math.min(5, list.size())))
            fastest.add(ship.path("name").asString() + " (" + ship.path("maxSpeed").asInt() + ")");
        var largest =
                list.stream()
                        .max(Comparator.comparingDouble(s -> s.path("length").asDouble()))
                        .orElseThrow();
        return "Fastest of "
                + list.size()
                + " starships by maximum speed: "
                + String.join(", ", fastest)
                + ".\n\nLargest: "
                + largest.path("name").asString()
                + " at "
                + largest.path("length").asDouble()
                + " in length. Source units are unspecified.";
    }

    private String filmCast(JsonNode film) {
        return film.path("title").asString()
                + " — Selected cast: "
                + String.join(", ", names(film.path("cast")))
                + ". This is a curated selection, not the full credits.";
    }

    private static String title(JsonNode data) {
        return data.path("films").path(0).path("title").asString("this film");
    }

    private static List<String> names(JsonNode items) {
        var names = new ArrayList<String>();
        items.forEach(i -> names.add(i.path("name").asString()));
        return names;
    }
}
