import { expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { FlightCard } from './FlightCard';
import { flightSnapshot } from '@/test/fixtures';

it('shows changing flight state, progress, alerts, and the received log without a new request', () => {
  const { rerender } = render(
    <FlightCard playback={{ snapshots: [flightSnapshot], state: 'streaming' }} />,
  );
  expect(screen.getByText('Mos Eisley · docking bay')).toBeInTheDocument();
  expect(screen.getByText(/Fictional presentation scenario/)).toBeInTheDocument();
  expect(screen.getByRole('progressbar')).toHaveAttribute('value', '0');
  expect(screen.queryByText('Replay simulated flight')).not.toBeInTheDocument();
  const next = {
    ...flightSnapshot,
    sequence: 8,
    flight: {
      ...flightSnapshot.flight!,
      phase: 'Alert',
      location: 'Tatooine · departure lane',
      pilot: { id: '5', name: 'Chewbacca' },
      coPilot: { id: '2', name: 'Han Solo' },
      progressPercent: 35,
      shieldsPercent: 82,
      event: 'Debris ahead.',
      elapsedMs: 14000,
    },
  };
  rerender(<FlightCard playback={{ snapshots: [flightSnapshot, next], state: 'streaming' }} />);
  expect(screen.queryByText('Mos Eisley · docking bay')).not.toBeInTheDocument();
  expect(screen.getByText('Tatooine · departure lane')).toBeInTheDocument();
  expect(screen.getByRole('status')).toHaveClass('flight-alert');
  expect(screen.getByText('82%')).toBeInTheDocument();
  expect(screen.getByRole('progressbar')).toHaveAttribute('value', '35');
  expect(screen.getByText('Flight log (2 updates)')).toBeInTheDocument();
  expect(screen.getByText(/14 \/ 40 sec/)).toBeInTheDocument();
  rerender(
    <FlightCard
      playback={{
        snapshots: [{ ...next, flight: { ...next.flight, phase: 'Hyperspace' } }],
        state: 'streaming',
      }}
    />,
  );
  expect(screen.getByText('Not applicable')).toBeInTheDocument();
});

it.each(['complete', 'stopped', 'interrupted'] as const)(
  'retains %s state and lets the user replay',
  async (state) => {
    const replay = vi.fn();
    render(<FlightCard playback={{ snapshots: [flightSnapshot], state }} onReplay={replay} />);
    expect(screen.queryByText('Receiving updates automatically')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Replay simulated flight' }));
    expect(replay).toHaveBeenCalledOnce();
  },
);
it('omits flight UI for catalog-only updates', () => {
  const { container } = render(
    <FlightCard playback={{ snapshots: [{ sequence: 1 }], state: 'complete' }} />,
  );
  expect(container).toBeEmptyDOMElement();
});

it('keeps the presentation view updated and provides Stop inside it', async () => {
  const { within } = await import('@testing-library/react');
  const stop = vi.fn();
  const { rerender } = render(
    <FlightCard playback={{ snapshots: [flightSnapshot], state: 'streaming' }} onStop={stop} />,
  );
  await userEvent.click(screen.getByRole('button', { name: 'Present flight' }));
  const dialog = screen.getByRole('dialog', { name: 'Flight presentation' });
  expect(within(dialog).getByText('Mos Eisley · docking bay')).toBeInTheDocument();
  rerender(
    <FlightCard
      playback={{
        snapshots: [
          { ...flightSnapshot, flight: { ...flightSnapshot.flight!, location: 'Yavin 4' } },
        ],
        state: 'streaming',
      }}
      onStop={stop}
    />,
  );
  expect(within(dialog).queryByText('Mos Eisley · docking bay')).not.toBeInTheDocument();
  expect(within(dialog).getAllByText('Yavin 4')).toHaveLength(2);
  await userEvent.click(within(dialog).getByRole('button', { name: 'Stop flight' }));
  expect(stop).toHaveBeenCalledOnce();
  await userEvent.click(within(dialog).getByRole('button', { name: 'Close flight presentation' }));
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
});
