import { useState, type ReactElement } from 'react';
import { Check, Copy, FileJson } from 'lucide-react';
import type { Insight } from '@/types';
import { ProtocolBadge } from './ProtocolBadge';
import { Button } from './ui/button';
import { Dialog, DialogContent, DialogDescription, DialogTitle, DialogTrigger } from './ui/dialog';

export function ResponseDialog({
  insight,
  children,
}: {
  insight: Insight;
  children: ReactElement;
}) {
  const [copyStatus, setCopyStatus] = useState<'idle' | 'copied' | 'error'>('idle');
  const response = JSON.stringify(insight.exchange.response, null, 2);
  async function copyResponse() {
    try {
      await navigator.clipboard.writeText(response);
      setCopyStatus('copied');
    } catch {
      setCopyStatus('error');
    }
  }
  return (
    <Dialog onOpenChange={() => setCopyStatus('idle')}>
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent>
        <header className="response-dialog-header">
          <div className="response-dialog-eyebrow">
            <FileJson size={16} /> CAPTURED RESPONSE
          </div>
          <DialogTitle>Response details</DialogTitle>
          <DialogDescription>{insight.exchange.operation}</DialogDescription>
        </header>
        <div className="response-dialog-toolbar">
          <div>
            <ProtocolBadge protocol={insight.decision.protocol} />
            <span>
              {insight.exchange.encoding} · {insight.exchange.responseBytes.toLocaleString()} B
            </span>
          </div>
          <Button variant="outline" size="sm" onClick={copyResponse}>
            {copyStatus === 'copied' ? <Check size={14} /> : <Copy size={14} />}
            {copyStatus === 'copied' ? 'Copied' : 'Copy response'}
          </Button>
        </div>
        <pre className="response-dialog-payload" tabIndex={0} aria-label="Response body">
          <code>{response}</code>
        </pre>
        <footer className="response-dialog-footer">
          <span>The full response returned by this request.</span>
          <span role="status">
            {copyStatus === 'error'
              ? 'Could not copy. Select the response text to copy it manually.'
              : copyStatus === 'copied'
                ? 'Response copied to clipboard.'
                : 'Esc to close'}
          </span>
        </footer>
      </DialogContent>
    </Dialog>
  );
}
