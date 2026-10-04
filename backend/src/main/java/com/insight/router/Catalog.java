package com.insight.router;

import java.io.IOException;
import java.util.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
public class Catalog {
    private final List<Starship> ships;
    private final List<Film> films;
    private final List<CastMember> cast;

    public Catalog(JsonMapper mapper) {
        try (var input = new ClassPathResource("catalog.json").getInputStream()) {
            var seed = mapper.readValue(input, Dataset.class);
            cast = List.copyOf(seed.cast());
            films =
                    seed.films().stream()
                            .map(
                                    f ->
                                            new Film(
                                                    f.id(),
                                                    f.title(),
                                                    f.castIds().stream()
                                                            .map(this::castMember)
                                                            .toList()))
                            .toList();
            ships =
                    seed.starships().stream()
                            .map(
                                    s ->
                                            new Starship(
                                                    s.id(),
                                                    s.name(),
                                                    s.maxSpeed(),
                                                    s.length(),
                                                    s.filmIds().stream().map(this::film).toList(),
                                                    seed.crew().stream()
                                                            .filter(c -> c.shipId().equals(s.id()))
                                                            .map(
                                                                    c -> {
                                                                        if (!s.filmIds()
                                                                                .contains(
                                                                                        c.filmId()))
                                                                            throw new IllegalArgumentException(
                                                                                    "Crew film is not linked to ship");
                                                                        var f = film(c.filmId());
                                                                        var credit =
                                                                                f.cast().stream()
                                                                                        .filter(
                                                                                                v ->
                                                                                                        v.id().equals(
                                                                                                                        c
                                                                                                                                .castId()))
                                                                                        .findFirst()
                                                                                        .orElseThrow(
                                                                                                () ->
                                                                                                        new IllegalArgumentException(
                                                                                                                "Crew member is not in film cast"));
                                                                        return new Crew(
                                                                                c.filmId(),
                                                                                f.title(),
                                                                                credit,
                                                                                c.role(),
                                                                                c.source());
                                                                    })
                                                            .toList()))
                            .toList();
            if (seed.crew().stream()
                    .anyMatch(c -> ships.stream().noneMatch(s -> s.id().equals(c.shipId()))))
                throw new IllegalArgumentException("Crew references unknown ship");
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load the canonical catalog", e);
        }
    }

    public record CastMember(String id, String name) {}

    public record Film(String id, String title, List<CastMember> cast) {
        public Film {
            cast = List.copyOf(cast);
        }
    }

    public record Crew(
            String filmId, String filmTitle, CastMember member, String role, String source) {}

    public record Starship(
            String id,
            String name,
            int maxSpeed,
            double length,
            List<Film> films,
            List<Crew> crew) {
        public Starship {
            films = List.copyOf(films);
            crew = List.copyOf(crew);
        }
    }

    record SeedFilm(String id, String title, List<String> castIds) {}

    record SeedShip(String id, String name, int maxSpeed, double length, List<String> filmIds) {}

    record SeedCrew(String shipId, String filmId, String castId, String role, String source) {}

    record Dataset(
            List<SeedShip> starships,
            List<SeedFilm> films,
            List<CastMember> cast,
            List<SeedCrew> crew) {}

    public List<Starship> starships(String target) {
        requireTarget(target);
        if ("all".equals(target)) return ships;
        if ("none".equals(target)) return List.of();
        if (target.startsWith("crew:")) {
            var parts = target.split(":");
            return ships.stream()
                    .filter(s -> s.id().equals(parts[1]))
                    .map(
                            s ->
                                    new Starship(
                                            s.id(),
                                            s.name(),
                                            s.maxSpeed(),
                                            s.length(),
                                            s.films().stream()
                                                    .filter(
                                                            f ->
                                                                    parts[2].equals("all")
                                                                            || f.id().equals(
                                                                                            parts[
                                                                                                    2]))
                                                    .toList(),
                                            s.crew().stream()
                                                    .filter(
                                                            c ->
                                                                    parts[2].equals("all")
                                                                            || c.filmId()
                                                                                    .equals(
                                                                                            parts[
                                                                                                    2]))
                                                    .toList()))
                    .toList();
        }
        return ships.stream()
                .filter(
                        s ->
                                target.equals("ship:" + s.id())
                                        || s.films().stream().anyMatch(f -> matches(f, target)))
                .toList();
    }

    private boolean matches(Film film, String target) {
        return target.equals("film:" + film.id())
                || film.cast().stream().anyMatch(c -> target.equals("cast:" + c.id()));
    }

    public List<Film> films() {
        return films;
    }

    public List<Film> films(String target) {
        requireTarget(target);
        if (target.equals("all")) return films;
        if (target.startsWith("ship:") || target.startsWith("crew:"))
            return starships(target).stream().flatMap(s -> s.films().stream()).distinct().toList();
        return films.stream().filter(f -> matches(f, target)).toList();
    }

    public List<CastMember> cast() {
        return cast;
    }

    public CastMember castMember(String id) {
        return cast.stream()
                .filter(c -> c.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown cast member"));
    }

    public Film film(String id) {
        return films.stream()
                .filter(f -> f.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown film"));
    }

    private void requireTarget(String target) {
        if (!targets().containsKey(target))
            throw new IllegalArgumentException("Unknown catalog target: " + target);
    }

    public Map<String, String> targets() {
        var targets = new LinkedHashMap<String, String>();
        targets.put("all", "All Star Wars catalog records, broad or ambiguous questions");
        targets.put("none", "Outside the catalog, including Star Trek or USS Enterprise");
        ships.forEach(s -> targets.put("ship:" + s.id(), s.name()));
        films.forEach(f -> targets.put("film:" + f.id(), f.title()));
        cast.forEach(c -> targets.put("cast:" + c.id(), c.name()));
        // Every ship/film pair is addressable, even when crew evidence is missing.
        ships.forEach(
                s -> {
                    targets.put(
                            "crew:" + s.id() + ":all",
                            "Who flew/piloted "
                                    + s.name()
                                    + " across films (partial crew coverage)");
                    films.forEach(
                            f ->
                                    targets.put(
                                            "crew:" + s.id() + ":" + f.id(),
                                            "Who flew/piloted " + s.name() + " in " + f.title()));
                });
        return Collections.unmodifiableMap(targets);
    }

    public String manualTarget(String message) {
        String text = message.toLowerCase(Locale.ROOT);
        if (text.contains("enterprise") || text.contains("star trek")) return "none";
        var ship =
                ships.stream()
                        .filter(
                                s ->
                                        text.contains(s.name().toLowerCase(Locale.ROOT))
                                                || s.id().equals("5") && text.contains("falcon"))
                        .findFirst();
        var film =
                films.stream()
                        .filter(
                                f ->
                                        text.contains(f.title().toLowerCase(Locale.ROOT))
                                                || f.id().equals("1") && text.contains("new hope"))
                        .findFirst();
        if (ship.isPresent()
                && (text.contains("flew")
                        || text.contains("pilot")
                        || text.contains("crew")
                        || text.contains("flies")))
            return "crew:" + ship.get().id() + ":" + film.map(Film::id).orElse("all");
        if (ship.isPresent()) return "ship:" + ship.get().id();
        if (film.isPresent()) return "film:" + film.get().id();
        return cast.stream()
                .filter(c -> text.contains(c.name().toLowerCase(Locale.ROOT)))
                .map(c -> "cast:" + c.id())
                .findFirst()
                .orElse("all");
    }
}
