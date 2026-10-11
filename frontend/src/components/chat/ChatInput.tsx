import { ArrowUp, ArrowUpRight, Braces, Radio, Square, WandSparkles } from 'lucide-react';
import type { Override } from '@/types';
import { Button } from '../ui/button';
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectLabel,
  SelectSeparator,
  SelectTrigger,
  SelectValue,
} from '../ui/select';
export function ChatInput({
  value,
  onChange,
  onSubmit,
  busy,
  onCancel,
  override,
  onOverride,
}: {
  value: string;
  onChange: (value: string) => void;
  onSubmit: () => void;
  busy: boolean;
  onCancel: () => void;
  override: Override;
  onOverride: (value: Override) => void;
}) {
  return (
    <div className="composer-area">
      <form
        className="composer"
        onSubmit={(e) => {
          e.preventDefault();
          onSubmit();
        }}
      >
        <textarea
          aria-label="Your question"
          placeholder="Ask about a starship, a film, or the connections between them…"
          value={value}
          maxLength={2000}
          onChange={(e) => onChange(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
              e.preventDefault();
              if (!busy && value.trim()) onSubmit();
            }
          }}
          rows={2}
        />
        <div className="composer-toolbar">
          <Select
            value={override}
            onValueChange={(next) => onOverride(next as Override)}
            disabled={busy}
          >
            <SelectTrigger aria-label="Protocol for next question">
              <WandSparkles size={14} aria-hidden="true" />
              <SelectValue />
            </SelectTrigger>
            <SelectContent align="start">
              <SelectGroup>
                <SelectLabel className="select-label">Routing for next question</SelectLabel>
                <SelectItem
                  value="AUTO"
                  icon={<WandSparkles size={17} />}
                  description="Let your selected AI provider choose."
                >
                  Auto · AI chooses
                </SelectItem>
              </SelectGroup>
              <SelectSeparator className="select-separator" />
              <SelectGroup>
                <SelectLabel className="select-label">Presenter overrides · bypass AI</SelectLabel>
                <SelectItem
                  value="REST"
                  className="select-rest"
                  icon={<ArrowUpRight size={17} />}
                  description="Look up a resource directly."
                >
                  Force REST · next question
                </SelectItem>
                <SelectItem
                  value="GRAPHQL"
                  className="select-graphql"
                  icon={<Braces size={17} />}
                  description="Explore connected ships, films and cast."
                >
                  Force GraphQL · next question
                </SelectItem>
                <SelectItem
                  value="GRPC"
                  className="select-grpc"
                  icon={<Radio size={17} />}
                  description="Stream live starship updates over one subscription."
                >
                  Force gRPC · next question
                </SelectItem>
              </SelectGroup>
            </SelectContent>
          </Select>
          <div className="send-controls">
            <span>{value.length > 1800 ? `${value.length}/2000` : '↵ to send'}</span>
            {busy ? (
              <Button type="button" size="icon" aria-label="Stop response" onClick={onCancel}>
                <Square size={14} />
              </Button>
            ) : (
              <Button size="icon" type="submit" aria-label="Send question" disabled={!value.trim()}>
                <ArrowUp size={18} />
              </Button>
            )}
          </div>
        </div>
      </form>
      <div className="composer-note">
        <span className="tiny-dot green" />
        Real protocol calls. Inspect every request.<span>Shift + Enter for a new line</span>
      </div>
    </div>
  );
}
