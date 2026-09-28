package com.insight.router;

import tools.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.util.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class Catalog {
    public record Producer(String id, String name) {}

    public record Film(String id, String title, List<Producer> producers) {
        public Film {
            producers = List.copyOf(producers);
        }
    }

    public record Starship(String id, String name, int maxSpeed, double length, List<Film> films) {
        public Starship {
            films = List.copyOf(films);
        }
    }

    public record Dataset(List<Starship> starships, List<Film> films, List<Producer> producers) {
        public Dataset {
            starships = List.copyOf(starships);
            films = List.copyOf(films);
            producers = List.copyOf(producers);
        }
    }

    private final Dataset data;

    public Catalog() {
        try (var input = new ClassPathResource("catalog.json").getInputStream()) {
			var mapper = JsonMapper.builder().build();
            data = mapper.readValue(input, Dataset.class);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load the canonical catalog", e);
        }
    }

    public List<Starship> starships(String target) {
        if ("all".equals(target)) return data.starships();
        if ("none".equals(target)) return List.of();
        if (!targets().containsKey(target))
            throw new IllegalArgumentException("Unknown catalog target: " + target);
        return data.starships().stream().filter(ship -> matches(ship, target)).toList();
    }

    private boolean matches(Starship ship, String target) {
        return target.equals("ship:" + ship.id())
                || ship.films().stream().anyMatch(film -> matches(film, target));
    }

    private boolean matches(Film film, String target) {
        return target.equals("film:" + film.id())
                || film.producers().stream()
                        .anyMatch(producer -> target.equals("producer:" + producer.id()));
    }

    public List<Film> films() {
        return data.films();
    }

    public List<Producer> producers() {
        return data.producers();
    }

    public Producer producer(String id) {
        return producers().stream()
                .filter(p -> p.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown producer"));
    }

    public Film film(String id) {
        return films().stream()
                .filter(f -> f.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown film"));
    }

    public Map<String, String> targets() {
        var targets = new LinkedHashMap<String, String>();
        targets.put("all", "All Star Wars catalog records, broad or ambiguous questions");
        targets.put("none", "Outside the catalog, including Star Trek or USS Enterprise");
        data.starships().forEach(s -> targets.put("ship:" + s.id(), s.name()));
        data.films().forEach(f -> targets.put("film:" + f.id(), f.title()));
        data.producers().forEach(p -> targets.put("producer:" + p.id(), p.name()));
        return Collections.unmodifiableMap(targets);
    }

    public String manualTarget(String message) {
        String text = message.toLowerCase(Locale.ROOT);
        if (text.contains("enterprise") || text.contains("star trek")) return "none";
        if (text.contains("falcon")) return "ship:5";
        return targets().entrySet().stream()
                .filter(e -> text.contains(e.getValue().toLowerCase(Locale.ROOT)))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse("all");
    }
}
