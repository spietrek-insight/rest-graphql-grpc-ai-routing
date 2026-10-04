# Run Protocol Studio in Rancher Desktop

For first-time installation and complete native, Docker, and hybrid walkthroughs, start with the [README](../README.md#choose-how-to-run). Root shortcuts are available without a root dependency install: `npm run rancher:up`, `rancher:start`, `rancher:stop`, `rancher:status`, `rancher:logs`, and `rancher:model`. The direct Docker commands below work without Node/npm.

Start Rancher Desktop and use its **dockerd (moby)** container engine. Kubernetes is not required. Run the commands below from the repository root.

## Start or update

```sh
cd /Users/spietrek/Projects/dev2next
docker --context rancher-desktop compose up --build -d
```

For a fresh clone, replace the directory above with your checkout path. Open **http://localhost:3000**. Port 5173 is only for the separate Vite development server.

The first run downloads build dependencies, images and a roughly 2.5 GB model. The frontend and Java backend run in containers; Ollama runs in its own container. No API key or database is needed. Containers run in the background independently of the terminal or coding session. Keep Rancher Desktop running while using the app.

Check startup and model download:

```sh
docker --context rancher-desktop compose ps -a
docker --context rancher-desktop compose logs -f model-init
```

`backend` should be healthy. `model-init` is a one-time download job: **Exited (0)** means success. Automatic routing is available once the model is downloaded; its first load can take longer. Presenter overrides work without model inference. Ctrl+C stops following logs, not the containers.

## Stop and resume

```sh
# Stop the app; preserve its containers and downloaded model.
docker --context rancher-desktop compose stop

# Resume, without rebuilding.
docker --context rancher-desktop compose up -d

# View application errors.
docker --context rancher-desktop compose logs --tail=100 backend frontend ollama
```

`docker --context rancher-desktop compose down` removes the app's containers and network but preserves its model volume. Avoid `down -v` unless you intend to delete the downloaded model. On later Rancher Desktop restarts, services use `restart: unless-stopped`; services you explicitly stopped need `compose up -d` again.

## Performance on Apple Silicon

The fully containerized model uses CPU inference inside Rancher Desktop's Linux VM. The earlier 1–2 second measurements were made with native Ollama using the Mac GPU and are not a container performance guarantee. More VM CPU and memory can help CPU inference; native Ollama is the faster option on this Mac.

To keep the frontend/backend in Rancher Desktop while using native Ollama, install and start Ollama for macOS, then:

```sh
ollama pull qwen3:4b-instruct-2507-q4_K_M
```

Add this line to a root `.env` file, preserving any existing settings:

```dotenv
OLLAMA_URL=http://host.docker.internal:11434
```

Then recreate only the application services:

```sh
docker --context rancher-desktop compose up -d backend frontend
```

Rancher Desktop documents `host.docker.internal` and `host.rancher-desktop.internal` for reaching host services in its [networking FAQ](https://docs.rancherdesktop.io/faq/). Native Ollama must remain running. The container model remains available if you remove `OLLAMA_URL` from `.env` and recreate the backend. This optional host-model configuration is separate from the fully containerized default.

## Connection errors

If the browser says the backend is unavailable, check Rancher Desktop is running, run `compose up -d`, and refresh **http://localhost:3000**. Inspect the logs above if the backend is unhealthy. A port-allocation error on 3000, 8080 or 9090 means another local process is using an application port; stop the conflicting development process before retrying. The model download does not need to be repeated after ordinary stops.

## Switch AI providers during a demo

Use **AI preferences** in the top bar. Switching between configured Ollama, Jev, and CLM applies to the next question, with no redeployment. Your browser remembers the choice. Jev is an optional hosted service; only the selected provider is called.

To enable Jev initially, add `TYPESAFE_API_KEY=your-key` to the root `.env` and run `docker --context rancher-desktop compose up -d --force-recreate backend frontend` once, then refresh the page. This applies server configuration; subsequent preference changes need no container restart. Keep the key out of source control. The default hosted model is `jev-latest`, configurable through `JEV_MODEL` independently of `OLLAMA_MODEL`.

## Verified deployment

On 2026-09-26, all three automatic protocol routes and a contextual follow-up passed through port 3000. With 2 VM CPU cores and about 6 GB RAM, one warm sample per scenario took 1.46 s (REST), 2.82 s (GraphQL), 3.64 s (gRPC, including three updates), and 5.10 s (follow-up). Initial model/prompt loading took 34.24 s. See [recorded measurements](rancher-performance.json). These CPU results are separate from the native GPU benchmarks.

### Enable CLM

Start a compatible CLM server and Qwen3-8B encoder separately. Set `CLM_URL=http://host.docker.internal:8700` in the root `.env` if the API runs on the Mac host, or use your remote CLM base URL. Optionally set `CLM_MODEL` (default `clm-latest`) and `CLM_API_KEY` if the server requires authentication. Recreate the backend with `docker --context rancher-desktop compose up -d --force-recreate backend frontend`, then refresh and select **CLM** in **AI preferences**. Build updated application images first when upgrading from a version without CLM support.

CLM stays disabled until its URL is configured. The app does not start a CLM model server, and this integration does not establish Apple Silicon encoder compatibility. See [CLM setup and validation](../README.md#optional-self-hosted-clm-classification).
