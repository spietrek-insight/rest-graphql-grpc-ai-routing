# Architecture decisions

## One process, genuine protocol calls

Spring Boot provides REST and GraphQL on port 8080; a managed grpc-java server provides server streaming (`Watch`) and the legacy bidirectional RPC (`Track`) on 9090. One immutable domain catalog serves all surfaces. The orchestrator uses actual HTTP clients and a native gRPC channel, not direct calls into controllers or synthesized traffic. Browser transport is POST `/api/chat` with incremental NDJSON events: `decision`, zero or more `snapshot`, then `result` or `error`.

## Classifier versus answer generation

Ollama receives three distinct tool schemas and chooses a protocol plus an allowlisted target. Jev and CLM receive two independent Choice questions against current message/history. Jev is an intentional alternative to the PRD's LLM tool-call mechanism: its structured classification contract is better suited to this small closed decision space. The system never substitutes keyword routing while labeling it AI. In explicit presenter override mode only, deterministic name matching supplies target arguments without a model.

Answer composition is deterministic over the actual transport response. This keeps offline demos grounded, minimizes latency, and supports Jev without a second text-generation model. It is a bounded catalog assistant, not a general chat assistant. Models choose routes but are not trusted to supply arbitrary URLs, query text, service names or free-form executable arguments.

## Observability

The inspector captures the HTTP request document and received JSON body, or the actual request and response Protobuf messages decoded through protobuf JsonFormat. It is an application payload trace, not a packet capture. Provider secrets and authorization headers are never sent to the browser. Classification output includes Jev/CLM confidence without inventing Ollama confidence. Measured sizes exclude transport framing. Routing, protocol and total server durations are reported separately; these are request diagnostics, not a comparative protocol benchmark.

## Streaming

The chat gateway sends one `WatchRequest` using the server-streaming `Watch` RPC. For the Falcon, `FlightSimulation` reads a separate immutable, fictional scenario and `TrackerService` emits 21 paced updates (40 seconds by default). Each call owns its own virtual worker thread; no global mutable timeline is shared. Updates include simulation provenance, location, phase, pilot/co-pilot, progress, speed, shields and a narrative event. Historical catalog relationships are unchanged. Non-Falcon targets receive one catalog-only update; unsupported targets fail validation. `Track` retains its original bidirectional request/snapshot behavior for compatibility.

The gateway forwards each Protobuf response to the browser as NDJSON and captures the exchange. The controller calls `HttpServletResponse.flushBuffer()` after each event: Spring 7.0.6+ suppresses `getBody().flush()` by default, which otherwise batches snapshots in the servlet buffer ([framework source](https://github.com/spring-projects/spring-framework/blob/main/spring-web/src/main/java/org/springframework/http/server/ServletServerHttpResponse.java)). A single chat card updates progressively and retains its last state on completion, Stop or failure. Fetch cancellation is noticed on a subsequent HTTP write; the gateway then cancels its gRPC context and channel, which interrupts the simulation worker. Server-side flow control waits for a ready consumer. A 115-second gRPC deadline bounds the allowed 20–5000 ms pacing (up to 100 seconds). HTTP/model calls have a 120-second timeout; an upstream classifier may finish before noticing browser cancellation. There is no automatic resume, cross-session flight, or additional model inference during the stream.

Contract and cancellation design follow the [gRPC Java generated-code reference](https://grpc.io/docs/languages/java/generated-code/) and [gRPC cancellation guide](https://grpc.io/docs/guides/cancellation/).

## Data continuity

The actual original repository supersedes the illustrative two-ship PRD dataset. See dataset-provenance.md. Mutations and compatibility with the previous API's exact paths are intentionally outside the new conversational read-only scope. Ship scalar fields, identities and ship/film relationships are retained. The old producer layer is replaced with character cast and explicit ship/film flight-crew assignments. The seed references IDs rather than duplicating film and character records. Missing flight-crew evidence is reported as a coverage gap, never inferred from a film appearance. The original application used database-backed entities; immutable startup data removes local database configuration for a fixed conference demo.

## Frontend ownership

Only Page modules import API clients or application Zustand state. Presentational components accept props and emit callbacks. The chat library's own runtime context manages presentation/scroll behavior, not application network or Zustand access. AbortController plus a session identity prevents stale requests from modifying a reset conversation. Conversation history remains in memory. A separate `usePreferencesStore` persists only the selected provider in localStorage; it validates restored values and stores no credentials or chat data.

## Provider preference

An optional allowlisted `provider` on each chat request selects Ollama, Jev, or CLM. The default is the server's `AI_PROVIDER` when omitted. There is no mutable server-wide active provider, so different browsers cannot change one another's routing. Each provider resolves its own model configuration. `/api/status` exposes safe model/configuration metadata, including whether a Jev key or CLM URL has been supplied, but never the key. Missing Jev configuration produces a specific safe error without fallback. Presenter overrides bypass all classifiers. The browser prevents changing preference during a request, preserves it across new conversations, and shows the selected provider's model in the sidebar.

## Latency controls

Local routing uses the explicitly pinned non-thinking Qwen3 4B Instruct model, a 96-token output budget, and 30-minute keep-alive. The last four history messages are truncated to 256 characters each for local classification; visible conversation history is unchanged. Jev retains its original bounded request history. This trades long-range model recall for a small routing context; the newest question is preserved in full. Every automatic request still runs actual model inference and every selected protocol still executes real transport traffic.

Prior turns are serialized as context data rather than supplied as assistant examples. This prevents earlier prose answers from teaching the classifier to generate prose instead of a tool call. Startup asynchronously primes the model with a classification request; it does not invoke a protocol or block HTTP startup. If the model is unavailable, the next chat request can retry loading it.

Insight includes `timings.routingMs` and `timings.totalMs` in addition to the existing protocol `exchange.durationMs`. Total server time is measured before writing the final result; it excludes network delivery and browser rendering.

## CLM provider

CLM reuses the typed decision adapter with its own base URL, model, and optional bearer key. The provider is unavailable until `CLM_URL` is set; configuration availability does not imply server health. The server URL and credentials are never sent to the browser. UI copy identifies CLM as self-hosted rather than assuming its server is local; the legacy `local` metadata flag remains exclusive to Ollama. Both the request validator and persisted preference allowlist accept `clm`.

The CLM state is prose: at most four history turns, each capped at 256 characters, followed by the full latest question. This bounds typical encoder input and keeps the newest question at the end. The upstream default limit is 2048 tokens; character bounds are not a strict token guarantee for every language/input. Configure higher encoder and CLM token limits together when needed. Choice descriptions use the shared catalog and protocol criteria, with `none` available for unknown entities. Existing target and protocol validation rejects invalid decisions. CLM distributions are relative to the supplied candidates, not guarantees of correctness.

The external CLM service and Qwen3-8B encoder have their own lifecycle. No model download or external CLM health dependency is added to application startup. A configured but unreachable service fails the request without switching providers.
