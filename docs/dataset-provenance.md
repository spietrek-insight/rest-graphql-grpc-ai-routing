# Demo dataset provenance

The canonical seed is `backend/src/main/resources/catalog.json`. It is loaded once at startup; no database or migration is required. Rebuild/restart the backend after editing it.

## Original ship data

The [original Devnexus demo](https://github.com/chetan-galgali/devnexus-api-comparison), archived in `docs/original-data.sql`, supplies 19 starships, six films and 30 ship/film links. Their IDs, names, numeric values and links are preserved. Units are unspecified; zero speeds remain zero. Ship/film links are incomplete historical demo data, not a complete canon inventory (for example, the source omits the X-wing's A New Hope link).

## Character cast enrichment

Producers have been replaced by a curated selection of **18 characters** and their film appearances. Cast means Luke Skywalker, Han Solo and Chewbacca, not the actors portraying them. It is a selection, not a complete credit list. Character IDs are new and do not reuse the meaning of producer IDs.

Character appearances are curated from the official film/databank pages:

- [A New Hope](https://www.starwars.com/films/star-wars-episode-iv-a-new-hope)
- [The Empire Strikes Back](https://www.starwars.com/films/star-wars-episode-v-the-empire-strikes-back)
- [Return of the Jedi](https://www.starwars.com/films/star-wars-episode-vi-return-of-the-jedi)
- [The Phantom Menace](https://www.starwars.com/films/star-wars-episode-i-the-phantom-menace)
- [Attack of the Clones](https://www.starwars.com/films/star-wars-episode-ii-attack-of-the-clones)
- [Revenge of the Sith](https://www.starwars.com/films/star-wars-episode-iii-revenge-of-the-sith)

The seed stores each ship, film and character once. Ships reference film IDs; films reference cast IDs. REST, GraphQL and gRPC resolve the same immutable records. A character's film appearance does **not** imply that they crewed or piloted a ship in that film.

## Verified flight crew

Four explicit ship/film/character assignments are included:

| Ship | Film / scope | Character | Role | Evidence |
| --- | --- | --- | --- | --- |
| Millennium Falcon | A New Hope | Han Solo | Pilot | [Han Solo](https://www.starwars.com/databank/han-solo) |
| Millennium Falcon | A New Hope | Chewbacca | Co-pilot | [Chewbacca](https://www.starwars.com/databank/chewbacca) |
| Millennium Falcon | Return of the Jedi, Battle of Endor | Lando Calrissian | Pilot | [Millennium Falcon](https://www.starwars.com/databank/millennium-falcon) |
| Millennium Falcon | Return of the Jedi, Battle of Endor | Nien Nunb | Co-pilot | [Nien Nunb](https://www.starwars.com/databank/nien-nunb) |

Crew answers include the source URL. These assignments are intentionally partial: other ships, scenes and films return a clear missing-coverage answer instead of inferring pilots from cast membership. A crew target retains both ship and film, preventing Endor's crew from leaking into A New Hope answers.

To extend the seed, add a character under `cast`, link its ID under the film's `castIds`, then add a `crew` entry with ship ID, film ID, cast ID, role and an evidence URL. Startup rejects dangling references. Update the bounded target validation, tests and field-guide count if the number of records changes.

## API change

REST uses `/api/catalog/cast` and `/api/catalog/cast/{id}`. GraphQL exposes `cast`, film `cast`, and ship `crew`. Routing uses `cast:ID` and `crew:SHIP_ID:FILM_ID` (or `all` for the film). Old producer endpoints/fields are removed. Protobuf reserves the removed producer field number 5 and name; cast and crew use new fields 7 and 8. Regenerate clients from `catalog.proto` when upgrading.

`docs/original-data.sql` and previously recorded benchmark JSON files remain historical evidence of the old dataset; they do not define the current catalog.
