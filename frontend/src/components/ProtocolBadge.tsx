import type { Protocol } from '@/types';
import { protocolLabel } from '@/lib/protocols';
export function ProtocolBadge({ protocol }: { protocol: Protocol }) {
  return (
    <span className={`protocol-badge protocol-${protocol.toLowerCase()}`}>
      <span className="badge-dot" />
      {protocolLabel[protocol]}
    </span>
  );
}
