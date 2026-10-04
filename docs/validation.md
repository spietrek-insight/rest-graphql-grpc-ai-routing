# Validation record — 2026-09-26

## Simulated flight streaming (2026-10-04)

- Backend: 40 tests passed, including per-event servlet flushing, real server-streaming transport, 21 ordered changing updates, deterministic replay, unsupported targets, historical crew isolation, pacing cancellation and retained bidirectional compatibility. Formatting and production build passed.
- Frontend: 42 tests passed, including progressive card updates, one assistant message per flight, retained Stop/error state, replay, ignored late updates and presentation view updates/Stop. Lint, formatting, coverage gates and production build passed.
- Rancher Desktop: rebuilt and deployed backend/frontend at http://localhost:3000. Eight presenter smoke cases passed; the Falcon delivered 21 snapshots through `catalog.StarshipTracker/Watch`.
- Real Ollama checks routed both current-location and current-pilot questions to `GRPC / ship:5`, returned the first simulated update, and were then cancelled. The historical A New Hope crew question still selected `GRAPHQL / crew:5:1` and returned Han Solo and Chewbacca.
- Browser: Auto/Ollama completed the 40-second flight (44.6 seconds including routing in one observed request), retained its final answer, and replayed from the opening scene. Presentation view fits the key readings at 1280×720 with no clipped text. It clearly labels fictional telemetry and keeps the documented film crew separate.
- Delivery timing through the deployed web proxy, one presenter-override run: first snapshot 374 ms, final snapshot 40,454 ms, 21 ordered updates, 18 distinct locations and two pilots. Updates arrived separately at roughly two-second intervals. The check exposed and fixed Spring 7 body-flush suppression; a regression test now asserts the response is committed before the final result.
- The v4 deck now describes server streaming, the simulated flight and presentation controls on slides 13 and 16, with updated speaker notes. All 27 slides were rendered; only those two slides changed visually.
- Older performance records below describe the previous three-static-snapshot demo. They are historical results, not current flight-duration benchmarks. `scripts/benchmark.mjs` now records time to the first snapshot separately from full playback time.


## Character cast and flight crew — October 4, 2026

- Replaced producers with 18 fictional characters across six films; no actor names are in the seed or API responses.
- Backend: 35 tests passed, including real REST/GraphQL/gRPC crew responses, film isolation, missing evidence, immutable references, and bounded model targets. Formatting passed.
- Frontend: 34 tests passed; lint, formatting and production build passed. Root launcher tests: 5 passed.
- Built and deployed frontend/backend images to Rancher Desktop; backend health passed.
- Deployed smoke checks: 8 presenter cases and 9 live Ollama cases passed. They cover film cast, Luke Skywalker lookup/appearances, Falcon crews in A New Hope and Return of the Jedi, missing X-wing crew coverage, three gRPC snapshots, and the contextual follow-up “Who flew it in A New Hope?”
- Browser: the GraphQL suggestion returned Han Solo (pilot) and Chewbacca (co-pilot), with official source URLs, using Auto/Ollama. The observed request took 5,386 ms; this is a single request observation, not a benchmark. The field guide displayed 18 characters, with no browser errors or warnings.
- The local model requires initial preparation after the routing schema changes; startup warmup primes it. Existing benchmark JSON files describe the previous dataset and were not rewritten.
- Verified crew coverage is deliberately limited to A New Hope and the Battle of Endor. Missing assignments produce an explicit coverage-gap answer.

## Projection readability — 2026-10-04

- Consolidated text into dark primary, secondary, and muted colors, retaining pale surfaces and distinct blue, purple/pink, and amber protocol identities. Increased small captions to at least 11 px, chat/composer text to 14 px, and inspector payloads to 13 px. Strengthened keyboard focus and selected-state indicators.
- Targeted the [WCAG enhanced text contrast](https://www.w3.org/WAI/WCAG22/Understanding/contrast-enhanced.html) threshold of 7:1 for enabled text. Browser measurements use computed foreground/background colors, composited translucent surfaces, and conservative gradient stops; they are not a full accessibility certification or a physical projector measurement.
- Representative ratios before → after: chat text 4.92 → 10.38; request timing 2.22 → 8.70; connection status 3.01 → 8.70; GraphQL badge 3.23 → 7.37.
- Browser checks covered the welcome page, a live GraphQL response and inspector, the protocol menu, response dialog, and AI preferences. No measured enabled text fell below 7:1; the lowest measured ratio was 7.37:1. Disabled controls retain their subdued appearance.
- Verified layouts at 1280×720 and 390×844 with no horizontal page overflow. The composer stays inside the viewport; the welcome area and sidebar scroll when needed. Short desktop displays omit the decorative welcome emblem/footer to leave room for prompts and input.
- Production Docker build passed lint, formatting, all 34 tests, coverage gates, and compilation. Keep room lighting, projector calibration, display size, and audience distance in mind when doing the final venue check; browser contrast alone cannot establish physical projection legibility.

## Frontend dependency upgrade — 2026-10-04

Reviewed the frontend's single package manifest and lockfile against npm's stable releases. Updated every direct dependency declaration to the latest compatible release; packages already current remain unchanged. The lockfile records the complete resolved tree.

| Package group | Verified versions |
| --- | --- |
| React / React DOM / corresponding types | 19.3.0 |
| assistant-ui | 0.15.23 |
| Radix Dialog / Select / Slot | 1.1.23 / 2.3.7 / 1.3.3 |
| Lucide | 1.52.0 |
| Tailwind / Vite integration | 4.3.3 |
| Tailwind Merge / Zustand | 3.7.0 / 5.0.15 |
| Vite / React plugin | 8.3.2 / 6.1.1 |
| Vitest / V8 coverage | 5.0.3 |
| ESLint / typescript-eslint | 10.12.0 / 8.71.0 |
| React Hooks / React Refresh lint plugins | 7.1.1 / 0.5.7 |
| jsdom / jest-dom | 30.1.2 / 7.0.1 |
| TypeScript / Node types | 6.0.3 / 22.20.5 |

Compatibility decisions:

- TypeScript is constrained to `~6.0.3`: the current typescript-eslint package declares support for TypeScript `>=4.8.4 <6.1.0`, so TypeScript 7.0.2 would produce an unsupported dependency tree. Node types use the newest Node 22 release to match the Docker and CI runtime, rather than exposing Node 26 APIs. These are the only two packages reported by the final `npm outdated` check, and both are at their newest allowed versions.
- Replaced `legacy-peer-deps=true` with explicit peer and engine validation. The frontend now requires Node `^22.22.2 || ^24.15.0 || >=26.0.0`, matching jsdom's runtime requirement.
- Migrated the assistant bundle from the removed Rollup object-form `manualChunks` to Rolldown `codeSplitting`, following the [Vite 8 migration guide](https://vite.dev/guide/migration.html). Removed `baseUrl` while preserving the relative alias mapping, following the [TypeScript 6 migration notes](https://www.typescriptlang.org/docs/handbook/release-notes/typescript-6-0.html). Reviewed the [Vitest 5](https://vitest.dev/guide/migration/) and [ESLint 10](https://eslint.org/docs/latest/use/migrate-to-10.0.0) migration guides; existing tests and lint configuration pass without weakening rules.
- Docker builds now enforce the same formatting check as CI, in addition to lint, coverage tests, and production compilation.

Verification:

- Baseline and upgraded frontend: all 34 tests across 8 files passed. Coverage remains 97.82% lines, 96.42% branches, 97.11% statements, and 95% functions; all existing 80% gates remain enabled.
- Clean `npm ci` and `npm ls --all` passed with strict peer checks. The first native clean-install attempt encountered npm's `ENOTEMPTY` removal error; a retry completed successfully. No peer bypasses or forced resolutions were used.
- Full npm audit, including development dependencies: zero reported vulnerabilities at verification time.
- A fresh Linux ARM64 frontend Docker build passed installation, lint, formatting, tests, coverage, and compilation. Deployed using the `rancher-desktop` Docker context; frontend HTTP 200 and backend health `UP` verified.
- All three presenter override routes and all three automatic Ollama routes passed through `http://localhost:3000`; each gRPC check delivered three snapshots.
- Browser verified the production bundle loads, the protocol selector opens and selects GraphQL, a live GraphQL answer renders, the override resets to Auto, and the full response dialog opens and closes. No browser console warnings or errors were reported during these checks.

## Earlier verification

- Frontend: 29 Vitest tests passed. Line coverage 97.79%; branch coverage 95.89%; function coverage 94.93%. Source thresholds are enforced at 80%.
- Backend: 25 JUnit tests passed, including real REST, GraphQL and bidirectional gRPC integration tests, bounded classification context and startup warmup. JaCoCo line coverage 96.26%; branch coverage 96.36%. Source thresholds are enforced at 80%.
- Response dialog: browser verified a full GraphQL payload in the scrollable modal. Unit tests cover both entry points, exact response content, copying, clipboard failure, Escape dismissal and focus restoration.
- TypeScript production build, ESLint, Prettier and Spotless checks passed.
- npm production dependency audit: zero reported vulnerabilities at verification time.
- Browser checks: REST resource lookup, GraphQL film relationships, three gRPC snapshots with decoded payloads, desktop three-column layout at 1440 px, and mobile layout/navigation at 390 px.
- Native application smoke test: all three presenter override routes passed. `node scripts/smoke.mjs` reproduces this check against a running backend.
- Live local AI check: Ollama 0.34.4 with `qwen3:4b-instruct-2507-q4_K_M` selected REST/ship:5, GRAPHQL/film:1 and GRPC/ship:5 correctly in all nine benchmark requests. Every gRPC case delivered three snapshots. Run `node scripts/smoke.mjs --ai` for a single pass or `node scripts/benchmark.mjs` for timed repetitions. This is a guided-prompt check, not a general accuracy benchmark.
- Warm complete-response medians: REST 1,105 ms, GraphQL 1,257 ms, gRPC 1,858 ms; maximum across all nine samples 1,938 ms. Three additional contextual relationship questions all correctly selected GRAPHQL/ship:5, taking 1,023–1,524 ms. Native Ollama on this Apple Silicon Mac, sequential requests, no presenter overrides or application response caching. Raw results: [performance.json](performance.json). First model loading was separately observed to take about 19 seconds; startup warmup moves that work ahead of normal interaction when the model is already downloaded.
- Rancher Desktop deployment verified on 2026-09-26 using dockerd (moby), Linux ARM64, 2 vCPUs and about 6 GB VM RAM. Both Docker image builds passed their checks (22 frontend tests and 21 backend tests); backend health passed. All three presenter routes and all three automatic routes plus a contextual follow-up passed through the frontend at port 3000. The model download job exited successfully and model data persists in the `dev2next_model-data` volume.
- Container CPU timings, one sample per scenario: REST 1.46 s, GraphQL 2.82 s, gRPC including three snapshots 3.64 s, contextual follow-up 5.10 s. Initial model/prompt warmup took 34.24 s. These are smoke measurements, not a statistical latency benchmark. See [rancher-performance.json](rancher-performance.json).
- Jev request/response adapter tests use the documented API contract and mocked responses; no live TypeSafe request was made because no API key was supplied.
- The repository has been initialized locally and has not been published to GitHub.

The current deployment runs at http://localhost:3000 in Rancher Desktop, independently of the temporary development sessions. Keep Rancher Desktop running. The old port 5173 preview processes have stopped. Temporary native verification tools remain under `/tmp/protocol-build`; the deployed containers and model volume are managed by Rancher Desktop. See [the run guide](rancher-desktop.md) for repeatable startup.

Provider preferences: tests cover Ollama → Jev → Ollama with the same classifier instance, independent model selection, missing-key rejection without fallback, invalid provider rejection, safe status metadata, request serialization, browser preference persistence, New conversation behavior, and disabled choices during requests. The live deployment has no Jev API key, so hosted Jev inference remains tested with mocked responses, not a live TypeSafe call.

Styled controls: the protocol dropdown uses Radix Select with a portal menu instead of a visible native select. Tests verify mouse selection, keyboard selection, Escape dismissal, focus restoration, no unintended form submission, disabled state during requests, and automatic reset of the next-question override. Existing Radix Dialog tests continue to pass.

CLM integration (2026-09-26): all 34 frontend tests and 31 backend tests pass, including coverage gates, lint/format checks, and production builds. CLM tests cover typed Choice requests, bounded history, distributions, independent models and optional keys, missing configuration, invalid decisions, unavailable upstreams without fallback, persisted preferences, server-default selection, and HTTP acceptance with presenter overrides. Compose configuration validates. A browser check confirmed all three options and the disabled, self-hosted CLM state when no URL is configured; a live backend request returned the actionable CLM_URL configuration error. CLM inference itself has not been run: model accuracy, Apple Silicon compatibility, and latency remain unverified until a real encoder and CLM server are connected.
