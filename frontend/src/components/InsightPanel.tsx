import { useState } from 'react';
import {
  ArrowDown,
  ArrowRight,
  Braces,
  Check,
  Copy,
  FileJson,
  Maximize2,
  Network,
  ScanLine,
} from 'lucide-react';
import type { Decision, Insight } from '@/types';
import { ProtocolBadge } from './ProtocolBadge';
import { protocolLabel } from '@/lib/protocols';
import { Button } from './ui/button';
import { ResponseDialog } from './ResponseDialog';
export function InsightPanel({
  insight,
  decision,
  snapshots,
  busy,
}: {
  insight: Insight | null;
  decision: Decision | null;
  snapshots: unknown[];
  busy: boolean;
}) {
  const [tab, setTab] = useState<'request' | 'response' | 'classification'>('request');
  const [copied, setCopied] = useState(false);
  const active = insight?.decision ?? decision;
  const payload = insight
    ? tab === 'classification'
      ? insight.decision.classification
      : insight.exchange[tab]
    : snapshots;
  async function copy() {
    try {
      await navigator.clipboard.writeText(JSON.stringify(payload, null, 2));
      setCopied(true);
    } catch {
      setCopied(false);
    }
  }
  return (
    <aside className="insight-panel">
      <div className="insight-header">
        <span>
          <ScanLine size={17} /> Protocol insight
        </span>
        <span className="live-label">
          <span className={`tiny-dot ${busy ? 'green' : 'gray'}`} />
          {busy ? 'LIVE' : 'INSPECTOR'}
        </span>
      </div>
      <div className="insight-content">
        <div className="section-kicker">UNDER THE HOOD</div>
        <h2>
          See the decision.
          <br />
          Follow the request.
        </h2>
        <p className="insight-intro">
          Every answer has a route. Here’s exactly how yours gets there.
        </p>
        <div className="route-diagram">
          <div>
            <span className="route-node">
              <Braces size={18} />
            </span>
            <span>Your question</span>
          </div>
          <ArrowDown size={15} />
          <div className="route-ai">
            <span>✧</span>
            <div>
              Intent classifier<small>{active?.source ?? 'Local model · tool selection'}</small>
            </div>
          </div>
          <div className="route-branches">
            <span />
            <span />
            <span />
          </div>
          <div className="route-protocols">
            {(['REST', 'GRAPHQL', 'GRPC'] as const).map((p) => (
              <div key={p} className={active?.protocol === p ? 'route-selected' : ''}>
                <ProtocolBadge protocol={p} />
              </div>
            ))}
          </div>
        </div>
        {active ? (
          <>
            <div className="decision-card">
              <div className="section-kicker">
                {active.source === 'Presenter override' ? 'PRESENTER SELECTED' : 'MODEL SELECTED'}{' '}
                <ProtocolBadge protocol={active.protocol} />
              </div>
              <p>{active.reason}</p>
            </div>
            {insight && (
              <>
                <div className="metrics">
                  <div>
                    <span>ROUTING TIME</span>
                    <strong>
                      {insight.timings?.routingMs ?? '—'}
                      <small> ms</small>
                    </strong>
                  </div>
                  <div>
                    <span>TOTAL SERVER TIME</span>
                    <strong>
                      {insight.timings?.totalMs ?? '—'}
                      <small> ms</small>
                    </strong>
                  </div>
                </div>
                <div className="metrics">
                  <div>
                    <span>PROTOCOL TIME</span>
                    <strong>
                      {insight.exchange.durationMs}
                      <small> ms</small>
                    </strong>
                  </div>
                  <div>
                    <span>RESPONSE BODY</span>
                    <strong>
                      {insight.exchange.responseBytes}
                      <small> B</small>
                    </strong>
                  </div>
                </div>
                <div className="operation">
                  <ArrowRight size={13} />
                  <code>{insight.exchange.operation}</code>
                </div>
              </>
            )}
            <div className="payload-tabs" role="tablist" aria-label="Captured traffic">
              {(['request', 'response', 'classification'] as const).map((t) => (
                <button
                  role="tab"
                  aria-selected={tab === t}
                  key={t}
                  onClick={() => {
                    setTab(t);
                    setCopied(false);
                  }}
                >
                  {t === 'classification' ? 'Decision' : t}
                </button>
              ))}
              <Button variant="ghost" size="icon" onClick={copy} aria-label="Copy payload">
                {copied ? <Check size={14} /> : <Copy size={14} />}
              </Button>
            </div>
            <pre className="payload">
              <code>{JSON.stringify(payload, null, 2)}</code>
            </pre>
            {insight && (
              <ResponseDialog insight={insight}>
                <Button className="expand-response" variant="outline" size="sm">
                  <Maximize2 size={14} /> Expand response
                </Button>
              </ResponseDialog>
            )}
            <p className="payload-caption">
              {insight?.exchange.encoding ?? `${snapshots.length} snapshots received`}
            </p>
          </>
        ) : (
          <div className="insight-empty">
            <span className="empty-code">
              <FileJson size={24} />
            </span>
            <strong>A little transparency goes a long way.</strong>
            <p>
              Send a question to see the selected protocol, routing rationale, and actual request
              and response.
            </p>
            <span className="waiting-pill">
              <span className="tiny-dot gray" /> Waiting for your first request
            </span>
          </div>
        )}
        <div className="insight-foot">
          <Network size={15} />
          <span>
            {active
              ? `${protocolLabel[active.protocol]} → shared Java domain service`
              : 'One dataset. Three real protocol surfaces.'}
          </span>
        </div>
      </div>
    </aside>
  );
}
