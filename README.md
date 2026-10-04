# Protocol Studio

A conversational API comparison lab: ask a question, let a model choose REST, GraphQL or gRPC, and inspect the actual request and response. Built for the Devnexus session with React, TypeScript and Java 21.

## Run the entire application

Prerequisite: Docker Desktop, Rancher Desktop with the Docker engine, or Docker Engine with Compose. Allow roughly 8 GB of available memory for a comfortable local-model demo; model performance depends on your hardware.

For Rancher Desktop, see the [start, stop, troubleshooting and performance guide](docs/rancher-desktop.md). The deployment uses Docker Compose with dockerd (moby); Kubernetes is not required.

```sh
# After cloning this repository:
docker compose up --build -d
```

Open **http://localhost:3000**. No API keys, database setup, or `.env` file are needed for the defaults. First startup requires internet to download dependencies, container images and the local model (several GB). Subsequent runs can work offline once those assets are cached. Follow model setup with `docker compose logs -f model-init`; automatic routing is available when the pull completes. Presenter overrides work before the model is ready.

```sh
docker compose down          # stop; preserve downloaded model
docker compose logs -f       # inspect startup or errors
```

On Apple Silicon, native Ollama is usually more responsive than CPU inference inside a Linux VM. Install Ollama, run `ollama pull qwen3:4b-instruct-2507-q4_K_M`, and set `OLLAMA_URL=http://host.docker.internal:11434` in `.env` before starting Compose. The default container path is portable and requires no host model installation.

## Develop without containers

Prerequisites: Java 21, Node.js 22.22.2+ (within Node 22) and Ollama. Node 24.15+ and Node 26+ are also supported by the frontend tooling. Maven is downloaded automatically by the committed wrapper.

```sh
ollama pull qwen3:4b-instruct-2507-q4_K_M
# Terminal 1
cd backend
./mvnw spring-boot:run
# Terminal 2, from repository root
cd frontend
npm ci
npm run dev
```

Open **http://localhost:5173**. Vite proxies `/api` to the backend. The backend listens on 8080, GraphQL is at `/graphql`, and native gRPC listens on 9090. Ollama listens on 11434. Without Ollama, use the protocol selector to force the next request.

## Optional Jev classification

Jev is a structured decision model, not a text generator. This implementation sends two Choice questions (protocol and catalog target) to the documented TypeSafe HTTP API, preserves its confidence/probabilities in the inspector, and composes answers from returned catalog data.

```dotenv
JEV_MODEL=jev-latest
TYPESAFE_API_KEY=your-key
```

Put these in a root `.env` file for Compose, then run `docker compose up -d --force-recreate backend frontend` once to apply the credential. No image rebuild is needed for environment changes. For native development, export the variables in the backend shell and restart it; Spring does not automatically load the root `.env`. The key remains server-side. Jev uses a hosted endpoint and requires internet; the reviewed public docs do not document downloadable weights or a local runtime. It is optional, and the Ollama default has no cloud dependency. Documentation: https://docs.typesafe.ai/api and https://docs.ollama.com/capabilities/tool-calling.

Open **AI preferences** in the top bar to switch between Ollama, Jev, and CLM. Switching is immediate for future questions and does not restart or redeploy anything. The browser remembers the selected provider using a dedicated Zustand preferences store; New conversation does not reset it. Only the provider choice is persisted, never conversation history or API keys. Switching is disabled while a response is running. Jev is disabled until a server-side key is configured; this checks configuration, not credential validity or provider reachability. Selecting Jev sends the question and recent conversation to TypeSafe. Existing responses retain their recorded provider, and failures never silently fall back to another provider.

`AI_PROVIDER` sets the initial choice for a browser without a saved preference. `OLLAMA_MODEL`, `JEV_MODEL`, and `CLM_MODEL` configure independent models; defaults are `qwen3:4b-instruct-2507-q4_K_M`, `jev-latest`, and `clm-latest`. Legacy `AI_MODEL` applies only to the server's default provider, unless that provider's dedicated model setting is supplied. API clients may include `provider: "ollama"`, `provider: "jev"`, or `provider: "clm"` in `/api/chat`; omission preserves the server default. Presenter protocol overrides bypass all AI providers.

## Optional self-hosted CLM classification

CLM uses the same two Choice questions as Jev to select a protocol and catalog target. Its confidence and probability distributions appear in the inspector; answers still come from actual catalog responses. It does not generate prose. The frontend labels CLM **SELF-HOSTED** because its configured server may be local or remote.

Run a CLM server and its required Qwen3-8B encoder separately, then configure the backend:

```dotenv
CLM_URL=http://host.docker.internal:8700
CLM_MODEL=clm-latest
# CLM_API_KEY=your-server-key
```

The URL above reaches a host service from Docker Desktop or Rancher Desktop. For a native backend and CLM on the same machine, use `http://127.0.0.1:8700`. Supply the base URL without `/v1/systemone`. The optional key is required only if your CLM server enforces authentication; it stays on the backend and is independent of the TypeSafe key. Use an appropriately secured connection for a remote server.

After changing `.env`, run `docker compose up -d --force-recreate backend frontend` to apply configuration (use `--build` when also updating application code). For native development, export these variables before starting the backend. Then select **CLM** in **AI preferences**. Set `AI_PROVIDER=clm` only if you want it to be the initial choice for browsers without a saved preference.

CLM is disabled until `CLM_URL` is configured. This is a configuration check, not a health check. Questions and bounded recent conversation are sent to that server; unreachable servers or invalid decisions produce errors without fallback. No server URL or key is exposed through `/api/status`. Its legacy `local` flag remains true only for Ollama; CLM's location is not inferred from its URL.

The upstream reference deployment is:

```sh
pip install contrastive-lm
# Run the encoder and API as separate services/terminals:
vllm serve Qwen/Qwen3-8B --served-model-name qwen3-8b --runner pooling --max-model-len 2048 --port 8090
clm-serve
```

This requires a compatible inference environment and model downloads. The released CLM heads require **Qwen3-8B last-token-pooled embeddings**; the existing Ollama Qwen3-4B model is not interchangeable. Compose does not install or start the CLM encoder/API. Apple Silicon inference compatibility and performance have not been validated for this project. See the [CLM deployment instructions](https://github.com/Contrastive-LM/CLM#quickstart) and [model requirements](https://huggingface.co/Contrastive-LM/CLM-v0.1-8B#limitations).

Once a real server is running, exercise it without changing the backend default:

```sh
AI_PROVIDER=clm node scripts/smoke.mjs --ai
AI_PROVIDER=clm node scripts/benchmark.mjs
```

These verify live routes and record warmup, response timing, and contextual follow-ups. Mocked adapter tests do not establish model accuracy or latency.

## Try it

- **REST:** “Tell me about the Millennium Falcon.”
- **GraphQL:** “Which starships appear in A New Hope, and who produced it?”
- **gRPC:** “Track the Millennium Falcon.”

Auto uses genuine model tool calling with Ollama or typed decisions with Jev or CLM. There is no hidden keyword-based automatic fallback. If a provider is unavailable or returns invalid output, the UI displays an error; choose a presenter override to continue. Overrides bypass the model and use a deliberately simple name matcher for arguments. They apply to one question and then reset to Auto.

The tracker sends three requests and receives three catalog snapshots over a real bidirectional HTTP/2 Protobuf stream. Snapshots are bridged to the browser incrementally over newline-delimited JSON. Timestamps are real; vessel telemetry is not fabricated. The inspector displays decoded Protobuf JSON, and its response size counts serialized Protobuf message bytes (not framing or headers). HTTP sizes count response body bytes. The inspector separately shows routing time, protocol execution time and total server time. Protocol timing excludes model classification, and includes deliberate 350 ms pacing between stream requests; it is **not a protocol benchmark**.

## Architecture and boundaries

- `frontend/src/pages/ChatPage.tsx` alone accesses application API clients and Zustand. All other UI components take props/callbacks. ESLint enforces the boundary. Zustand has separate conversation, protocol and session slices, plus a dedicated persisted provider-preferences store. Conversation history is not persisted.
- Presentational chat scrolling/runtime uses **assistant-ui**. Tailwind CSS v4 and shadcn-style components in `components/ui` supply Button (Radix Slot + CVA), Select (`@radix-ui/react-select`) and Dialog (`@radix-ui/react-dialog`). The visible protocol menu is a styled Radix popover with keyboard navigation and rich option descriptions; dialogs use portals, focus trapping, Escape dismissal and focus restoration. The components share the application theme and respect reduced-motion preferences, with `components.json` available for additional shadcn components.
- Java uses Spring Boot, constructor injection, immutable domain records, a single seeded domain service, input validation, bounded upstream timeouts, generated Protobuf bindings, and a replaceable classifier interface.
- The Java orchestrator calls its own real REST and GraphQL HTTP endpoints or native gRPC listener. It captures payloads at invocation time. Answers use returned data, never a separate catalog lookup or invented LLM facts.
- Only the in-memory UI carries conversation history; up to 20 recent turns are accepted; local classification uses the latest four messages, capped at 256 characters each, to resolve recent follow-up references without repeatedly processing long answers. Refresh or New conversation resets the session.

See [architecture decisions](docs/architecture.md), [dataset provenance](docs/dataset-provenance.md), and the [verification record](docs/validation.md).

## Dataset

The original [Devnexus repository](https://github.com/chetan-galgali/devnexus-api-comparison) supplies **19 ships, 6 films and 4 producers**, with their original IDs, numeric fields and relationships. It is authoritative over the PRD’s illustrative Enterprise/Star Trek records, which are absent from the original. `backend/src/main/resources/catalog.json` is the single canonical seed; `docs/original-data.sql` records its source. Original credits are preserved, not fact-corrected. No external database is required.

## Tests and code quality

```sh
make test
# Or individually:
cd frontend && npm ci && npm run lint && npm run format:check && npm test && npm run build
cd backend && ./mvnw verify
```

Vitest + Testing Library cover UI interactions, page/store boundaries, cancel/reset handling, streaming parsing, and errors. JUnit/Mockito cover classification, domain relationships and failure paths. Integration tests start real HTTP, GraphQL and gRPC servers and exercise all three paths without external AI services. Both suites enforce at least **80% line and branch coverage**; the frontend also enforces 80% statements/functions. Generated Protobuf classes and the Java boot entry point are excluded; frontend types, test fixtures and its render-only entry point are excluded. Coverage reports: `frontend/coverage/index.html` and `backend/target/site/jacoco/index.html`.

With the backend running, `node scripts/smoke.mjs` checks all three real transports; `node scripts/smoke.mjs --ai` additionally checks the three guided prompts against your live model. Model outcomes may vary.

CI runs lint, formatting, builds, tests and coverage gates and uploads reports. Docker builds also run tests. Run `npm run format` in the frontend or `./mvnw spotless:apply` in the backend for formatting.

## Scope

A single-user local presentation tool, not a public hosted product. No accounts, persistent chat history, arbitrary tool execution or external dataset search. Compose publishes application ports on loopback. Do not expose it publicly without authentication and operational hardening. Publishing this workspace to a GitHub remote is a separate action; no remote is assumed in these instructions.

## Local response time

The default pins `qwen3:4b-instruct-2507-q4_K_M`, the non-thinking instruction model. Avoid the unqualified `qwen3:4b` tag for this routing task: it currently resolves to the thinking model and generates unnecessary reasoning before a tool call, even with `think: false`. The backend asynchronously primes local model weights and its tool prompt at startup (disable with `AI_WARMUP=false`); if the model is not downloaded yet, the next automatic request will load it. The router caps output at 96 tokens and asks Ollama to retain the model for 30 minutes. There is no canned-answer or keyword-classification shortcut.

A warm native model on Apple Silicon is the target for interactive latency. Initial loading, idle expiry, long questions, simultaneous requests and Docker CPU inference can be slower. Total server time includes classification and the selected transport; browser/network time is measured separately by the benchmark script.

Measured locally on 2026-09-26 with native Ollama: median complete response times were **1.11 s REST, 1.26 s GraphQL and 1.86 s gRPC**, over three requests per protocol. The gRPC measurement includes all three paced snapshots. Three contextual follow-ups also passed in 1.02–1.52 seconds. These are warm, sequential guided prompts, not a load test or a guarantee for arbitrary questions. See [raw measurements](docs/performance.json). Reproduce with `node scripts/benchmark.mjs`; optionally set `BENCHMARK_ROUNDS=10`. The script records its initial warmup separately and verifies every route, target and gRPC snapshot count.
