import { ArrowLeft, Database, Film, Rocket, Users } from 'lucide-react';
import { Button } from './ui/button';
export function CatalogGuide({ onClose }: { onClose: () => void }) {
  return (
    <section className="catalog-guide">
      <Button variant="ghost" onClick={onClose}>
        <ArrowLeft size={16} /> Back to conversation
      </Button>
      <span className="hero-emblem">
        <Database size={28} />
      </span>
      <div className="section-kicker">THE DEMO FIELD GUIDE</div>
      <h1>
        A small universe.
        <br />A world of connections.
      </h1>
      <p>
        One shared dataset powers every protocol. Ask for a resource, explore its relationships, or
        watch repeated snapshots.
      </p>
      <div className="catalog-grid">
        <div>
          <Rocket />
          <h3>19 starships</h3>
          <p>
            Millennium Falcon
            <br />
            X-wing · CR90 corvette · …
          </p>
        </div>
        <div>
          <Film />
          <h3>6 films</h3>
          <p>
            A New Hope · …
            <br />
            Revenge of the Sith
          </p>
        </div>
        <div>
          <Users />
          <h3>18 characters</h3>
          <p>
            Luke Skywalker · Han Solo
            <br />
            Leia Organa · Chewbacca
          </p>
        </div>
      </div>
      <div className="guide-note">
        <strong>Made for a live demo</strong>
        <p>
          Auto mode asks the configured model to choose a protocol. The input’s protocol selector
          overrides the next question only, without contacting a model. gRPC streams real catalog
          snapshots; vessel telemetry is not simulated as live data. Answers are composed from
          returned records.
        </p>
        <p>
          Original ship records are enriched with selected cast for all six films. Cast means
          characters, with appearances linked to each film. Verified Falcon crew covers A New Hope
          and the Battle of Endor; other flight history is not yet recorded. Cast membership does
          not imply piloting. Conversations stay in memory and disappear on refresh.
        </p>
      </div>
    </section>
  );
}
