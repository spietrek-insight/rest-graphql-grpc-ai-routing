import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ResponseDialog } from './ResponseDialog';
import { InsightPanel } from './InsightPanel';
import { MessageBubble } from './chat/MessageList';
import { insight } from '@/test/fixtures';

describe('response dialog', () => {
  it('opens from an answer, shows the full response, and restores focus on Escape', async () => {
    const user = userEvent.setup();
    render(
      <MessageBubble
        message={{ id: '1', role: 'assistant', content: 'A ship.', insight }}
        onInspect={vi.fn()}
      />,
    );
    const trigger = screen.getByRole('button', { name: 'View response' });
    await user.click(trigger);
    const dialog = screen.getByRole('dialog', { name: 'Response details' });
    expect(within(dialog).getByLabelText('Response body').textContent).toBe(
      JSON.stringify(insight.exchange.response, null, 2),
    );
    expect(within(dialog).getByText(insight.exchange.operation)).toBeInTheDocument();
    await user.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(trigger).toHaveFocus();
  });

  it('opens the response from the inspector even while the request tab is selected', async () => {
    const user = userEvent.setup();
    render(<InsightPanel insight={insight} decision={null} snapshots={[]} busy={false} />);
    await user.click(screen.getByRole('button', { name: 'Expand response' }));
    expect(screen.getByLabelText('Response body')).toHaveTextContent('Millennium Falcon');
    await user.click(screen.getByRole('button', { name: 'Copy response' }));
    expect(await navigator.clipboard.readText()).toBe(
      JSON.stringify(insight.exchange.response, null, 2),
    );
    expect(screen.getByRole('status')).toHaveTextContent('Response copied');
    await user.click(screen.getByRole('button', { name: 'Close response' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('keeps the response available when clipboard access fails and clears feedback on reopen', async () => {
    const user = userEvent.setup();
    render(
      <ResponseDialog insight={insight}>
        <button>Open response</button>
      </ResponseDialog>,
    );
    await user.click(screen.getByRole('button', { name: 'Open response' }));
    vi.spyOn(navigator.clipboard, 'writeText').mockRejectedValueOnce(new Error('denied'));
    await user.click(screen.getByRole('button', { name: 'Copy response' }));
    expect(screen.getByRole('status')).toHaveTextContent('Could not copy');
    expect(screen.getByLabelText('Response body')).toHaveTextContent('Millennium Falcon');
    await user.keyboard('{Escape}');
    await user.click(screen.getByRole('button', { name: 'Open response' }));
    expect(screen.getByRole('status')).toHaveTextContent('Esc to close');
  });
});
