import { beforeEach, expect, it } from 'vitest';
import { usePreferencesStore } from './usePreferencesStore';
import { useChatStore } from './useChatStore';

beforeEach(() => {
  localStorage.clear();
  usePreferencesStore.setState({ provider: null });
});
it.each(['ollama', 'jev', 'clm'] as const)(
  'persists %s preference across resets and reloads',
  async (provider) => {
    usePreferencesStore.getState().setProvider(provider);
    useChatStore.getState().append({ id: '1', role: 'user', content: 'private conversation' });
    useChatStore.getState().reset();
    expect(usePreferencesStore.getState().provider).toBe(provider);
    const saved = localStorage.getItem('protocol-studio-preferences')!;
    expect(JSON.parse(saved).state).toEqual({ provider });
    usePreferencesStore.setState({ provider: null });
    localStorage.setItem('protocol-studio-preferences', saved);
    await usePreferencesStore.persist.rehydrate();
    expect(usePreferencesStore.getState().provider).toBe(provider);
  },
);
it('ignores invalid persisted provider values', async () => {
  localStorage.setItem(
    'protocol-studio-preferences',
    JSON.stringify({ state: { provider: 'unknown' }, version: 0 }),
  );
  await usePreferencesStore.persist.rehydrate();
  expect(usePreferencesStore.getState().provider).toBeNull();
});
