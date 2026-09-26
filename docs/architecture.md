# Architecture decisions

## One process, genuine protocol calls

Spring Boot provides REST and GraphQL on port 8080; a managed grpc-java server provides bidirectional streaming on 9090. One immutable domain catalog serves all surfaces. The orchestrator uses actual HTTP clients and a native gRPC channel, not direct calls into controllers or synthesized traffic. Browser transport is POST `/api/chat` with incremental NDJSON events: `decision`, zero or more `snapshot`, then `result` or `error`.

## Classifier versus answer generation

Ollama receives three distinct tool schemas and chooses a protocol plus an allowlisted target. Jev receives two independent Choice questions against current message/history. Jev is an intentional alternative to the PRD's LLM tool-call mechanism: its structured classification contract is better suited to this small closed decision space. The system never substitutes keyword routing while labeling it AI. In explicit presenter override mode only, deterministic name matching supplies target arguments without a model.

Answer composition is deterministic over the actual transport response. This keeps offline demos grounded, minimizes latency, and supports Jev without a second text-generation model. It is a bounded catalog assistant, not a general chat assistant. Models choose routes but are not trusted to supply arbitrary URLs, query text, service names or free-form executable arguments.

## Observability

The inspector captures the HTTP request document and received JSON body, or the actual request and response Protobuf messages decoded through protobuf JsonFormat. It is an application payload trace, not a packet capture. Provider secrets and authorization headers are never sent to the browser. Classification output includes Jev confidence without inventing Ollama confidence. Measured sizes exclude transport framing. Routing, protocol and total server durations are reported separately; these are request diagnostics, not a comparative protocol benchmark.

## Streaming

The native gRPC contract is bidirectional. The client sends three paced snapshot requests while the response stream is open; the service replies to each. The HTTP bridge flushes each snapshot immediately. The UI shows progress as events arrive and retains the completed exchange. A 10-second gRPC deadline bounds transport work; HTTP/model calls have a 120-second timeout. Stop cancels the browser fetch and suppresses late session results; an upstream classifier may finish within its timeout before noticing disconnect because Java's synchronous upstream call is not tied to the browser AbortSignal.

## Data continuity

The actual original repository supersedes the illustrative two-ship PRD dataset. See dataset-provenance.md. Mutations and compatibility with the previous API's exact paths are intentionally outside the new conversational read-only scope. Scalar fields, identities and many-to-many relationships are retained. The original application used database-backed entities; immutable startup data removes local database configuration for a fixed conference demo.

## Frontend ownership

Only Page modules import API clients or application Zustand state. Presentational components accept props and emit callbacks. The chat library's own runtime context manages presentation/scroll behavior, not application network or Zustand access. AbortController plus a session identity prevents stale requests from modifying a reset conversation. Conversation history remains in memory. A separate `usePreferencesStore` persists only the selected provider in localStorage; it validates restored values and stores no credentials or chat data.

## Provider preference

An optional allowlisted `provider` on each chat request selects Ollama or Jev. The default is the server's `AI_PROVIDER` when omitted. There is no mutable server-wide active provider, so different browsers cannot change one another's routing. Each provider resolves its own model configuration. `/api/status` exposes safe model/configuration metadata, including whether a Jev key has been supplied, but never the key. Missing Jev configuration produces a specific safe error without fallback. Presenter overrides bypass both classifiers. The browser prevents changing preference during a request, preserves it across new conversations, and shows the selected provider's model in the sidebar.

## Latency controls

Local routing uses the explicitly pinned non-thinking Qwen3 4B Instruct model, a 96-token output budget, and 30-minute keep-alive. The last four history messages are truncated to 256 characters each for local classification; visible conversation history is unchanged. Jev retains its original bounded request history. This trades long-range model recall for a small routing context; the newest question is preserved in full. Every automatic request still runs actual model inference and every selected protocol still executes real transport traffic.

Prior turns are serialized as context data rather than supplied as assistant examples. This prevents earlier prose answers from teaching the classifier to generate prose instead of a tool call. Startup asynchronously primes the model with a classification request; it does not invoke a protocol or block HTTP startup. If the model is unavailable, the next chat request can retry loading it.

Insight includes `timings.routingMs` and `timings.totalMs` in addition to the existing protocol `exchange.durationMs`. Total server time is measured before writing the final result; it excludes network delivery and browser rendering.
