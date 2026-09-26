import type { AiProvider, ChatEvent, ChatMessage, Override, Status } from '@/types';
export async function getStatus(signal?: AbortSignal): Promise<Status> {
  const response = await fetch('/api/status', { signal });
  if (!response.ok) throw new Error('Backend unavailable');
  return response.json() as Promise<Status>;
}
export async function sendChat(
  message: string,
  override: Override,
  history: ChatMessage[],
  signal: AbortSignal,
  onEvent: (event: ChatEvent) => void,
  provider?: AiProvider,
): Promise<void> {
  const response = await fetch('/api/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      message,
      provider,
      override: override === 'AUTO' ? null : override,
      history: history
        .slice(-20)
        .map(({ role, content }) => ({ role, content: content.slice(0, 4000) })),
    }),
    signal,
  });
  if (!response.ok || !response.body)
    throw new Error('The backend could not accept this request. Please try again.');
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  let finished = false;
  const consume = (line: string) => {
    if (!line.trim()) return;
    const event = JSON.parse(line) as ChatEvent;
    if (event.type === 'error') throw new Error(event.data.message);
    if (event.type === 'result') finished = true;
    onEvent(event);
  };
  try {
    while (true) {
      const { value, done } = await reader.read();
      buffer += decoder.decode(value, { stream: !done });
      const lines = buffer.split('\n');
      buffer = lines.pop() ?? '';
      lines.forEach(consume);
      if (done) break;
    }
    if (buffer.trim()) consume(buffer);
    if (!finished) throw new Error('Connection ended before the answer arrived. Please retry.');
  } finally {
    await reader.cancel();
    reader.releaseLock();
  }
}
