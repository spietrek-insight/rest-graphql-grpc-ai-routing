import type { Insight } from '@/types';
export const providers = [
  { id: 'ollama' as const, model: 'qwen3:4b-instruct-2507-q4_K_M', local: true, available: true },
  { id: 'jev' as const, model: 'jev-latest', local: false, available: true },
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
