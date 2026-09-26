import { Cloud, Laptop, Server, Settings2 } from 'lucide-react';
import type { AiProvider, ProviderOption } from '@/types';
import { Button } from './ui/button';
import { Dialog, DialogContent, DialogDescription, DialogTitle, DialogTrigger } from './ui/dialog';

const providerDetails = {
  ollama: {
    name: 'Ollama',
    badge: 'LOCAL',
    description: 'Runs on your machine. No cloud API key needed.',
    Icon: Laptop,
  },
  jev: {
    name: 'Jev',
    badge: 'HOSTED',
    description:
      'TypeSafe’s structured classification service. Requires internet and a configured API key.',
    Icon: Cloud,
  },
  clm: {
    name: 'CLM',
    badge: 'SELF-HOSTED',
    description:
      'Structured decisions on your configured CLM server. Can run locally or on your own remote server.',
    Icon: Server,
  },
};

export function AiPreferences({
  provider,
  providers,
  busy,
  onChange,
}: {
  provider: AiProvider;
  providers: ProviderOption[];
  busy: boolean;
  onChange: (provider: AiProvider) => void;
}) {
  return (
    <Dialog>
      <DialogTrigger asChild>
        <Button
          variant="ghost"
          size="sm"
          aria-label="AI preferences"
          className="preferences-trigger"
        >
          <Settings2 size={16} />
          <span>{providerDetails[provider].name}</span>
        </Button>
      </DialogTrigger>
      <DialogContent className="preferences-dialog" closeLabel="Close preferences">
        <header className="response-dialog-header">
          <div className="response-dialog-eyebrow">
            <Settings2 size={16} /> YOUR WORKSPACE
          </div>
          <DialogTitle>AI preferences</DialogTitle>
          <DialogDescription>
            Choose who routes your next question. Changes apply immediately and are remembered in
            this browser.
          </DialogDescription>
        </header>
        <fieldset className="provider-options" disabled={busy}>
          <legend>Routing provider</legend>
          {(['ollama', 'jev', 'clm'] as const).map((id) => {
            const option = providers.find((p) => p.id === id);
            const details = providerDetails[id];
            return (
              <label
                className={`provider-option ${provider === id ? 'provider-selected' : ''}`}
                key={id}
              >
                <input
                  type="radio"
                  name="ai-provider"
                  value={id}
                  checked={provider === id}
                  disabled={!option?.available}
                  onChange={() => onChange(id)}
                />
                <span className="provider-option-icon">
                  <details.Icon size={20} />
                </span>
                <span className="provider-option-copy">
                  <strong>
                    {details.name}
                    <small>{details.badge}</small>
                  </strong>
                  <span>{details.description}</span>
                  <code>{option?.model ?? 'Checking configuration…'}</code>
                  {option && !option.available && (
                    <span className="provider-unavailable">Not configured on this server.</span>
                  )}
                </span>
              </label>
            );
          })}
        </fieldset>
        <div className="preferences-note">
          <p>
            Only the selected provider receives your question. With Jev, your question and recent
            conversation are sent to TypeSafe. With CLM, they go to your configured server. All
            providers choose a protocol; answers come from the same catalog.
          </p>
          <p>
            {busy
              ? 'Wait for the current response before switching providers.'
              : 'Your selection applies to future questions. Existing responses keep their original provider.'}
          </p>
          {providers.some((p) => p.id === 'jev' && !p.available) && (
            <p>
              To enable Jev, configure its API key on the backend once. API keys are never stored in
              this browser.
            </p>
          )}
          {providers.some((p) => p.id === 'clm' && !p.available) && (
            <p>To enable CLM, connect a running CLM server in the backend configuration.</p>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}
