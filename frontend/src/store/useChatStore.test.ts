import { beforeEach, it, expect } from 'vitest';
import { useChatStore } from './useChatStore';
import { insight } from '@/test/fixtures';
beforeEach(() => useChatStore.getState().reset());
it('owns ephemeral conversation, protocol and session state independently', () => {
  const s = useChatStore.getState();
  const id = s.sessionId;
  s.append({ id: '1', role: 'user', content: 'hello' });
  s.setError('retry');
  s.setOverride('GRPC');
  s.setDecision(insight.decision);
  s.selectInsight(insight);
  s.addSnapshot({ sequence: 1 });
  expect(useChatStore.getState().messages).toHaveLength(1);
  expect(useChatStore.getState().snapshots).toHaveLength(1);
  s.setBusy(true);
  expect(useChatStore.getState().error).toBeNull();
  expect(useChatStore.getState().snapshots).toEqual([]);
  s.setBusy(false);
  s.reset();
  expect(useChatStore.getState()).toMatchObject({
    messages: [],
    busy: false,
    override: 'AUTO',
    selectedInsight: null,
    decision: null,
  });
  expect(useChatStore.getState().sessionId).not.toBe(id);
});
