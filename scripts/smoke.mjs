// Runs against an already-started backend; --ai also verifies live model decisions.
const base = process.env.API_URL ?? 'http://localhost:8080';
const provider = process.env.AI_PROVIDER;
if (provider && !['ollama', 'jev', 'clm'].includes(provider)) throw new Error('AI_PROVIDER must be ollama, jev, or clm');
const cases = [
  ['Tell me about the Millennium Falcon', 'REST', 'ship:5'],
  ['Which starships appear in A New Hope, and who is in its cast?', 'GRAPHQL', 'film:1'],
  ['Who flew the Millennium Falcon in A New Hope?', 'GRAPHQL', 'crew:5:1'],
  ['Who flew the Millennium Falcon in Return of the Jedi?', 'GRAPHQL', 'crew:5:3'],
  ['Tell me about Luke Skywalker', 'REST', 'cast:1'],
  ['Which films feature Luke Skywalker?', 'GRAPHQL', 'cast:1'],
  ['Who flew the X-wing in A New Hope?', 'GRAPHQL', 'crew:7:1'],
  ['Track the Millennium Falcon', 'GRPC', 'ship:5'],
  ...(process.argv.includes('--ai') ? [['Who flew it in A New Hope?', 'GRAPHQL', 'crew:5:1', [{ role: 'user', content: 'Tell me about the Millennium Falcon' }]]] : []),
];
for (const [message, expected, target, history = []] of cases) {
  const response = await fetch(`${base}/api/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ message, override: process.argv.includes('--ai') ? null : expected, history, provider }),
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
  if (target === 'crew:5:1' && (!result.answer.includes('Han Solo') || !result.answer.includes('Chewbacca') || result.answer.includes('Lando'))) throw new Error('Incorrect A New Hope crew');
  if (target === 'crew:5:3' && (!result.answer.includes('Lando Calrissian') || !result.answer.includes('Nien Nunb') || result.answer.includes('Han Solo'))) throw new Error('Incorrect Endor crew');
  if (target === 'crew:7:1' && !result.answer.includes('No verified flight-crew records')) throw new Error('Missing crew coverage must be explicit');
  if (target === 'cast:1' && (!result.answer.includes('Luke Skywalker') || result.answer.includes('Mark Hamill'))) throw new Error('Cast must mean characters');
  const snapshots = events.filter(event => event.type === 'snapshot').length;
  if (expected === 'GRPC' && snapshots !== 3) throw new Error('Expected 3 streamed snapshots');
  console.log(`PASS ${expected} → ${target} | ${result.insight.decision.source} | ${snapshots} snapshots`);
}
