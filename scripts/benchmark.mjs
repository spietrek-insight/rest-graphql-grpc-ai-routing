// Run against a local backend with its model available. No presenter overrides or response cache.
const base = process.env.API_URL ?? 'http://localhost:8080';
const provider = process.env.AI_PROVIDER;
if (provider && !['ollama', 'jev', 'clm'].includes(provider)) throw new Error('AI_PROVIDER must be ollama, jev, or clm');
const rounds = Number(process.env.BENCHMARK_ROUNDS ?? 3);
if (!Number.isInteger(rounds) || rounds < 1 || rounds > 100) throw new Error('BENCHMARK_ROUNDS must be 1–100');
const cases = [
  ['Tell me about the Millennium Falcon', 'REST', 'ship:5'],
  ['Which starships appear in A New Hope, and who is in its cast?', 'GRAPHQL', 'film:1'],
  ['Track the Millennium Falcon', 'GRPC', 'ship:5'],
];
async function measure([message, protocol, target], history = []) {
  const start = performance.now();
  const response = await fetch(`${base}/api/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ message, history, provider }),
    signal: AbortSignal.timeout(250_000),
  });
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  const events = [];
  let firstSnapshotMs = null;
  let buffer = '';
  const reader = response.body.pipeThrough(new TextDecoderStream()).getReader();
  function consume(line) {
    if (!line.trim()) return;
    const event = JSON.parse(line);
    if (event.type === 'snapshot' && firstSnapshotMs === null) firstSnapshotMs = Math.round(performance.now() - start);
    events.push(event);
  }
  try {
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += value;
      const lines = buffer.split('\n');
      buffer = lines.pop();
      lines.forEach(consume);
    }
    consume(buffer);
  } finally { reader.releaseLock(); }
  const wallMs = Math.round(performance.now() - start);
  const error = events.find(event => event.type === 'error');
  if (error) throw new Error(error.data.message);
  const insight = events.find(event => event.type === 'result')?.data.insight;
  if (insight?.decision.protocol !== protocol || insight?.decision.target !== target) {
    throw new Error(`Unexpected route: ${JSON.stringify(insight?.decision)}`);
  }
  if (protocol === 'GRPC' && events.filter(event => event.type === 'snapshot').length !== 21) {
    throw new Error('Expected 21 simulated flight updates');
  }
  return { protocol, target, wallMs, firstSnapshotMs, ...insight.timings, protocolMs: insight.exchange.durationMs };
}
const status = await fetch(`${base}/api/status`).then(response => response.json());
const warmup = await measure(cases[0]);
const samples = [];
for (let round = 1; round <= rounds; round++) {
  for (const testCase of cases) samples.push({ round, ...await measure(testCase) });
}
const summary = Object.fromEntries(cases.map(([, protocol]) => {
  const times = samples.filter(sample => sample.protocol === protocol).map(sample => sample.wallMs).sort((a, b) => a - b);
  return [protocol, { medianMs: times[Math.floor(times.length / 2)], maxMs: times.at(-1), samples: times.length }];
}));
const followups = [];
for (let round = 1; round <= rounds; round++) {
  followups.push({ round, ...await measure(
    ['Which films does it appear in and who is in their cast?', 'GRAPHQL', 'ship:5'],
    [
      { role: 'user', content: 'Tell me about the Millennium Falcon' },
      { role: 'assistant', content: 'Millennium Falcon — Maximum speed: 1050; length: 34.37 (source units unspecified). Appears in A New Hope, The Empire Strikes Back, Return of the Jedi. Selected cast: Han Solo, Chewbacca.' },
    ],
  ) });
}
console.log(JSON.stringify({ recordedAt: new Date().toISOString(), status, selectedProvider: provider ?? status.provider, warmup, summary, samples, followups }, null, 2));
