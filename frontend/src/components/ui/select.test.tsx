import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, it, vi } from 'vitest';
import { useState } from 'react';
import { ChatInput } from '../chat/ChatInput';
import type { Override } from '@/types';

it('supports keyboard selection, Escape dismissal and focus restoration without submitting', async () => {
  const user = userEvent.setup();
  const submit = vi.fn();
  function Harness() {
    const [override, setOverride] = useState<Override>('AUTO');
    return (
      <ChatInput
        value="Falcon"
        onChange={vi.fn()}
        onSubmit={submit}
        busy={false}
        onCancel={vi.fn()}
        override={override}
        onOverride={setOverride}
      />
    );
  }
  render(<Harness />);
  const trigger = screen.getByRole('combobox', { name: 'Protocol for next question' });
  trigger.focus();
  await user.keyboard('{Enter}');
  expect(screen.getByRole('listbox')).toBeInTheDocument();
  expect(screen.getByRole('option', { name: /Auto/ })).toHaveAttribute('aria-selected', 'true');
  await user.keyboard('{End}{Enter}');
  expect(trigger).toHaveTextContent('Force gRPC');
  expect(trigger).toHaveFocus();
  expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
  await user.keyboard('{Enter}{Escape}');
  expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
  expect(trigger).toHaveFocus();
  expect(submit).not.toHaveBeenCalled();
});
