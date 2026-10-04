import { Radio, RotateCcw, Maximize2 } from 'lucide-react';
import type { FlightPlayback } from '@/types';
import { Dialog, DialogContent, DialogDescription, DialogTitle, DialogTrigger } from '../ui/dialog';

export function FlightCard({
  playback,
  onReplay,
  onStop,
  presentation = false,
}: {
  playback: FlightPlayback;
  onReplay?: () => void;
  onStop?: () => void;
  presentation?: boolean;
}) {
  const snapshot = playback.snapshots.at(-1);
  const flight = snapshot?.flight;
  if (!flight) return null;
  const live = playback.state === 'streaming';
  const status = {
    streaming: 'Receiving updates automatically',
    complete: 'Flight complete',
    stopped: 'Stopped · last received state',
    interrupted: 'Connection interrupted · last received state',
  }[playback.state];
  return (
    <section className="flight-card" aria-label="Simulated Millennium Falcon flight">
      <div className="flight-title">
        <div>
          <span className="flight-label">
            <Radio size={14} /> SIMULATED FLIGHT
          </span>
          <h3>{flight.shipName}</h3>
        </div>
        <strong className="flight-phase">{flight.phase}</strong>
      </div>
      <p className="flight-fiction">
        Fictional presentation scenario · not live telemetry or film history
      </p>
      <div className="flight-route">
        <span>{flight.origin}</span>
        <span>{flight.progressPercent}%</span>
        <span>{flight.destination}</span>
      </div>
      <progress aria-label="Flight progress" value={flight.progressPercent} max={100} />
      <dl className="flight-readings">
        <div className="flight-location">
          <dt>Current location in this simulation</dt>
          <dd>{flight.location}</dd>
        </div>
        <div>
          <dt>Pilot at the controls</dt>
          <dd>{flight.pilot.name}</dd>
        </div>
        <div>
          <dt>Co-pilot</dt>
          <dd>{flight.coPilot.name}</dd>
        </div>
        <div>
          <dt>Sublight speed</dt>
          <dd>
            {flight.phase === 'Hyperspace'
              ? 'Not applicable'
              : `${flight.speedKph.toLocaleString()} km/h`}
          </dd>
        </div>
        <div>
          <dt>Shields</dt>
          <dd>{flight.shieldsPercent}%</dd>
        </div>
      </dl>
      <p
        className={`flight-event ${flight.phase === 'Alert' ? 'flight-alert' : ''}`}
        role="status"
        aria-live="polite"
      >
        {flight.event}
      </p>
      <div className="flight-status">
        <strong>{status}</strong>
        <span>
          {Math.round(flight.elapsedMs / 1000)} / {Math.round(flight.durationMs / 1000)} sec ·{' '}
          {snapshot.sequence}/{snapshot.totalSnapshots} updates
        </span>
      </div>
      <details className="flight-log">
        <summary>Flight log ({playback.snapshots.length} updates)</summary>
        <ol>
          {playback.snapshots.map((s) => (
            <li key={s.sequence}>
              <span>{Math.round((s.flight?.elapsedMs ?? 0) / 1000)}s</span>
              {s.flight?.event}
            </li>
          ))}
        </ol>
      </details>
      {!presentation && (
        <Dialog>
          <DialogTrigger asChild>
            <button className="inspect-link flight-present">
              <Maximize2 size={14} /> Present flight
            </button>
          </DialogTrigger>
          <DialogContent className="flight-presentation" closeLabel="Close flight presentation">
            <DialogTitle className="sr-only">Flight presentation</DialogTitle>
            <DialogDescription className="sr-only">
              An automatically updating fictional flight. Close to return to the conversation.
            </DialogDescription>
            <FlightCard playback={playback} onStop={onStop} presentation />
          </DialogContent>
        </Dialog>
      )}
      {presentation && live && onStop && (
        <button className="inspect-link" onClick={onStop}>
          Stop flight
        </button>
      )}
      {!live && onReplay && (
        <button className="inspect-link" onClick={onReplay}>
          <RotateCcw size={14} /> Replay simulated flight
        </button>
      )}
    </section>
  );
}
