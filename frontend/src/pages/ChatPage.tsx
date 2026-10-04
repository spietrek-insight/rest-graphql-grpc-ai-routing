import { useEffect, useRef, useState } from 'react';
import {
  Braces,
  Plus,
  BookOpen,
  ChevronRight,
  CircleAlert,
  PanelRightClose,
  PanelRightOpen,
  Sparkles,
} from 'lucide-react';
import { getStatus, sendChat } from '@/api/chat';
import { useChatStore } from '@/store/useChatStore';
import { usePreferencesStore } from '@/store/usePreferencesStore';
import { AiPreferences } from '@/components/AiPreferences';
import type { Status } from '@/types';
import { Sidebar } from '@/components/Sidebar';
import { InsightPanel } from '@/components/InsightPanel';
import { CatalogGuide } from '@/components/CatalogGuide';
import { ChatInput } from '@/components/chat/ChatInput';
import { MessageList } from '@/components/chat/MessageList';
import { PromptSuggestions } from '@/components/chat/PromptSuggestions';
import { Button } from '@/components/ui/button';
export function ChatPage() {
  const state = useChatStore();
  const preferences = usePreferencesStore();
  const [input, setInput] = useState('');
  const [status, setStatus] = useState<Status | null>(null);
  const [guide, setGuide] = useState(false);
  const [inspector, setInspector] = useState(() => window.innerWidth >= 970);
  const [retry, setRetry] = useState('');
  const controller = useRef<AbortController | null>(null);
  const provider =
    preferences.provider ??
    (status?.provider === 'jev' || status?.provider === 'clm' ? status.provider : 'ollama');
  const selectedProvider = status?.providers.find((p) => p.id === provider);
  const selectedStatus =
    status && selectedProvider
      ? { ...status, provider, model: selectedProvider.model, local: selectedProvider.local }
      : status;
  useEffect(() => {
    const abort = new AbortController();
    getStatus(abort.signal)
      .then(setStatus)
      .catch(() => setStatus(null));
    return () => {
      abort.abort();
      controller.current?.abort();
    };
  }, []);
  async function submit(text: string) {
    const message = text.trim();
    if (!message || controller.current) return;
    if (state.override === 'AUTO' && selectedProvider?.available === false) {
      state.setError(
        'The selected AI provider is not configured. Choose an available provider in AI preferences or configure the selected provider on the backend.',
      );
      return;
    }
    const abort = new AbortController();
    controller.current = abort;
    const session = state.sessionId;
    const previous = state.messages;
    state.append({ id: crypto.randomUUID(), role: 'user', content: message });
    state.setBusy(true);
    state.setDecision(null);
    state.selectInsight(null);
    const override = state.override;
    state.setOverride('AUTO');
    setInput('');
    setGuide(false);
    setRetry(message);
    try {
      await sendChat(
        message,
        override,
        previous,
        abort.signal,
        (event) => {
          if (abort.signal.aborted || useChatStore.getState().sessionId !== session) return;
          if (event.type === 'decision') state.setDecision(event.data);
          if (event.type === 'snapshot') state.addSnapshot(event.data);
          if (event.type === 'result') {
            state.append({
              id: crypto.randomUUID(),
              role: 'assistant',
              content: event.data.answer,
              insight: event.data.insight,
            });
            state.selectInsight(event.data.insight);
          }
        },
        provider,
      );
    } catch (error) {
      if (!abort.signal.aborted)
        state.setError(
          error instanceof Error ? error.message : 'Something went wrong. Please try again.',
        );
    } finally {
      if (controller.current === abort) {
        controller.current = null;
        state.setBusy(false);
      }
    }
  }
  function cancel() {
    controller.current?.abort();
    controller.current = null;
    state.setBusy(false);
  }
  function reset() {
    cancel();
    state.reset();
    setInput('');
    setGuide(false);
  }
  return (
    <div className={`app-shell ${inspector ? '' : 'inspector-hidden'}`}>
      <Sidebar
        onReset={reset}
        onGuide={() => setGuide(false)}
        onCatalog={() => setGuide(true)}
        status={selectedStatus}
      />
      <main className="main-panel">
        <header className="topbar">
          <div className="breadcrumb">
            Workspace <ChevronRight size={13} />
            <strong>{guide ? 'Dataset & guide' : 'Conversation'}</strong>
          </div>
          <div className="topbar-actions">
            <AiPreferences
              provider={provider}
              providers={status?.providers ?? []}
              busy={state.busy}
              onChange={preferences.setProvider}
            />
            <Button
              className="mobile-action"
              variant="ghost"
              size="icon"
              aria-label="Start new conversation"
              onClick={reset}
            >
              <Plus size={17} />
            </Button>
            <Button
              className="mobile-action"
              variant="ghost"
              size="icon"
              aria-label="Open dataset guide"
              onClick={() => setGuide(true)}
            >
              <BookOpen size={17} />
            </Button>
            <span className="session-label">
              <span className={`tiny-dot ${status ? 'green' : 'gray'}`} />
              {status ? 'Backend connected' : 'Backend unavailable'}
            </span>
            <Button
              variant="ghost"
              size="icon"
              onClick={() => setInspector(!inspector)}
              aria-label={inspector ? 'Hide protocol insight' : 'Show protocol insight'}
            >
              {inspector ? <PanelRightClose size={18} /> : <PanelRightOpen size={18} />}
            </Button>
          </div>
        </header>
        <div className="conversation-heading">
          <div>
            <h2>
              {guide ? 'Explore the dataset' : 'The conversation starts here'}{' '}
              <span className="demo-badge">PLAYGROUND</span>
            </h2>
            <p>Natural language in. The right protocol out.</p>
          </div>
          <span className="ephemeral-label">Session only</span>
        </div>
        {guide ? (
          <CatalogGuide onClose={() => setGuide(false)} />
        ) : state.messages.length ? (
          <MessageList
            messages={state.messages}
            busy={state.busy}
            snapshots={state.snapshots.length}
            onInspect={(insight) => {
              state.selectInsight(insight);
              setInspector(true);
            }}
            onSend={(text) => void submit(text)}
          />
        ) : (
          <section className="welcome">
            <span className="hero-emblem">
              <Braces size={30} />
              <span className="emblem-spark">
                <Sparkles size={12} />
              </span>
            </span>
            <div className="section-kicker">A CONVERSATION WORTH EXPLORING</div>
            <h1>
              Ask naturally.
              <br />
              <span>Discover what connects.</span>
            </h1>
            <p>
              Explore a universe of starships, films, and cast.
              <br />
              Let AI choose the route. See every request along the way.
            </p>
            <PromptSuggestions onPick={(text) => void submit(text)} />
            <div className="welcome-foot">
              <span /> A small dataset. Three powerful ways to explore it. <span />
            </div>
          </section>
        )}
        {state.error && (
          <div className="error-banner" role="alert">
            <CircleAlert size={17} />
            <span>{state.error}</span>
            <Button variant="ghost" size="sm" onClick={() => void submit(retry)}>
              Retry
            </Button>
          </div>
        )}
        <ChatInput
          value={input}
          onChange={setInput}
          onSubmit={() => void submit(input)}
          busy={state.busy}
          onCancel={cancel}
          override={state.override}
          onOverride={state.setOverride}
        />
      </main>
      {inspector && (
        <InsightPanel
          insight={state.selectedInsight}
          decision={state.decision}
          snapshots={state.snapshots}
          busy={state.busy}
        />
      )}
    </div>
  );
}
