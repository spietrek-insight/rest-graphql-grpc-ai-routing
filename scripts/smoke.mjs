// Runs against an already-started backend; --ai also verifies live model decisions.
const base = process.env.API_URL ?? 'http://localhost:8080';
const provider = process.env.AI_PROVIDER;
if (provider && !['ollama', 'jev', 'clm'].includes(provider)) throw new Error('AI_PROVIDER must be ollama, jev, or clm');
const ai = process.argv.includes('--ai');
// [question, protocol, target, operation, history]
const cases = [
  ['Tell me about the Millennium Falcon', 'REST', 'ship:5', 'STARSHIP'],
  ['Who is the cast in A New Hope?', 'REST', 'film:1', 'FILM'],
  ['Which starships appear in A New Hope?', 'GRAPHQL', 'film:1', 'SHIPS_IN_FILM'],
  ['Which starships appear in A New Hope, and who flew them?', 'GRAPHQL', 'film:1', 'FILM_SHIPS_CREW'],
  ['Who flew the Millennium Falcon in A New Hope?', 'GRAPHQL', 'crew:5:1', 'SHIP_CREW'],
  ['Who flew the Millennium Falcon in Return of the Jedi?', 'GRAPHQL', 'crew:5:3', 'SHIP_CREW'],
  ['Tell me about Luke Skywalker', 'REST', 'cast:1', 'CHARACTER'],
  ['Which films feature Luke Skywalker?', 'GRAPHQL', 'cast:1', 'CHARACTER_FILMS'],
  ['Who flew the X-wing in A New Hope?', 'GRAPHQL', 'crew:7:1', 'SHIP_CREW'],
  ['Track the Millennium Falcon', 'GRPC', 'ship:5', 'TRACK'],
  ...(ai
    ? [
        ['Who flew it in A New Hope?', 'GRAPHQL', 'crew:5:1', 'SHIP_CREW', [{ role: 'user', content: 'Tell me about the Millennium Falcon' }]],
        // Regression: a crew answer in history must not turn a cast question into a crew lookup.
        ['Who is the cast in A New Hope?', 'REST', 'film:1', 'FILM', [
          { role: 'user', content: 'Who flew the Millennium Falcon in A New Hope?' },
          { role: 'assistant', content: 'Millennium Falcon in A New Hope: Han Solo — pilot. Millennium Falcon in A New Hope: Chewbacca — co-pilot.' },
        ]],
      ]
    : []),
];
for (const [message, expected, target, operation, history = []] of cases) {
  const response = await fetch(`${base}/api/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ message, override: ai ? null : expected, history, provider }),
    signal: AbortSignal.timeout(250_000),
  });
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  const events = (await response.text()).trim().split('\n').map(line => JSON.parse(line));
  const error = events.find(event => event.type === 'error');
  if (error) throw new Error(error.data.message);
  const result = events.find(event => event.type === 'result')?.data;
  const decision = result?.insight?.decision;
  if (!decision || decision.protocol !== expected || decision.target !== target || decision.operation !== operation) {
    throw new Error(`Unexpected route for ${message}: ${JSON.stringify(result?.insight?.decision)}`);
  }
  if (target === 'crew:5:1' && (!result.answer.includes('Han Solo') || !result.answer.includes('Chewbacca') || result.answer.includes('Lando'))) throw new Error('Incorrect A New Hope crew');
  if (target === 'crew:5:3' && (!result.answer.includes('Lando Calrissian') || !result.answer.includes('Nien Nunb') || result.answer.includes('Han Solo'))) throw new Error('Incorrect Endor crew');
  if (target === 'crew:7:1' && !result.answer.includes('No verified flight-crew records')) throw new Error('Missing crew coverage must be explicit');
  if (operation === 'FILM' && (!result.answer.includes('Leia Organa') || result.answer.includes('pilot'))) throw new Error('Cast answers must list characters, not crew');
  if (operation === 'FILM_SHIPS_CREW' && !result.answer.includes('Han Solo (pilot)')) throw new Error('Expected ships with their A New Hope crew');
  if (expected === 'GRAPHQL' && !(result.insight.exchange.comparison?.responseBytes > result.insight.exchange.responseBytes)) throw new Error('GraphQL answers must carry a measured REST comparison');
  if (target === 'cast:1' && (!result.answer.includes('Luke Skywalker') || result.answer.includes('Mark Hamill'))) throw new Error('Cast must mean characters');
  const snapshots = events.filter(event => event.type === 'snapshot').length;
  if (expected === 'GRPC') {
    const updates = events.filter(event => event.type === 'snapshot').map(event => event.data);
    if (snapshots !== 21 || updates.some((u, i) => u.sequence !== i + 1 || u.totalSnapshots !== 21 || !u.flight?.simulated)) throw new Error('Expected 21 ordered simulated flight updates');
    if (new Set(updates.map(u => u.flight.location)).size < 10 || new Set(updates.map(u => u.flight.pilot.name)).size !== 2) throw new Error('Flight must change location and pilot');
    if (updates.at(-1).flight.phase !== 'Arrived' || result.insight.exchange.operation !== 'catalog.StarshipTracker/Watch') throw new Error('Expected one completed server-streaming flight');
  }
  console.log(`PASS ${expected} ${operation} → ${target} | ${decision.source} | ${snapshots} snapshots`);
}
