export type Protocol = 'REST' | 'GRAPHQL' | 'GRPC';
export type Override = Protocol | 'AUTO';
export type AiProvider = 'ollama' | 'jev' | 'clm';
export interface ProviderOption {
  id: AiProvider;
  model: string;
  local: boolean;
  available: boolean;
}
export interface Decision {
  protocol: Protocol;
  operation?: string;
  target: string;
  source: string;
  reason: string;
  classification: unknown;
}
export interface Exchange {
  operation: string;
  endpoint: string;
  encoding: string;
  request: unknown;
  response: unknown;
  durationMs: number;
  responseBytes: number;
  comparison?: RestComparison | null;
}
/** The same data fetched over REST, measured by the backend for comparison. */
export interface RestComparison {
  calls: string[];
  responseBytes: number;
  durationMs: number;
}
export interface Insight {
  decision: Decision;
  exchange: Exchange;
  timings: { routingMs: number; totalMs: number };
}
export interface FlightState {
  simulated: boolean;
  scenarioId: string;
  title: string;
  shipName: string;
  origin: string;
  destination: string;
  location: string;
  phase: string;
  pilot: { id: string; name: string };
  coPilot: { id: string; name: string };
  progressPercent: number;
  speedKph: number;
  shieldsPercent: number;
  event: string;
  elapsedMs: number;
  durationMs: number;
}
export interface TrackSnapshot {
  sequence: number;
  totalSnapshots?: number;
  observedAt?: string;
  status?: string;
  flight?: FlightState;
}
export interface FlightPlayback {
  snapshots: TrackSnapshot[];
  state: 'streaming' | 'complete' | 'stopped' | 'interrupted';
}
export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  insight?: Insight;
  decision?: Decision;
  flight?: FlightPlayback;
}
export interface Status {
  service: string;
  provider: string;
  model: string;
  local: boolean;
  providers: ProviderOption[];
}
export type ChatEvent =
  | { type: 'decision'; data: Decision }
  | { type: 'snapshot'; data: TrackSnapshot }
  | { type: 'result'; data: { answer: string; insight: Insight } }
  | { type: 'error'; data: { message: string } };
