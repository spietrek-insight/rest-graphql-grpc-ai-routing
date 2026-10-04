import { create, type StateCreator } from 'zustand';
import type { ChatMessage, Decision, Insight, Override, TrackSnapshot } from '@/types';
interface ConversationSlice {
  messages: ChatMessage[];
  busy: boolean;
  error: string | null;
  snapshots: TrackSnapshot[];
  append: (message: ChatMessage) => void;
  setBusy: (busy: boolean) => void;
  upsert: (message: ChatMessage) => void;
  setError: (error: string | null) => void;
  addSnapshot: (value: TrackSnapshot) => void;
}
interface ProtocolSlice {
  override: Override;
  decision: Decision | null;
  selectedInsight: Insight | null;
  setOverride: (override: Override) => void;
  setDecision: (decision: Decision | null) => void;
  selectInsight: (insight: Insight | null) => void;
}
interface SessionSlice {
  sessionId: string;
  reset: () => void;
}
type ChatState = ConversationSlice & ProtocolSlice & SessionSlice;
const conversationSlice: StateCreator<ChatState, [], [], ConversationSlice> = (set) => ({
  messages: [],
  busy: false,
  error: null,
  snapshots: [],
  append: (message) => set((s) => ({ messages: [...s.messages, message] })),
  upsert: (message) =>
    set((s) => ({
      messages: s.messages.some((m) => m.id === message.id)
        ? s.messages.map((m) => (m.id === message.id ? message : m))
        : [...s.messages, message],
    })),
  setBusy: (busy) => set({ busy, ...(busy ? { snapshots: [], error: null } : {}) }),
  setError: (error) => set({ error }),
  addSnapshot: (value) => set((s) => ({ snapshots: [...s.snapshots, value] })),
});
const protocolSlice: StateCreator<ChatState, [], [], ProtocolSlice> = (set) => ({
  override: 'AUTO',
  decision: null,
  selectedInsight: null,
  setOverride: (override) => set({ override }),
  setDecision: (decision) => set({ decision }),
  selectInsight: (selectedInsight) => set({ selectedInsight }),
});
const sessionSlice: StateCreator<ChatState, [], [], SessionSlice> = (set) => ({
  sessionId: crypto.randomUUID(),
  reset: () =>
    set({
      sessionId: crypto.randomUUID(),
      messages: [],
      busy: false,
      error: null,
      snapshots: [],
      override: 'AUTO',
      decision: null,
      selectedInsight: null,
    }),
});
export const useChatStore = create<ChatState>()((...args) => ({
  ...conversationSlice(...args),
  ...protocolSlice(...args),
  ...sessionSlice(...args),
}));
