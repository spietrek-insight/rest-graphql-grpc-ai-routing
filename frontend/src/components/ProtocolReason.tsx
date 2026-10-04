import type { Decision } from '@/types';

export function ProtocolReason({ decision }: { decision: Decision }) {
  return (
    <p className="protocol-reason">
      <strong>Why this protocol: </strong>
      {decision.source === 'Presenter override' && 'You selected it for this request. '}
      {decision.reason}
    </p>
  );
}
