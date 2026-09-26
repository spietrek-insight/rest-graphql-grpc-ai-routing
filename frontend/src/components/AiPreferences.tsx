import { Cloud, Laptop, Settings2 } from 'lucide-react';
import type { AiProvider, ProviderOption } from '@/types';
import { Button } from './ui/button';
import { Dialog, DialogContent, DialogDescription, DialogTitle, DialogTrigger } from './ui/dialog';

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
          <span>{provider === 'jev' ? 'Jev' : 'Ollama'}</span>
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
          {(['ollama', 'jev'] as const).map((id) => {
            const option = providers.find((p) => p.id === id);
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
                  {id === 'ollama' ? <Laptop size={20} /> : <Cloud size={20} />}
                </span>
                <span className="provider-option-copy">
                  <strong>
                    {id === 'ollama' ? 'Ollama' : 'Jev'}
                    <small>{id === 'ollama' ? 'LOCAL' : 'HOSTED'}</small>
                  </strong>
                  <span>
                    {id === 'ollama'
                      ? 'Runs on your machine. No cloud API key needed.'
                      : 'TypeSafe’s structured classification service. Requires internet and a configured API key.'}
                  </span>
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
            conversation are sent to TypeSafe. Both providers choose a protocol; answers come from
            the same catalog.
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
        </div>
      </DialogContent>
    </Dialog>
  );
}
