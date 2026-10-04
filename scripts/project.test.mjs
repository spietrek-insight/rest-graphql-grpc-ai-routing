import assert from 'node:assert/strict';
import { spawn, spawnSync } from 'node:child_process';
import { once } from 'node:events';
import {
  copyFileSync,
  existsSync,
  mkdirSync,
  mkdtempSync,
  readFileSync,
  rmSync,
  writeFileSync,
} from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';
import { test } from 'node:test';

const servicesUrl = new URL('./services.mjs', import.meta.url).href;

function fixture(t) {
  const folder = mkdtempSync(join(tmpdir(), 'protocol-scripts-test-'));
  t.after(() => rmSync(folder, { recursive: true, force: true }));
  return folder;
}

async function waitForFile(path) {
  for (let i = 0; i < 100; i++) {
    if (existsSync(path)) return;
    await delay(20);
  }
  throw new Error(`Timed out waiting for ${path}`);
}

function supervisor(t, services) {
  const code = `import { runServices } from ${JSON.stringify(servicesUrl)}; process.exitCode = await runServices(${JSON.stringify(services)}, { shutdownMs: 200 });`;
  const child = spawn(process.execPath, ['--input-type=module', '-e', code], { stdio: 'ignore' });
  const finished = once(child, 'exit');
  t.after(() => {
    if (child.exitCode === null) child.kill('SIGTERM');
  });
  return { child, finished };
}

function worker(folder, name = 'worker') {
  return {
    name,
    command: process.execPath,
    args: [
      '-e',
      `require('node:fs').writeFileSync(${JSON.stringify(join(folder, name))}, String(process.pid)); setInterval(() => {}, 1000);`,
    ],
  };
}

function assertStopped(path) {
  const pid = Number(readFileSync(path, 'utf8'));
  assert.throws(() => process.kill(pid, 0), { code: 'ESRCH' });
}

test(
  'shutdown forcibly stops a service that ignores graceful termination',
  { skip: process.platform === 'win32' },
  async (t) => {
    const folder = fixture(t);
    const service = worker(folder);
    service.args[1] = `process.on('SIGTERM', () => {}); ${service.args[1]}`;
    const { child, finished } = supervisor(t, [service]);
    await waitForFile(join(folder, 'worker'));
    child.kill('SIGINT');
    assert.equal((await finished)[0], 130);
    assertStopped(join(folder, 'worker'));
  },
);

test(
  'a failed service returns its exit code and stops the other service',
  { skip: process.platform === 'win32' },
  async (t) => {
    const folder = fixture(t);
    const failure = {
      name: 'failure',
      command: process.execPath,
      args: [
        '-e',
        `const timer = setInterval(() => { if (require('node:fs').existsSync(${JSON.stringify(join(folder, 'worker'))})) { clearInterval(timer); process.exit(7); } }, 20);`,
      ],
    };
    const { finished } = supervisor(t, [worker(folder), failure]);
    assert.equal((await finished)[0], 7);
    assertStopped(join(folder, 'worker'));
  },
);

test(
  'Ctrl+C stops both services and reports interruption',
  { skip: process.platform === 'win32' },
  async (t) => {
    const folder = fixture(t);
    const { child, finished } = supervisor(t, [worker(folder, 'api'), worker(folder, 'web')]);
    await Promise.all(['api', 'web'].map((name) => waitForFile(join(folder, name))));
    child.kill('SIGINT');
    assert.equal((await finished)[0], 130);
    assertStopped(join(folder, 'api'));
    assertStopped(join(folder, 'web'));
  },
);

test(
  'a missing executable returns failure instead of leaving the launcher running',
  { skip: process.platform === 'win32' },
  async (t) => {
    const { finished } = supervisor(t, [
      { name: 'missing', command: '/nonexistent/protocol-studio-test' },
    ]);
    assert.equal((await finished)[0], 1);
  },
);

test(
  'native commands load native settings, preserve shell precedence, and forward Maven arguments',
  { skip: process.platform === 'win32' },
  (t) => {
    const folder = fixture(t);
    for (const sub of ['scripts', 'backend', 'jdk/bin'])
      mkdirSync(join(folder, sub), { recursive: true });
    for (const name of ['project.mjs', 'services.mjs'])
      copyFileSync(new URL(name, import.meta.url), join(folder, 'scripts', name));
    writeFileSync(join(folder, '.env'), 'OLLAMA_MODEL=docker-only\n');
    writeFileSync(join(folder, '.env.native'), 'OLLAMA_MODEL=native-model\nAI_PROVIDER=ollama\n');
    writeFileSync(join(folder, 'jdk/bin/javac'), '#!/bin/sh\nprintf "javac 25.0.1\\n"\n', {
      mode: 0o755,
    });
    writeFileSync(
      join(folder, 'backend/mvnw'),
      `#!${process.execPath}\nrequire('node:fs').writeFileSync('../observed.json', JSON.stringify({ args: process.argv.slice(2), model: process.env.OLLAMA_MODEL, provider: process.env.AI_PROVIDER }));\n`,
      { mode: 0o755 },
    );
    const env = { ...process.env, JAVA_HOME: join(folder, 'jdk'), AI_PROVIDER: 'jev' };
    delete env.OLLAMA_MODEL;
    const first = spawnSync(
      process.execPath,
      [join(folder, 'scripts/project.mjs'), 'backend', 'verify', '-DskipTests'],
      { env, encoding: 'utf8' },
    );
    assert.equal(first.status, 0, first.stderr);
    assert.deepEqual(JSON.parse(readFileSync(join(folder, 'observed.json'))), {
      args: ['-B', 'verify', '-DskipTests'],
      model: 'native-model',
      provider: 'jev',
    });
    const second = spawnSync(
      process.execPath,
      [join(folder, 'scripts/project.mjs'), 'backend', 'verify'],
      { env: { ...env, OLLAMA_MODEL: 'shell-model' }, encoding: 'utf8' },
    );
    assert.equal(second.status, 0, second.stderr);
    assert.equal(JSON.parse(readFileSync(join(folder, 'observed.json'))).model, 'shell-model');
  },
);
