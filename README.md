# Protocol Studio

A conversational API comparison lab: ask a question, let a model choose REST, GraphQL or gRPC, and inspect the actual request and response. Built for the Devnexus session with React, TypeScript and Java 25.

## Choose how to run

Run all commands below from the repository root (`cd /path/to/dev2next`). The root `package.json` provides shortcuts; run `npm run` to list them. It has no third-party dependencies, so you do **not** need a root `npm install`. `npm run setup` installs the frontend from its committed lockfile.

| Mode | Requirements on your machine | Open |
| --- | --- | --- |
| Everything in Docker | Docker Engine + Compose, or Rancher/Docker Desktop | http://localhost:3000 |
| Everything native | Node.js, JDK 25, Ollama | http://localhost:5173 |
| Docker app + native Ollama | Docker stack plus host Ollama | http://localhost:3000 |

Node is needed for the `npm run` shortcuts. Use Node **22.22.2 or newer within Node 22** (the version used by CI and Docker); Node 24.15+ and Node 26+ are also supported. If you only want Docker and do not have Node, use the equivalent `docker compose` commands below. No host Java or Ollama installation is needed for the fully containerized mode.

Allow roughly 8 GB of available memory for the local model. First setup needs internet and several GB of disk space for dependencies, container images, and model weights. The default app needs no database, cloud account, or API key.

## Run everything in Docker

### 1. Install and start a container engine

Install [Rancher Desktop](https://rancherdesktop.io/), [Docker Desktop](https://www.docker.com/products/docker-desktop/), or [Docker Engine with Compose](https://docs.docker.com/engine/install/).

For Rancher Desktop, open the app, select **dockerd (moby)** in **Preferences → Container Engine**, and wait for startup to complete. Kubernetes is not required. The `rancher:*` commands always target the `rancher-desktop` context without changing your global Docker context. The `docker:*` commands use your active context or `DOCKER_CONTEXT`.

Verify Rancher Desktop:

```sh
docker --context rancher-desktop info
docker --context rancher-desktop compose version
```

For Docker Desktop or Docker Engine, omit `--context rancher-desktop`.

### 2. Build and start the stack

```sh
npm run rancher:up
# Docker Desktop / Docker Engine:
# npm run docker:up
```

This builds and checks the frontend and Java backend, starts both services and Ollama, and starts a one-time model download job. It uses the source files currently in your checkout. Build or test failures stop deployment.

Without Node/npm, the equivalent is:

```sh
docker --context rancher-desktop compose up --build -d
# Or for your active Docker context:
# docker compose up --build -d
```

### 3. Wait for the backend and model

```sh
npm run rancher:status
npm run rancher:model
```

The backend should show **healthy**. The model log should finish with **success**, and `model-init` should show **Exited (0)**. This is a successful one-time job, not a crashed service. Ctrl+C stops following logs and leaves the containers running. Use the corresponding `docker:status` / `docker:model` commands for other Docker engines.

### 4. Open and verify the app

Open **http://localhost:3000**. Wait for **Backend connected**, then try a suggested question. Automatic routing is ready after model setup; presenter overrides work before that.

Optional checks through the frontend proxy (macOS/Linux/WSL shell):

```sh
API_URL=http://localhost:3000 npm run smoke
API_URL=http://localhost:3000 npm run smoke:ai
```

The second check uses the real local AI model and can take longer on the first request. Each check should print PASS for REST, GraphQL, and gRPC.

### 5. Stop, resume, inspect, or rebuild

```sh
npm run rancher:logs     # Follow recent logs; Ctrl+C exits log viewing
npm run rancher:stop     # Stop containers; retain containers and models
npm run rancher:start    # Resume existing images without rebuilding
npm run rancher:up       # Rebuild after source/dependency changes
npm run rancher:down     # Remove containers/network; retain model volume
```

Use `docker:*` instead of `rancher:*` for the active Docker context. After `down`, `start` can recreate the containers using already-built images. Do not add `-v` to `down` unless you intend to delete downloaded models. Keep Rancher Desktop or Docker Desktop running while using the app.

Configuration is optional: copy `.env.example` to `.env` if needed, edit it, then run `npm run rancher:up` to apply it. For another web port, set `WEB_PORT=3001` in `.env`, then open http://localhost:3001 and update `API_URL` in the smoke commands. See the [Rancher guide](docs/rancher-desktop.md) for more troubleshooting.

## Run everything outside Docker

The native commands support **macOS and Linux, including WSL**. On Windows without WSL, use the Docker instructions. Native Ollama uses the Mac GPU on Apple Silicon, which is generally faster than inference in the Linux VM.

### 1. Install Node.js and Java 25

The backend's Maven build targets **Java 25**. Java 21 is insufficient. Maven itself is downloaded automatically by the committed wrapper on first use.

On macOS, install [Homebrew](https://brew.sh/) if needed, then:

```sh
brew install node@22
export PATH="$(brew --prefix node@22)/bin:$PATH"
node --version
npm --version

npm run java:install
export JAVA_HOME="$(brew --prefix openjdk@25)/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
java -version
javac -version
```

If you already manage a supported Node version with nvm or another manager, keep using it. `java:install` uses the [Homebrew JDK 25 formula](https://formulae.brew.sh/formula/openjdk@25). Save the applicable `export` lines in your shell profile (for example `~/.zshrc`) to retain them in new terminals.

On Linux/WSL, install a supported [Node.js release](https://nodejs.org/en/download) and a **JDK 25** distribution for your system. Set `JAVA_HOME` to its installation directory and put `$JAVA_HOME/bin` on `PATH`. Verify both `java -version` and `javac -version` report 25 before continuing. `java:install` automates the Homebrew path only.

### 2. Install frontend dependencies and Ollama

```sh
npm run setup
npm run ollama:install
```

`setup` runs `npm ci` in `frontend/`. It does not install system tools or start services. `ollama:install` installs the [Homebrew Ollama formula](https://formulae.brew.sh/formula/ollama) on macOS; on Linux/WSL it downloads and runs the [official Ollama installer](https://docs.ollama.com/linux), which may request administrator access and configure a system service. Skip this command if Ollama is already installed and available on `PATH`.

### 3. Start Ollama in terminal A

```sh
npm run ollama:start
```

Leave this terminal open. If the Ollama desktop app or a system service is already serving port 11434, use that existing server instead; do not start a second copy. `npm run ollama:list` in another terminal checks the running server.

### 4. Download the model in terminal B

Open a second terminal, return to the repository root, and ensure the Node/JAVA_HOME setup from step 1 is available there:

```sh
npm run ollama:pull
npm run ollama:list
```

The default model is `qwen3:4b-instruct-2507-q4_K_M`. The initial download is roughly 2.5 GB. The pull command reuses already-downloaded layers on later runs.

### 5. Stop any Docker copy, then start the app

Native and Docker backends share ports 8080 and 9090. Stop whichever Docker stack you started before switching modes:

```sh
npm run rancher:stop
# Or: npm run docker:stop
```

Skip the stop command if no Docker stack is running. Then, in terminal B:

```sh
npm run doctor
npm run dev
```

`doctor` checks JDK 25, frontend installation, and the selected model on the native Ollama server. `dev` starts the backend and frontend together, displaying their logs in this terminal. It fails on occupied app ports and stops both child process groups if either service exits. It warns if Ollama/model setup is incomplete; presenter overrides remain available.

Open **http://localhost:5173** once Vite is ready and wait for **Backend connected**. The backend may take longer on first startup while the Maven wrapper downloads dependencies. The backend uses 8080, GraphQL is at `/graphql`, gRPC uses 9090, and Ollama uses 11434. Vite proxies `/api` to the backend on 8080.

### 6. Verify and stop

In another terminal at the repository root:

```sh
npm run smoke
npm run smoke:ai
```

These checks default to the backend at http://localhost:8080. Ctrl+C in terminal B stops the frontend and backend. Ctrl+C in terminal A stops the manually launched Ollama server. An existing Ollama app/system service stays running; stop it through its own controls if desired. Downloaded models persist.

For later runs, start Ollama if necessary, then run `npm run dev`. Repeat `npm run setup` whenever the frontend lockfile changes. To run the app processes separately, use `npm run dev:backend` and `npm run dev:frontend` in separate terminals.

### Optional native configuration

Copy `.env.native.example` to `.env.native` and edit it. The root `dev`, `dev:backend`, `doctor`, and `ollama:*` runtime commands load this file; existing shell variables take precedence. No environment file is required for the defaults. Docker continues to read `.env` separately, so Docker-only addresses such as `host.docker.internal` do not leak into native runs.

For a different model, set `OLLAMA_MODEL` in `.env.native`, run `npm run ollama:pull`, then restart the backend. The pull command uses that same model choice. Set `OLLAMA_URL` to the server's base URL if using another Ollama server; it also controls the Ollama CLI endpoint for the root commands. Do not override backend `PORT` when using the combined `dev` command: the frontend proxy expects 8080.

The direct `./mvnw spring-boot:run` command does not load `.env.native`; use the root helper or export variables yourself. Smoke commands intentionally read shell variables only so the same command can check native or Docker deployments. Both `.env` and `.env.native` are ignored by Git; keep credentials out of tracked files.

## Docker app with native Ollama on a Mac

1. Complete native steps 2–4 for Ollama (a host JDK is not needed for this mode).
2. Copy `.env.example` to `.env` if it does not already exist. Preserve existing settings and add `OLLAMA_URL=http://host.docker.internal:11434`.
3. Run `npm run rancher:up` (or `npm run docker:up`).
4. Open http://localhost:3000 and run the Docker smoke checks above. Keep native Ollama running.

The Compose Ollama container and model volume remain available, although backend requests now use the host server. To return to the fully containerized model, remove the `OLLAMA_URL` override from `.env` and run the corresponding `up` command again. If the VM cannot reach the host server, see the [Rancher networking guide](docs/rancher-desktop.md).

## Root command reference

| Command | Purpose |
| --- | --- |
| `npm run setup` | Install frontend dependencies from the lockfile |
| `npm run java:install` | Install JDK 25 with Homebrew on macOS |
| `npm run ollama:install` | Install Ollama on macOS or Linux/WSL |
| `npm run ollama:start` | Run the native model server in the foreground |
| `npm run ollama:pull` / `ollama:list` | Download the configured model / list downloaded models |
| `npm run doctor` | Check native prerequisites and model readiness |
| `npm run dev` | Run native backend and frontend together |
| `npm run dev:backend` / `dev:frontend` | Run one application process |
| `npm test` | Check helper scripts, frontend, and backend; requires JDK 25 |
| `npm run check:frontend` / `check:backend` | Run checks for one application |
| `npm run smoke` / `smoke:ai` | Verify a running app without / with real AI routing |
| `npm run rancher:up` / `docker:up` | Build, check, and start the container stack |
| `npm run rancher:start` / `docker:start` | Start existing images without rebuilding |
| `npm run rancher:stop` / `docker:stop` | Stop services while preserving containers/models |
| `npm run rancher:down` / `docker:down` | Remove containers/network while preserving models |
| `npm run rancher:status` / `docker:status` | Show service and model-job status |
| `npm run rancher:logs` / `docker:logs` | Follow application logs |
| `npm run rancher:model` / `docker:model` | Follow model download progress |

## Startup troubleshooting

- **Java missing or wrong version:** use a JDK, not just a JRE. Check `JAVA_HOME`, `java -version`, and `javac -version`; the backend requires 25.
- **Node engine error during setup:** select a supported Node version listed above and rerun `npm run setup`.
- **Port already occupied:** stop the other native/Docker copy. The native launcher checks 8080, 9090 (or `GRPC_PORT`), and 5173, and Vite is configured not to silently switch to another port.
- **Cannot connect to Docker:** start the desktop engine; for Rancher select dockerd (moby) and use `rancher:*`. A missing context usually means Rancher has not finished initialization.
- **Ollama cannot connect:** start Ollama and check `npm run ollama:list`. In Docker, inspect `rancher:status` and `rancher:model` instead.
- **Model missing:** run `npm run ollama:pull` for native use; for Docker wait for `model-init` to exit successfully. First inference can take longer while weights load.
- **Frontend loads but backend is disconnected:** wait for backend startup, inspect the native terminal or Docker logs, and use the correct web URL: 5173 for native, 3000 for Docker.

## Optional Jev classification

Jev is a structured decision model, not a text generator. This implementation sends two Choice questions (protocol and catalog target) to the documented TypeSafe HTTP API, preserves its confidence/probabilities in the inspector, and composes answers from returned catalog data.

```dotenv
JEV_MODEL=jev-latest
TYPESAFE_API_KEY=your-key
```

Put these in a root `.env` file for Compose, then run `docker compose up -d --force-recreate backend frontend` once to apply the credential. No image rebuild is needed for environment changes. For native development, put these values in `.env.native` and restart `npm run dev`, or export them in the backend shell. Spring itself does not automatically load `.env`. The key remains server-side. Jev uses a hosted endpoint and requires internet; the reviewed public docs do not document downloadable weights or a local runtime. It is optional, and the Ollama default has no cloud dependency. Documentation: https://docs.typesafe.ai/api and https://docs.ollama.com/capabilities/tool-calling.

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

After changing `.env`, run `docker compose up -d --force-recreate backend frontend` to apply configuration (use `--build` when also updating application code). For native development, put these variables in `.env.native` before starting the backend through the root commands, or export them in the backend shell. Then select **CLM** in **AI preferences**. Set `AI_PROVIDER=clm` only if you want it to be the initial choice for browsers without a saved preference.

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
npm test
# Or individually from the repository root:
npm run check:frontend
npm run check:backend
# Direct equivalents:
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
