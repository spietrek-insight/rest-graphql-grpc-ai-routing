// Runs against an already-started backend; --ai also verifies live model decisions.
const base = process.env.API_URL ?? 'http://localhost:8080';
const provider = process.env.AI_PROVIDER;
if (provider && !['ollama', 'jev', 'clm'].includes(provider)) throw new Error('AI_PROVIDER must be ollama, jev, or clm');
const cases = [
  ['Tell me about the Millennium Falcon', 'REST', 'ship:5'],
  ['Which starships appear in A New Hope, and who produced it?', 'GRAPHQL', 'film:1'],
  ['Track the Millennium Falcon', 'GRPC', 'ship:5'],
];
for (const [message, expected, target] of cases) {
  const response = await fetch(`${base}/api/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ message, override: process.argv.includes('--ai') ? null : expected, history: [], provider }),
    signal: AbortSignal.timeout(150_000),
  });
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  const events = (await response.text()).trim().split('\n').map(line => JSON.parse(line));
  const error = events.find(event => event.type === 'error');
  if (error) throw new Error(error.data.message);
  const result = events.find(event => event.type === 'result')?.data;
  if (!result || result.insight.decision.protocol !== expected || result.insight.decision.target !== target) {
    throw new Error(`Unexpected route for ${message}: ${JSON.stringify(result?.insight?.decision)}`);
  }
  const snapshots = events.filter(event => event.type === 'snapshot').length;
  if (expected === 'GRPC' && snapshots !== 3) throw new Error('Expected 3 streamed snapshots');
  console.log(`PASS ${expected} → ${target} | ${result.insight.decision.source} | ${snapshots} snapshots`);
}
