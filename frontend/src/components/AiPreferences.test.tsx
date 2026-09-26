import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, it, vi } from 'vitest';
import { AiPreferences } from './AiPreferences';
import { providers } from '@/test/fixtures';

it('switches providers and explains the hosted data flow', async () => {
  const user = userEvent.setup();
  const change = vi.fn();
  render(<AiPreferences provider="ollama" providers={providers} busy={false} onChange={change} />);
  await user.click(screen.getByRole('button', { name: 'AI preferences' }));
  expect(screen.getByRole('radio', { name: /Ollama/ })).toBeChecked();
  await user.click(screen.getByRole('radio', { name: /Jev/ }));
  expect(change).toHaveBeenCalledWith('jev');
  await user.click(screen.getByRole('radio', { name: /CLM/ }));
  expect(change).toHaveBeenCalledWith('clm');
  expect(screen.getByText('SELF-HOSTED')).toBeInTheDocument();
  expect(
    screen.getByText(/question and recent conversation are sent to TypeSafe/),
  ).toBeInTheDocument();
});
it('disables unavailable providers and freezes switching during a request', async () => {
  const user = userEvent.setup();
  const props = {
    provider: 'ollama' as const,
    providers: providers.map((p) => ({ ...p, available: p.id === 'ollama' })),
    busy: false,
    onChange: vi.fn(),
  };
  const { rerender } = render(<AiPreferences {...props} />);
  await user.click(screen.getByRole('button', { name: 'AI preferences' }));
  expect(screen.getByRole('radio', { name: /Jev/ })).toBeDisabled();
  expect(screen.getByRole('radio', { name: /CLM/ })).toBeDisabled();
  expect(screen.getByText(/configure its API key on the backend once/)).toBeInTheDocument();
  rerender(<AiPreferences {...props} providers={providers} busy />);
  expect(screen.getByRole('radio', { name: /CLM/ })).toBeDisabled();
  expect(screen.getByRole('radio', { name: /Ollama/ })).toBeDisabled();
  expect(screen.getByText(/Wait for the current response/)).toBeInTheDocument();
});
