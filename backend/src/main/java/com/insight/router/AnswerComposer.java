package com.insight.router;

import static com.insight.router.ChatModels.*;

import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class AnswerComposer {
    public String compose(Decision decision, Exchange exchange) {
        if (decision.protocol() == Protocol.GRPC && !exchange.response().isEmpty()) {
            var last = exchange.response().get(exchange.response().size() - 1);
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
                        + exchange.response().size()
                        + " updates from one subscription. "
                        + "The location, pilot handoff and alert were scripted presentation events, not canon history or live telemetry. Ask again to replay the same flight.";
            }
        }
        if (decision.protocol() == Protocol.REST && decision.target().startsWith("cast:")) {
            return exchange.response().path("name").asString()
                    + " is a character in the curated demo cast. Ask which films feature this character and which ships appear in those films to explore cast relationships.";
        }
        if (decision.protocol() == Protocol.REST && decision.target().startsWith("film:"))
            return filmCast(exchange.response(), null);
        var lines = new ArrayList<String>();
        if (decision.protocol() == Protocol.GRAPHQL
                && (decision.target().startsWith("film:")
                        || decision.target().startsWith("cast:"))) {
            var castId =
                    decision.target().startsWith("cast:") ? decision.target().substring(5) : null;
            exchange.response()
                    .path("data")
                    .path("films")
                    .forEach(f -> lines.add(filmCast(f, castId)));
        }
        JsonNode ships =
                switch (decision.protocol()) {
                    case REST -> exchange.response();
                    case GRAPHQL -> exchange.response().path("data").path("starships");
                    case GRPC ->
                            exchange.response()
                                    .get(exchange.response().size() - 1)
                                    .path("starships");
                };
        for (var ship : ships) {
            if (decision.target().startsWith("crew:")) {
                var crew = ship.path("crew");
                if (crew.isEmpty())
                    lines.add(
                            "No verified flight-crew records for "
                                    + ship.path("name").asString()
                                    + " in the requested film are included in this demo. Missing data does not mean nobody flew it; film cast is not evidence of piloting.");
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
                if (!crew.isEmpty())
                    lines.add(
                            "These are the documented crew assignments in this curated demo, not an exhaustive flight history.");
                continue;
            }
            var films = new ArrayList<String>();
            var cast = new LinkedHashSet<String>();
            for (var film : ship.path("films")) {
                films.add(film.isString() ? film.asString() : film.path("title").asString());
                film.path("cast").forEach(c -> cast.add(c.path("name").asString()));
            }
            ship.path("cast").forEach(c -> cast.add(c.asString()));
            lines.add(
                    ship.path("name").asString()
                            + " — Maximum speed: "
                            + ship.path("maxSpeed").asInt()
                            + "; length: "
                            + ship.path("length").asDouble()
                            + " (source units unspecified). Appears in "
                            + String.join(", ", films)
                            + "."
                            + (decision.target().startsWith("film:")
                                            || decision.target().startsWith("cast:")
                                    ? ""
                                    : " Selected cast of linked films (not ship crew): "
                                            + String.join(", ", cast)
                                            + "."));
        }
        if (lines.isEmpty())
            lines.add(
                    "No matching starships are linked to this target in the Star Wars demo catalog. Try the Millennium Falcon or A New Hope. Star Trek and USS Enterprise are not in this dataset.");
        if (decision.target().equals("all"))
            lines.add(
                    "These are the 19 starships in the original demo catalog, enriched with selected film cast. Cast and flight-crew coverage is partial.");
        if (decision.protocol() == Protocol.GRPC)
            lines.add(
                    "Catalog only: no simulated flight is available for this target. Try tracking the Millennium Falcon. This is catalog data, not live vessel telemetry.");
        return String.join("\n\n", lines);
    }

    private String filmCast(JsonNode film, String castId) {
        var names = new ArrayList<String>();
        for (var c : film.path("cast"))
            if (castId == null || c.path("id").asString().equals(castId))
                names.add(c.path("name").asString());
        return film.path("title").asString()
                + " — Selected cast: "
                + String.join(", ", names)
                + ". This is a curated selection, not the full credits.";
    }
}
