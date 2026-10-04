import { beforeEach, it, expect, vi } from 'vitest';
import { render, screen, waitFor, act, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ChatPage } from './ChatPage';
import { useChatStore } from '@/store/useChatStore';
import { getStatus, sendChat } from '@/api/chat';
import { insight, providers } from '@/test/fixtures';
import { usePreferencesStore } from '@/store/usePreferencesStore';
vi.mock('@/api/chat', () => ({ getStatus: vi.fn(), sendChat: vi.fn() }));
beforeEach(() => {
  vi.resetAllMocks();
  useChatStore.getState().reset();
  usePreferencesStore.setState({ provider: null });
  vi.mocked(getStatus).mockResolvedValue({
    service: 'ready',
    provider: 'ollama',
    model: 'qwen3:4b',
    local: true,
    providers,
  });
});
it('sends prompts, consumes next-question override, renders answer and resets session', async () => {
  vi.mocked(sendChat).mockImplementation(async (_m, _o, _h, _s, event) => {
    event({ type: 'decision', data: insight.decision });
    event({ type: 'snapshot', data: { sequence: 1 } });
    event({ type: 'result', data: { answer: 'The Millennium Falcon is a freighter.', insight } });
  });
  render(<ChatPage />);
  await screen.findByText('Backend connected');
  await userEvent.click(screen.getByRole('combobox'));
  await userEvent.click(screen.getByRole('option', { name: /Force REST/ }));
  await userEvent.click(screen.getByText('Meet the Millennium Falcon'));
  expect(await screen.findByText('The Millennium Falcon is a freighter.')).toBeInTheDocument();
  expect(vi.mocked(sendChat).mock.calls[0][1]).toBe('REST');
  expect(screen.getByRole('combobox')).toHaveTextContent('Auto · AI chooses');
  await userEvent.click(screen.getByText('Inspect this request'));
  await userEvent.click(screen.getByRole('button', { name: 'Hide protocol insight' }));
  expect(screen.queryByText('Protocol insight')).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: 'Show protocol insight' }));
  await userEvent.click(screen.getByText('New conversation'));
  expect(screen.getByText('Discover what connects.')).toBeInTheDocument();
});
it('opens guide and returns through either navigation control', async () => {
  render(<ChatPage />);
  await userEvent.click(screen.getByText('Explore the dataset'));
  expect(screen.getByText('19 starships')).toBeInTheDocument();
  await userEvent.click(screen.getByText('Back to conversation'));
  await userEvent.click(screen.getByText('Demo field guide'));
  await userEvent.click(screen.getByText('Conversation'));
  expect(screen.getByText('Discover what connects.')).toBeInTheDocument();
});
it('reports errors, retries and handles backend unavailability', async () => {
  vi.mocked(getStatus).mockRejectedValue(new Error());
  vi.mocked(sendChat)
    .mockRejectedValueOnce(new Error('Model is offline'))
    .mockRejectedValueOnce('failed');
  render(<ChatPage />);
  await userEvent.type(screen.getByRole('textbox'), 'hello');
  await userEvent.click(screen.getByRole('button', { name: 'Send question' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Model is offline');
  await userEvent.click(screen.getByText('Retry'));
  expect(await screen.findByRole('alert')).toHaveTextContent('Something went wrong');
  expect(screen.getByText('Backend unavailable')).toBeInTheDocument();
});
it('cancels in-flight work and ignores late events after reset', async () => {
  let resolve!: () => void;
  let emit!: Parameters<typeof sendChat>[4];
  vi.mocked(sendChat).mockImplementation((_m, _o, _h, _s, events) => {
    emit = events;
    return new Promise<void>((r) => {
      resolve = r;
    });
  });
  render(<ChatPage />);
  await userEvent.click(screen.getByText('Track the Millennium Falcon'));
  await userEvent.click(screen.getByRole('button', { name: 'Stop response' }));
  expect(vi.mocked(sendChat).mock.calls[0][3].aborted).toBe(true);
  await userEvent.click(screen.getByText('New conversation'));
  act(() => {
    emit({ type: 'result', data: { answer: 'late', insight } });
    resolve();
  });
  await waitFor(() => expect(useChatStore.getState().busy).toBe(false));
  expect(screen.queryByText('late')).not.toBeInTheDocument();
});
it('ignores empty submissions, prevents duplicate requests and aborts on unmount', async () => {
  vi.mocked(sendChat).mockImplementation(() => new Promise(() => {}));
  const { unmount } = render(<ChatPage />);
  fireEvent.submit(screen.getByRole('textbox').closest('form')!);
  expect(sendChat).not.toHaveBeenCalled();
  await userEvent.click(screen.getByText('Track the Millennium Falcon'));
  fireEvent.submit(screen.getByRole('textbox').closest('form')!);
  expect(sendChat).toHaveBeenCalledOnce();
  unmount();
  expect(vi.mocked(sendChat).mock.calls[0][3].aborted).toBe(true);
});
it.each(['jev', 'clm'] as const)(
  'sends %s per question and keeps it across new conversations',
  async (provider) => {
    const user = userEvent.setup();
    vi.mocked(sendChat).mockImplementation(async (_m, _o, _h, _s, event) => {
      event({ type: 'result', data: { answer: 'A catalog answer.', insight } });
    });
    render(<ChatPage />);
    await screen.findByText('Backend connected');
    await user.click(screen.getByRole('button', { name: 'AI preferences' }));
    await user.click(screen.getByRole('radio', { name: new RegExp(provider, 'i') }));
    await user.click(screen.getByRole('button', { name: 'Close preferences' }));
    await user.click(screen.getByText('Meet the Millennium Falcon'));
    await screen.findByText('A catalog answer.');
    expect(vi.mocked(sendChat).mock.calls[0][5]).toBe(provider);
    await user.click(screen.getByText('New conversation'));
    expect(usePreferencesStore.getState().provider).toBe(provider);
    await user.click(screen.getByRole('button', { name: 'AI preferences' }));
    await user.click(screen.getByRole('radio', { name: /Ollama/ }));
    await user.keyboard('{Escape}');
    await user.click(screen.getByText('Meet the Millennium Falcon'));
    expect(vi.mocked(sendChat).mock.calls[1][5]).toBe('ollama');
  },
);
it.each(['jev', 'clm'] as const)(
  'blocks unavailable %s without switching providers',
  async (provider) => {
    usePreferencesStore.getState().setProvider(provider);
    vi.mocked(getStatus).mockResolvedValue({
      service: 'ready',
      provider: 'ollama',
      model: 'local',
      local: true,
      providers: providers.map((p) => ({ ...p, available: p.id === 'ollama' })),
    });
    render(<ChatPage />);
    await screen.findByText('Backend connected');
    await userEvent.click(screen.getByText('Meet the Millennium Falcon'));
    expect(screen.getByRole('alert')).toHaveTextContent('selected AI provider is not configured');
    expect(sendChat).not.toHaveBeenCalled();
    expect(usePreferencesStore.getState().provider).toBe(provider);
  },
);

it('honors CLM as the server default and displays its own description', async () => {
  vi.mocked(getStatus).mockResolvedValue({
    service: 'ready',
    provider: 'clm',
    model: 'clm-latest',
    local: false,
    providers,
  });
  vi.mocked(sendChat).mockResolvedValue();
  render(<ChatPage />);
  await screen.findByText('CLM classification');
  expect(
    screen.getByText('Structured decisions on your configured CLM server.'),
  ).toBeInTheDocument();
  expect(screen.queryByText('Jev classification')).not.toBeInTheDocument();
  await userEvent.click(screen.getByText('Meet the Millennium Falcon'));
  expect(vi.mocked(sendChat).mock.calls[0][5]).toBe('clm');
});

it('updates one flight card while streaming, preserves stopped state, and ignores late updates', async () => {
  const { flightSnapshot } = await import('@/test/fixtures');
  let emit!: Parameters<typeof sendChat>[4];
  let resolve!: () => void;
  vi.mocked(sendChat).mockImplementation((_m, _o, _h, _s, events) => {
    emit = events;
    return new Promise<void>((r) => {
      resolve = r;
    });
  });
  render(<ChatPage />);
  await userEvent.click(screen.getByText('Track the Millennium Falcon'));
  act(() => {
    emit({ type: 'decision', data: { ...insight.decision, protocol: 'GRPC' } });
    emit({ type: 'snapshot', data: flightSnapshot });
    emit({
      type: 'snapshot',
      data: {
        ...flightSnapshot,
        sequence: 2,
        flight: { ...flightSnapshot.flight!, location: 'Tatooine orbit' },
      },
    });
  });
  expect(screen.getByText('Tatooine orbit')).toBeInTheDocument();
  expect(useChatStore.getState().messages.filter((m) => m.role === 'assistant')).toHaveLength(1);
  expect(sendChat).toHaveBeenCalledOnce();
  await userEvent.click(screen.getByRole('button', { name: 'Stop response' }));
  expect(screen.getByText('Stopped · last received state')).toBeInTheDocument();
  act(() => {
    emit({ type: 'snapshot', data: flightSnapshot });
    resolve();
  });
  await waitFor(() => expect(useChatStore.getState().busy).toBe(false));
  expect(screen.getByText('Tatooine orbit')).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: 'Replay simulated flight' }));
  expect(sendChat).toHaveBeenCalledTimes(2);
  act(() => {
    emit({ type: 'snapshot', data: flightSnapshot });
    emit({ type: 'result', data: { answer: 'Simulated flight complete.', insight } });
    resolve();
  });
  await screen.findByText('Flight complete');
  expect(
    screen.getAllByRole('region', { name: 'Simulated Millennium Falcon flight' }),
  ).toHaveLength(2);
});

it('retains the last flight update when the connection fails', async () => {
  const { flightSnapshot } = await import('@/test/fixtures');
  vi.mocked(sendChat).mockImplementation(async (_m, _o, _h, _s, emit) => {
    emit({ type: 'snapshot', data: flightSnapshot });
    throw new Error('Connection ended before the answer arrived.');
  });
  render(<ChatPage />);
  await userEvent.click(screen.getByText('Track the Millennium Falcon'));
  expect(
    await screen.findByText('Connection interrupted · last received state'),
  ).toBeInTheDocument();
  expect(screen.getByText('Mos Eisley · docking bay')).toBeInTheDocument();
});
