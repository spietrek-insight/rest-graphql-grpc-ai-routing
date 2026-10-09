import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ChatInput } from './chat/ChatInput';
import { MessageList, MessageBubble } from './chat/MessageList';
import { PromptSuggestions } from './chat/PromptSuggestions';
import { Sidebar } from './Sidebar';
import { InsightPanel } from './InsightPanel';
import { CatalogGuide } from './CatalogGuide';
import { Button } from './ui/button';
import { flightSnapshot, insight, providers } from '@/test/fixtures';
describe('presentational components', () => {
  it('lists each gRPC message with its size, timing and change, live and after completion', async () => {
    const user = userEvent.setup();
    const frame = (sequence: number, location: string, pilot: string, phase = 'Cruise') => ({
      sequence,
      flight: {
        ...flightSnapshot.flight!,
        location,
        phase,
        pilot: { id: pilot, name: pilot },
        event: `event ${sequence}`,
      },
    });
    const frames = [
      frame(1, 'Mos Eisley', 'Han Solo'),
      frame(2, 'Tatooine orbit', 'Han Solo'),
      frame(3, 'Tatooine orbit', 'Chewbacca'),
      frame(4, 'Tatooine orbit', 'Chewbacca'),
    ];
    const stream = frames.map((f, i) => ({
      sequence: f.sequence,
      bytes: i ? 120 : 900,
      elapsedMs: i * 2000,
    }));
    const decision = { ...insight.decision, protocol: 'GRPC' as const };
    const { rerender } = render(
      <InsightPanel
        insight={null}
        decision={decision}
        snapshots={frames.slice(0, 2)}
        wire={stream.slice(0, 2)}
        busy
      />,
    );
    expect(screen.getByText(/2 received so far · 1,020 B/)).toBeInTheDocument();
    rerender(
      <InsightPanel
        insight={{
          ...insight,
          decision,
          exchange: { ...insight.exchange, response: frames, responseBytes: 1260, stream },
        }}
        decision={null}
        snapshots={[]}
        busy={false}
      />,
    );
    expect(screen.getByText(/4 received · 1,260 B/)).toBeInTheDocument();
    expect(screen.getByText('Ship record and flight start · Mos Eisley')).toBeInTheDocument();
    expect(screen.getByText('Tatooine orbit')).toBeInTheDocument();
    expect(screen.getByText('pilot → Chewbacca')).toBeInTheDocument();
    expect(screen.getByText('event 4')).toBeInTheDocument();
    expect(screen.getByText('+6.0 s')).toBeInTheDocument();
    await user.click(screen.getByText('#2'));
    expect(screen.getAllByText(/"location": "Tatooine orbit"/).length).toBeGreaterThan(0);
    rerender(
      <InsightPanel
        insight={{
          ...insight,
          exchange: { ...insight.exchange, response: [{ sequence: 1 }], stream: [stream[0]] },
        }}
        decision={null}
        snapshots={[]}
        busy={false}
      />,
    );
    expect(screen.getByText('Catalog snapshot')).toBeInTheDocument();
  });

  it('handles input, Enter, Shift+Enter, submit, stop and override callbacks', async () => {
    const props = {
      value: 'hello',
      onChange: vi.fn(),
      onSubmit: vi.fn(),
      busy: false,
      onCancel: vi.fn(),
      override: 'AUTO' as const,
      onOverride: vi.fn(),
    };
    const { rerender } = render(<ChatInput {...props} />);
    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'new' } });
    expect(props.onChange).toHaveBeenCalledWith('new');
    fireEvent.keyDown(screen.getByRole('textbox'), { key: 'Enter', shiftKey: true });
    expect(props.onSubmit).not.toHaveBeenCalled();
    fireEvent.keyDown(screen.getByRole('textbox'), { key: 'Enter' });
    expect(props.onSubmit).toHaveBeenCalledOnce();
    await userEvent.click(screen.getByRole('button', { name: 'Send question' }));
    expect(props.onSubmit).toHaveBeenCalledTimes(2);
    await userEvent.click(screen.getByRole('combobox'));
    await userEvent.click(screen.getByRole('option', { name: /Force gRPC/ }));
    expect(props.onOverride).toHaveBeenCalledWith('GRPC');
    rerender(<ChatInput {...props} busy value={'a'.repeat(1801)} />);
    expect(screen.getByRole('combobox')).toBeDisabled();
    expect(screen.getByText('1801/2000')).toBeInTheDocument();
    fireEvent.keyDown(screen.getByRole('textbox'), { key: 'Enter' });
    expect(props.onSubmit).toHaveBeenCalledTimes(2);
    await userEvent.click(screen.getByRole('button', { name: 'Stop response' }));
    expect(props.onCancel).toHaveBeenCalled();
    rerender(<ChatInput {...props} value=" " />);
    expect(screen.getByRole('button', { name: 'Send question' })).toBeDisabled();
    fireEvent.keyDown(screen.getByRole('textbox'), { key: 'Enter' });
    expect(props.onSubmit).toHaveBeenCalledTimes(2);
  });
  it('emits guided prompts and renders message insight controls', async () => {
    const pick = vi.fn();
    render(<PromptSuggestions onPick={pick} />);
    await userEvent.click(screen.getByText('Track the Millennium Falcon'));
    expect(pick).toHaveBeenCalledWith('Track the Millennium Falcon');
    const inspect = vi.fn();
    render(
      <MessageBubble
        message={{ id: '1', role: 'assistant', content: 'answer', insight }}
        onInspect={inspect}
      />,
    );
    await userEvent.click(screen.getByText('Inspect this request'));
    expect(inspect).toHaveBeenCalledWith(insight);
  });
  it('renders assistant-ui scrolling and streaming states', () => {
    const { rerender } = render(
      <MessageList
        messages={[{ id: '1', role: 'user', content: 'hello' }]}
        busy
        snapshots={0}
        onInspect={vi.fn()}
        onSend={vi.fn()}
      />,
    );
    expect(screen.getByText('Finding the right route…')).toBeInTheDocument();
    rerender(<MessageList messages={[]} busy snapshots={2} onInspect={vi.fn()} onSend={vi.fn()} />);
    expect(screen.getByText(/Receiving updates · 2/)).toBeInTheDocument();
  });
  it('renders guide, navigation, model configuration and slot buttons', async () => {
    const reset = vi.fn(),
      guide = vi.fn(),
      catalog = vi.fn();
    const { rerender } = render(
      <Sidebar onReset={reset} onGuide={guide} onCatalog={catalog} status={null} />,
    );
    expect(screen.getByText('Backend disconnected')).toBeInTheDocument();
    await userEvent.click(screen.getByText('New conversation'));
    await userEvent.click(screen.getByText('Conversation'));
    await userEvent.click(screen.getByText('Explore the dataset'));
    await userEvent.click(screen.getByText('Demo field guide'));
    expect(reset).toHaveBeenCalled();
    expect(guide).toHaveBeenCalled();
    expect(catalog).toHaveBeenCalledTimes(2);
    rerender(
      <Sidebar
        onReset={reset}
        onGuide={guide}
        onCatalog={catalog}
        status={{ service: 'ready', provider: 'jev', model: 'jev-latest', local: false, providers }}
      />,
    );
    expect(screen.getByText('Jev classification')).toBeInTheDocument();
    render(<CatalogGuide onClose={guide} />);
    await userEvent.click(screen.getByText('Back to conversation'));
    render(
      <Button asChild>
        <a href="#test">Link button</a>
      </Button>,
    );
    expect(screen.getByRole('link', { name: 'Link button' })).toHaveAttribute('href', '#test');
  });
  it('shows empty, live, captured and copied insight states', async () => {
    const user = userEvent.setup();
    const { rerender } = render(
      <InsightPanel insight={null} decision={null} snapshots={[]} busy={false} />,
    );
    expect(screen.getByText('Waiting for your first request')).toBeInTheDocument();
    rerender(
      <InsightPanel
        insight={null}
        decision={{ ...insight.decision, source: 'Ollama' }}
        snapshots={[{ sequence: 1 }]}
        busy
      />,
    );
    expect(screen.getByText('LIVE')).toBeInTheDocument();
    expect(screen.getByText('1 snapshots received')).toBeInTheDocument();
    rerender(
      <InsightPanel
        insight={{
          ...insight,
          exchange: {
            ...insight.exchange,
            operation: 'query ShipsInFilm',
            responseBytes: 180,
            comparison: {
              calls: ['GET /api/catalog/films/1', 'GET /api/catalog/starships/film:1'],
              responseBytes: 9400,
              durationMs: 4,
            },
          },
        }}
        decision={null}
        snapshots={[]}
        busy={false}
      />,
    );
    expect(screen.getByText('SAME DATA OVER REST')).toBeInTheDocument();
    expect(screen.getByText('GET /api/catalog/starships/film:1')).toBeInTheDocument();
    expect(screen.getByText(/requests · 9,400 B/)).toBeInTheDocument();
    rerender(<InsightPanel insight={insight} decision={null} snapshots={[]} busy={false} />);
    expect(screen.queryByText('SAME DATA OVER REST')).not.toBeInTheDocument();
    await user.click(screen.getByRole('tab', { name: 'response' }));
    expect(screen.getByText(/Millennium Falcon/)).toBeInTheDocument();
    await user.click(screen.getByRole('tab', { name: 'Decision' }));
    expect(screen.getByRole('tab', { name: 'Decision' })).toHaveAttribute('aria-selected', 'true');
    await user.click(screen.getByRole('button', { name: 'Copy payload' }));
    expect(await navigator.clipboard.readText()).toContain('override');
    vi.spyOn(navigator.clipboard, 'writeText').mockRejectedValueOnce(new Error('denied'));
    await user.click(screen.getByRole('button', { name: 'Copy payload' }));
    await user.click(screen.getByRole('tab', { name: 'request' }));
    expect(screen.getByText(/GET/, { selector: 'pre code' })).toBeInTheDocument();
  });
});
