export type Protocol = 'REST' | 'GRAPHQL' | 'GRPC';
export type Override = Protocol | 'AUTO';
export type AiProvider = 'ollama' | 'jev';
export interface ProviderOption {
  id: AiProvider;
  model: string;
  local: boolean;
  available: boolean;
}
export interface Decision {
  protocol: Protocol;
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
}
export interface Insight {
  decision: Decision;
  exchange: Exchange;
  timings: { routingMs: number; totalMs: number };
}
export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  insight?: Insight;
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
  | { type: 'snapshot'; data: unknown }
  | { type: 'result'; data: { answer: string; insight: Insight } }
  | { type: 'error'; data: { message: string } };
