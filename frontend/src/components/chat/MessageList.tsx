import {
  AssistantRuntimeProvider,
  ThreadPrimitive,
  useExternalStoreRuntime,
  type ThreadMessageLike,
} from '@assistant-ui/react';
import { Braces, ChevronRight, LoaderCircle, Maximize2, User } from 'lucide-react';
import type { ChatMessage, Insight } from '@/types';
import { ProtocolBadge } from '../ProtocolBadge';
import { ProtocolReason } from '../ProtocolReason';
import { ResponseDialog } from '../ResponseDialog';
export function MessageBubble({
  message,
  onInspect,
}: {
  message: ChatMessage;
  onInspect: (insight: Insight) => void;
}) {
  return (
    <article className={`message message-${message.role}`}>
      <div className="message-avatar">
        {message.role === 'assistant' ? <Braces size={18} /> : <User size={17} />}
      </div>
      <div className="message-body">
        <div className="message-heading">
          <strong>{message.role === 'assistant' ? 'Protocol Studio' : 'You'}</strong>
          {message.insight && <ProtocolBadge protocol={message.insight.decision.protocol} />}
        </div>
        {message.insight && <ProtocolReason decision={message.insight.decision} />}
        <div className="message-content">{message.content}</div>
        {message.insight && (
          <div className="message-actions">
            <button className="inspect-link" onClick={() => onInspect(message.insight!)}>
              Inspect this request <span>{message.insight.timings?.totalMs ?? '—'} ms total</span>
              <ChevronRight size={13} />
            </button>
            <ResponseDialog insight={message.insight}>
              <button className="inspect-link">
                <Maximize2 size={12} /> View response
              </button>
            </ResponseDialog>
          </div>
        )}
      </div>
    </article>
  );
}
export function MessageList({
  messages,
  busy,
  snapshots,
  onInspect,
  onSend,
}: {
  messages: ChatMessage[];
  busy: boolean;
  snapshots: number;
  onInspect: (insight: Insight) => void;
  onSend: (text: string) => void;
}) {
  const runtime = useExternalStoreRuntime({
    messages,
    isRunning: busy,
    convertMessage: (m: ChatMessage): ThreadMessageLike => ({
      id: m.id,
      role: m.role,
      content: [{ type: 'text', text: m.content }],
    }),
    onNew: async (message) => {
      const part = message.content.find((p) => p.type === 'text');
      if (part?.type === 'text') onSend(part.text);
    },
  });
  return (
    <AssistantRuntimeProvider runtime={runtime}>
      <ThreadPrimitive.Root className="thread-root">
        <ThreadPrimitive.Viewport className="message-viewport" autoScroll>
          <div className="messages-inner">
            {messages.map((m) => (
              <MessageBubble key={m.id} message={m} onInspect={onInspect} />
            ))}
            {busy && (
              <div className="thinking" role="status">
                <LoaderCircle size={16} className="spin" />
                {snapshots
                  ? `Receiving gRPC snapshots · ${snapshots}/3`
                  : 'Finding the right route…'}
              </div>
            )}
          </div>
        </ThreadPrimitive.Viewport>
      </ThreadPrimitive.Root>
    </AssistantRuntimeProvider>
  );
}
