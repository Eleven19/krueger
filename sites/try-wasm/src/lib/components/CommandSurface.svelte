<script lang="ts">
  import type { CommandAction } from '$lib/playground/types';

  let {
    actions,
    value = '',
    placeholder = 'Examples, imports, commands...',
    onSubmit,
    onInput
  }: {
    actions: CommandAction[];
    value?: string;
    placeholder?: string;
    onSubmit: (value: string) => void;
    onInput: (value: string) => void;
  } = $props();
</script>

<form
  class="command-surface"
  role="search"
  aria-label="Playground command surface"
  onsubmit={(event) => {
    event.preventDefault();
    onSubmit(value);
  }}
>
  <input
    type="search"
    class="command-input"
    aria-label="Playground command"
    autocomplete="off"
    spellcheck="false"
    bind:value
    placeholder={placeholder}
    oninput={(event) => onInput((event.currentTarget as HTMLInputElement).value)}
  />
  <span class="command-hint">{actions[0]?.label} / {actions[1]?.label}</span>
</form>

<style>
  .command-surface {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    align-items: center;
    gap: 0.75rem;
    width: min(40rem, calc(100vw - 14rem));
    padding: 0.4rem 0.65rem;
    background:
      linear-gradient(180deg, color-mix(in srgb, var(--kr-panel-bg) 92%, white 8%), var(--kr-panel-bg));
    border: 1px solid var(--kr-border);
    border-radius: 0.75rem;
    box-shadow: 0 10px 24px color-mix(in srgb, var(--kr-bg) 78%, transparent);
  }

  .command-input {
    width: 100%;
    min-width: 0;
    padding: 0.4rem 0.45rem;
    color: var(--kr-text);
    background: transparent;
    border: 0;
    outline: none;
    font: inherit;
  }

  .command-input::placeholder {
    color: var(--kr-muted);
  }

  .command-input::-webkit-search-decoration,
  .command-input::-webkit-search-cancel-button,
  .command-input::-webkit-search-results-button,
  .command-input::-webkit-search-results-decoration {
    appearance: none;
  }

  .command-hint {
    color: var(--kr-muted);
    font-size: 0.73rem;
    font-weight: 700;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    white-space: nowrap;
  }

  @media (max-width: 960px) {
    .command-surface {
      width: min(100%, calc(100vw - 8rem));
      grid-template-columns: minmax(0, 1fr);
      gap: 0.35rem;
    }

    .command-hint {
      display: none;
    }
  }
</style>
