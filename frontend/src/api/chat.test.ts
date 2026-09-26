import { describe, it, expect, vi, afterEach } from 'vitest';
import { sendChat, getStatus } from './chat';
import { insight } from '@/test/fixtures';
const encoder = new TextEncoder();
function response(chunks: string[]) {
  return {
    ok: true,
    body: new ReadableStream({
      start(controller) {
        chunks.forEach((c) => controller.enqueue(encoder.encode(c)));
        controller.close();
      },
    }),
  };
}
afterEach(() => vi.unstubAllGlobals());
describe('chat transport', () => {
  it('loads status and reports non-OK responses', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValueOnce({ ok: true, json: async () => ({ service: 'ready' }) })
        .mockResolvedValue({ ok: false }),
    );
    expect(await getStatus()).toEqual({ service: 'ready' });
    await expect(getStatus()).rejects.toThrow('Backend');
  });
  it('decodes chunk boundaries, unicode, blank lines, and final unterminated records', async () => {
    const events = [
      { type: 'decision', data: insight.decision },
      { type: 'result', data: { answer: 'Ship → film', insight } },
    ];
    const data = events.map((e) => JSON.stringify(e)).join('\n\n');
    const fetchMock = vi
      .fn()
      .mockResolvedValue(response([data.slice(0, 17), data.slice(17, 70), data.slice(70)]));
    vi.stubGlobal('fetch', fetchMock);
    const received = vi.fn();
    await sendChat(
      'hi',
      'AUTO',
      Array.from({ length: 22 }, (_, i) => ({
        id: String(i),
        role: 'user' as const,
        content: 'prior',
      })),
      new AbortController().signal,
      received,
    );
    expect(received).toHaveBeenCalledTimes(2);
    const body = JSON.parse(fetchMock.mock.calls[0][1].body);
    expect(body.history).toHaveLength(20);
    expect(body.override).toBeNull();
  });
  it('preserves an explicit override', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(
        response([JSON.stringify({ type: 'result', data: { answer: 'ok', insight } }) + '\n']),
      );
    vi.stubGlobal('fetch', fetchMock);
    await sendChat('hi', 'REST', [], new AbortController().signal, vi.fn(), 'jev');
    expect(JSON.parse(fetchMock.mock.calls[0][1].body).override).toBe('REST');
    expect(JSON.parse(fetchMock.mock.calls[0][1].body).provider).toBe('jev');
  });
  it.each([{ ok: false }, { ok: true, body: null }])(
    'rejects unavailable transport',
    async (value) => {
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue(value));
      await expect(
        sendChat('hi', 'AUTO', [], new AbortController().signal, vi.fn()),
      ).rejects.toThrow('could not accept');
    },
  );
  it.each([
    [
      JSON.stringify({ type: 'error', data: { message: 'Model unavailable' } }) + '\n',
      'Model unavailable',
    ],
    ['\n', 'Connection ended'],
    ['broken\n', 'JSON'],
  ])('rejects failed or truncated streams', async (data, error) => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response([data])));
    await expect(sendChat('hi', 'AUTO', [], new AbortController().signal, vi.fn())).rejects.toThrow(
      error,
    );
  });
});
