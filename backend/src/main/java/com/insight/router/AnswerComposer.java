package com.insight.router;

import static com.insight.router.ChatModels.*;

import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class AnswerComposer {
    public String compose(Decision decision, Exchange exchange) {
        if (decision.protocol() == Protocol.REST && decision.target().startsWith("producer:")) {
            return exchange.response().path("name").asString()
                    + " is producer #"
                    + exchange.response().path("id").asString()
                    + " in the original demo catalog. Ask which starships and films are connected to this producer to explore relationships.";
        }
        if (decision.protocol() == Protocol.REST && decision.target().startsWith("film:")) {
            var names = new ArrayList<String>();
            exchange.response()
                    .path("producers")
                    .forEach(p -> names.add(p.path("name").asString()));
            return exchange.response().path("title").asString()
                    + " is film #"
                    + exchange.response().path("id").asString()
                    + ". Its producers in the source dataset are "
                    + String.join(", ", names)
                    + ".";
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
        var lines = new ArrayList<String>();
        for (JsonNode ship : ships) {
            var films = new ArrayList<String>();
            var producers = new LinkedHashSet<String>();
            for (var film : ship.path("films")) {
                films.add(film.isString() ? film.asString() : film.path("title").asString());
                film.path("producers").forEach(p -> producers.add(p.path("name").asString()));
            }
            ship.path("producers").forEach(p -> producers.add(p.asString()));
            lines.add(
                    ship.path("name").asString()
                            + " — "
                            + "Maximum speed: "
                            + ship.path("maxSpeed").asInt()
                            + "; length: "
                            + ship.path("length").asDouble()
                            + " (source units unspecified). Appears in "
                            + String.join(", ", films)
                            + ". Producer: "
                            + String.join(", ", producers)
                            + ".");
        }
        if (lines.isEmpty())
            lines.add(
                    "No matching starships are linked to this target in the original Star Wars catalog. Try the Millennium Falcon or A New Hope. Star Trek and USS Enterprise are not in this dataset.");
        if (decision.target().equals("all"))
            lines.add(
                    "These are the 19 starships in the original demo catalog. Credits and values are preserved from its source dataset.");
        if (decision.protocol() == Protocol.GRPC)
            lines.add(
                    "Received 3 catalog snapshots through a live bidirectional gRPC stream. This is demonstration data, not live vessel telemetry.");
        return String.join("\n\n", lines);
    }
}
