import {
  ArrowUpRight,
  BookOpen,
  Boxes,
  Braces,
  ChevronRight,
  CircleHelp,
  MessageSquare,
  Plus,
  Radio,
  Sparkles,
} from 'lucide-react';
import { Button } from './ui/button';
import type { Status } from '@/types';
export function Sidebar({
  onReset,
  onGuide,
  onCatalog,
  status,
}: {
  onReset: () => void;
  onGuide: () => void;
  onCatalog: () => void;
  status: Status | null;
}) {
  return (
    <aside className="sidebar">
      <a href="/" className="brand">
        <span className="brand-symbol">
          <Braces size={23} />
        </span>
        <span>
          protocol<span className="brand-light">studio</span>
          <small>THE API EXPLORATION LAB</small>
        </span>
      </a>
      <Button variant="outline" className="new-session" onClick={onReset}>
        <Plus size={16} /> New conversation <span className="shortcut">↵</span>
      </Button>
      <div className="nav-label">WORKSPACE</div>
      <nav aria-label="Workspace">
        <button className="nav-item active" onClick={onGuide}>
          <MessageSquare size={17} /> Conversation <span className="nav-count">1</span>
        </button>
        <button className="nav-item" onClick={onCatalog}>
          <Boxes size={17} /> Explore the dataset <ChevronRight size={14} />
        </button>
      </nav>
      <div className="sidebar-section">
        <div className="nav-label">THREE PROTOCOLS. ONE CONVERSATION.</div>
        <div className="protocol-nav">
          <span className="protocol-icon rest">
            <ArrowUpRight size={16} />
          </span>
          <div>
            REST<small>Simple, resource-focused</small>
          </div>
          <span className="tiny-dot blue" />
        </div>
        <div className="protocol-nav">
          <span className="protocol-icon graphql">
            <Braces size={16} />
          </span>
          <div>
            GraphQL<small>Ask for exactly what you need</small>
          </div>
          <span className="tiny-dot pink" />
        </div>
        <div className="protocol-nav">
          <span className="protocol-icon grpc">
            <Radio size={16} />
          </span>
          <div>
            gRPC<small>Repeated, typed exchanges</small>
          </div>
          <span className="tiny-dot orange" />
        </div>
      </div>
      <div className="sidebar-bottom">
        <div className="local-card">
          <span className="local-symbol">
            <Sparkles size={16} />
          </span>
          <strong>{status?.local === false ? 'Jev classification' : 'Local-first AI'}</strong>
          <p>
            {status?.local === false
              ? 'Structured decisions with TypeSafe. Requests use the hosted API.'
              : 'Your questions. Your machine. No cloud API key needed.'}
          </p>
          <span className="local-model">
            <span className={`tiny-dot ${status ? 'green' : 'gray'}`} />
            {status ? status.model : 'Backend disconnected'}
          </span>
        </div>
        <button className="help-link" onClick={onCatalog}>
          <BookOpen size={15} /> Demo field guide <CircleHelp size={14} />
        </button>
        <div className="sidebar-footer">
          PROTOCOL STUDIO <span>v1.0</span>
        </div>
      </div>
    </aside>
  );
}
