import { spawn, spawnSync } from 'node:child_process';
import { existsSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { createServer } from 'node:net';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { runServices } from './services.mjs';

const root = dirname(dirname(fileURLToPath(import.meta.url)));
const [command, ...args] = process.argv.slice(2);
const nativeCommands = ['doctor', 'dev', 'backend', 'ollama'];
if (nativeCommands.includes(command) && existsSync(join(root, '.env.native'))) {
  process.loadEnvFile(join(root, '.env.native'));
}

function run(executable, argv, cwd = root, env = process.env) {
  return new Promise((resolve, reject) => {
    const child = spawn(executable, argv, { cwd, env, stdio: 'inherit' });
    child.on('error', reject);
    child.on('exit', (code, signal) => {
      if (code === 0) resolve();
      else reject(new Error(`${executable} exited with ${signal ?? code}.`));
    });
  });
}

function requireJava() {
  const java = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', 'javac') : 'javac';
  const result = spawnSync(java, ['-version'], { encoding: 'utf8' });
  const output = `${result.stdout ?? ''}${result.stderr ?? ''}`;
  if (result.status !== 0 || !/javac 25(?:\.|\s|$)/.test(output)) {
    throw new Error(
      'JDK 25 is required. Run npm run java:install on macOS, then set JAVA_HOME as shown in README.md.',
    );
  }
  console.log(output.trim());
}

function requireFrontend() {
  if (!existsSync(join(root, 'frontend/node_modules/vite/bin/vite.js'))) {
    throw new Error('Frontend dependencies are missing. Run npm run setup.');
  }
  console.log('Frontend dependencies are installed.');
}

async function freePort(port) {
  await new Promise((resolve, reject) => {
    const server = createServer();
    server.once('error', () =>
      reject(
        new Error(
          `Port ${port} is unavailable. Stop the Docker stack (npm run rancher:stop) or the process using it before starting native development.`,
        ),
      ),
    );
    server.listen(port, '127.0.0.1', () => server.close(resolve));
  });
}

async function ollamaStatus() {
  const base = process.env.OLLAMA_URL || 'http://127.0.0.1:11434';
  let response;
  try {
    response = await fetch(`${base.replace(/\/$/, '')}/api/tags`, {
      signal: AbortSignal.timeout(3000),
    });
  } catch {
    throw new Error(
      'Cannot reach native Ollama. Run npm run ollama:start, or check OLLAMA_URL in .env.native.',
    );
  }
  if (!response.ok) throw new Error(`Ollama returned HTTP ${response.status}.`);
  const { models } = await response.json();
  const model = selectedModel();
  if (!models?.some((item) => item.name === model || item.model === model)) {
    throw new Error(`Ollama is running, but ${model} is missing. Run npm run ollama:pull.`);
  }
  console.log(`Ollama is ready with ${model}.`);
}

function selectedModel() {
  return (
    process.env.OLLAMA_MODEL ||
    ((!process.env.AI_PROVIDER || process.env.AI_PROVIDER === 'ollama') && process.env.AI_MODEL) ||
    'qwen3:4b-instruct-2507-q4_K_M'
  );
}

async function main() {
  switch (command) {
    case 'install-java':
      if (process.platform !== 'darwin')
        throw new Error('Install a JDK 25 distribution and set JAVA_HOME; see README.md.');
      await run('brew', ['install', 'openjdk@25']);
      console.log(
        'Next: export JAVA_HOME="$(brew --prefix openjdk@25)/libexec/openjdk.jdk/Contents/Home"',
      );
      console.log('Then: export PATH="$JAVA_HOME/bin:$PATH"');
      break;
    case 'install-ollama':
      if (process.platform === 'darwin') {
        await run('brew', ['install', 'ollama']);
      } else if (process.platform === 'linux') {
        const folder = mkdtempSync(join(tmpdir(), 'protocol-ollama-'));
        try {
          const response = await fetch('https://ollama.com/install.sh', {
            signal: AbortSignal.timeout(30000),
          });
          if (!response.ok)
            throw new Error(`Ollama installer download failed: HTTP ${response.status}`);
          const installer = join(folder, 'install.sh');
          writeFileSync(installer, await response.text(), { mode: 0o700 });
          await run('sh', [installer]);
        } finally {
          rmSync(folder, { recursive: true, force: true });
        }
      } else {
        throw new Error('Install Ollama from https://ollama.com/download/windows, or use WSL.');
      }
      break;
    case 'ollama': {
      const host = process.env.OLLAMA_URL || 'http://127.0.0.1:11434';
      await run('ollama', args[0] === 'pull' ? ['pull', args[1] || selectedModel()] : args, root, {
        ...process.env,
        OLLAMA_HOST: host,
      });
      break;
    }
    case 'doctor': {
      let failures = 0;
      console.log(`Node ${process.versions.node}; native settings: .env.native (optional).`);
      for (const check of [requireJava, requireFrontend, ollamaStatus]) {
        try {
          await check();
        } catch (error) {
          failures++;
          console.error(error.message);
        }
      }
      process.exitCode = failures ? 1 : 0;
      break;
    }
    case 'backend':
      requireJava();
      await run('./mvnw', ['-B', ...args], join(root, 'backend'));
      break;
    case 'dev':
      requireJava();
      requireFrontend();
      if (process.env.PORT && process.env.PORT !== '8080')
        throw new Error(
          'The frontend proxy targets port 8080. Remove the PORT override for npm run dev.',
        );
      await Promise.all([8080, Number(process.env.GRPC_PORT || 9090), 5173].map(freePort));
      try {
        await ollamaStatus();
      } catch (error) {
        console.warn(
          `${error.message}\nAutomatic Ollama routing is unavailable; presenter overrides still work.`,
        );
      }
      console.log(
        'Open http://localhost:5173 after Vite is ready. Ctrl+C stops the backend and frontend.',
      );
      process.exitCode = await runServices([
        {
          name: 'backend',
          command: './mvnw',
          args: ['-B', 'spring-boot:run'],
          cwd: join(root, 'backend'),
        },
        {
          name: 'frontend',
          command: process.execPath,
          args: ['node_modules/vite/bin/vite.js', '--host', '127.0.0.1', '--strictPort'],
          cwd: join(root, 'frontend'),
        },
      ]);
      break;
    case 'smoke':
      // Deliberately avoid loading .env.native: these checks also target Docker.
      await run(process.execPath, ['scripts/smoke.mjs', ...args]);
      break;
    default:
      throw new Error('Unknown command. Run npm run to list available project commands.');
  }
}

main().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
