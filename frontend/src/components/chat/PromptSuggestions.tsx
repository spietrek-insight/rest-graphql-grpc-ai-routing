import { ArrowUpRight, Braces, Radio } from 'lucide-react';
import { ProtocolBadge } from '../ProtocolBadge';
const prompts = [
  {
    protocol: 'REST' as const,
    title: 'Meet the Millennium Falcon',
    text: 'Tell me about the Millennium Falcon',
    detail: 'One starship. One resource.',
    icon: ArrowUpRight,
  },
  {
    protocol: 'GRAPHQL' as const,
    title: 'Connect the story',
    text: 'Which starships appear in A New Hope, and who flew them?',
    detail: 'Ships and their pilots. One query, only what you ask for.',
    icon: Braces,
  },
  {
    protocol: 'GRPC' as const,
    title: 'Track the Millennium Falcon',
    text: 'Track the Millennium Falcon',
    detail: 'One request. A 40-second simulated flight.',
    icon: Radio,
  },
];
export function PromptSuggestions({
  onPick,
  disabled = false,
}: {
  onPick: (text: string) => void;
  disabled?: boolean;
}) {
  return (
    <div className="prompt-grid">
      {prompts.map((p) => (
        <button
          key={p.protocol}
          className="prompt-card"
          onClick={() => onPick(p.text)}
          disabled={disabled}
        >
          <div className="prompt-top">
            <span className={`protocol-icon ${p.protocol.toLowerCase()}`}>
              <p.icon size={18} />
            </span>
            <ArrowUpRight size={15} />
          </div>
          <strong>{p.title}</strong>
          <p>{p.detail}</p>
          <ProtocolBadge protocol={p.protocol} />
        </button>
      ))}
    </div>
  );
}
