import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { AiProvider } from '@/types';

interface Preferences {
  provider: AiProvider | null;
  setProvider: (provider: AiProvider) => void;
}

export const usePreferencesStore = create<Preferences>()(
  persist((set) => ({ provider: null, setProvider: (provider) => set({ provider }) }), {
    name: 'protocol-studio-preferences',
    partialize: ({ provider }) => ({ provider }),
    merge: (saved, current) => {
      const provider = (saved as Partial<Preferences> | null)?.provider;
      return {
        ...current,
        provider:
          provider === 'ollama' || provider === 'jev' || provider === 'clm' ? provider : null,
      };
    },
  }),
);
