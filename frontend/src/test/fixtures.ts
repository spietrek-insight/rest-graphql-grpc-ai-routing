import type { Insight } from '@/types';
export const providers = [
  { id: 'ollama' as const, model: 'qwen3:4b-instruct-2507-q4_K_M', local: true, available: true },
  { id: 'jev' as const, model: 'jev-latest', local: false, available: true },
  { id: 'clm' as const, model: 'clm-latest', local: false, available: true },
];
export const insight: Insight = {
  timings: { routingMs: 1200, totalMs: 1215 },
  decision: {
    protocol: 'REST',
    target: 'ship:5',
    source: 'Presenter override',
    reason: 'A single resource.',
    classification: { override: 'REST' },
  },
  exchange: {
    operation: 'GET /api/catalog/starships/ship:5',
    endpoint: 'http://localhost:8080/api/catalog/starships/ship:5',
    encoding: 'JSON',
    request: { method: 'GET' },
    response: [{ name: 'Millennium Falcon' }],
    durationMs: 12,
    responseBytes: 245,
  },
};

export const flightSnapshot: import('@/types').TrackSnapshot = {
  sequence: 1,
  totalSnapshots: 21,
  flight: {
    simulated: true,
    scenarioId: 'falcon-demo-v1',
    title: 'Falcon flight: Tatooine to Yavin 4',
    shipName: 'Millennium Falcon',
    origin: 'Tatooine',
    destination: 'Yavin 4',
    location: 'Mos Eisley · docking bay',
    phase: 'Preflight',
    pilot: { id: '2', name: 'Han Solo' },
    coPilot: { id: '5', name: 'Chewbacca' },
    progressPercent: 0,
    speedKph: 0,
    shieldsPercent: 100,
    event: 'Flight checks complete.',
    elapsedMs: 0,
    durationMs: 40000,
  },
};
