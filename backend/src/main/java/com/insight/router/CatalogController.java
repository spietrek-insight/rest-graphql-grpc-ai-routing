package com.insight.router;

import java.util.List;
import org.springframework.graphql.data.method.annotation.*;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/catalog")
class CatalogController {
    private final Catalog catalog;

    CatalogController(Catalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/starships/{target}")
    ResponseEntity<List<Catalog.Starship>> starships(@PathVariable String target) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofMinutes(5)))
                .body(catalog.starships(target));
    }

    @GetMapping("/films/{id}")
    Catalog.Film film(@PathVariable String id) {
        return catalog.film(id);
    }

    @GetMapping("/producers/{id}")
    Catalog.Producer producer(@PathVariable String id) {
        return catalog.producer(id);
    }

    @GetMapping("/films")
    List<Catalog.Film> films() {
        return catalog.films();
    }

    @GetMapping("/producers")
    List<Catalog.Producer> producers() {
        return catalog.producers();
    }
}

@Controller
class CatalogGraphqlController {
    private final Catalog catalog;

    CatalogGraphqlController(Catalog catalog) {
        this.catalog = catalog;
    }

    @QueryMapping
    List<Catalog.Starship> starships(@Argument String target) {
        return catalog.starships(target);
    }

    @QueryMapping
    List<Catalog.Film> films() {
        return catalog.films();
    }

    @QueryMapping
    List<Catalog.Producer> producers() {
        return catalog.producers();
    }
}
