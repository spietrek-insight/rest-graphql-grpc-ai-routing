# Dataset provenance

Source: https://github.com/chetan-galgali/devnexus-api-comparison
Commit: `7d13e347802afd647df80b770b3bb55573979b51`
Source file: `client/src/main/resources/data.sql` (verbatim copy: `original-data.sql`).

The original application is authoritative over the PRD's illustrative Star Trek examples. It contains 19 starships, six films, four producers, 30 starship/film links, and nine distinct film/producer links. It does not contain USS Enterprise or Star Trek. Guided streaming therefore uses the Millennium Falcon.

`backend/src/main/resources/catalog.json` preserves original IDs (one-based insertion order), names, maxSpeed, length, and relationships. The duplicate `(3,1)` film/producer insert is collapsed to one relationship, matching the original Java Set model. Howard G. Kazanjian and George Lucas exist as producer records but have no film links in the original SQL. Historical credits are intentionally not corrected. Units for speed and length are not specified by the source, so the application does not invent units. Zero speed values remain zero.

No invented ship descriptions, telemetry, Star Trek records, or producer links are added. The immutable catalog is loaded once at startup. This demo does not need an external database or migrations.
