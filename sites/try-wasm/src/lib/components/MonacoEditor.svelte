<script lang="ts">
  import { onMount } from 'svelte';

  import { elmLanguageId, registerElmLanguage } from '$lib/elm-language';
  import { subscribeToResolvedTheme } from '$lib/theme';

  let {
    value,
    language,
    ariaLabel,
    onChange
  }: {
    value: string;
    language: string;
    ariaLabel: string;
    onChange: (value: string) => void;
  } = $props();

  let host: HTMLDivElement;
  let enhanced = $state(false);
  let editor: import('monaco-editor/esm/vs/editor/editor.api').editor.IStandaloneCodeEditor | null = null;

  onMount(() => {
    if (import.meta.env.MODE === 'test') return;

    let disposed = false;
    let subscription: { dispose: () => void } | null = null;
    let themeUnsubscribe: (() => void) | null = null;

    void import('monaco-editor/esm/vs/editor/editor.api').then((monaco) => {
      if (disposed) return;

      if (language === elmLanguageId) {
        registerElmLanguage(monaco);
      }

      editor = monaco.editor.create(host, {
        value,
        language,
        automaticLayout: true,
        fontSize: 14,
        minimap: { enabled: false },
        theme: 'vs-dark'
      });
      enhanced = true;

      // Bridge Monaco's theme to the page-level `data-theme` attribute the
      // shared bootstrap in app.html stamps on <html>. Monaco's setTheme
      // is global — calling it on every theme transition keeps both the
      // source and query editors in lockstep, and tracks user preference
      // changes from any ThemeToggle on this page or another tab.
      themeUnsubscribe = subscribeToResolvedTheme((resolved) => {
        monaco.editor.setTheme(resolved === 'light' ? 'vs' : 'vs-dark');
      });

      subscription = editor.onDidChangeModelContent(() => {
        const next = editor?.getValue() ?? '';
        if (next !== value) onChange(next);
      });
    });

    return () => {
      disposed = true;
      subscription?.dispose();
      themeUnsubscribe?.();
      editor?.dispose();
    };
  });

  $effect(() => {
    if (editor !== null && editor.getValue() !== value) {
      editor.setValue(value);
    }
  });
</script>

<div class="monaco-frame">
  <textarea
    hidden={enhanced}
    spellcheck="false"
    aria-label={ariaLabel}
    {value}
    oninput={(event) => onChange(event.currentTarget.value)}
  ></textarea>
  <div bind:this={host} class="monaco-host" hidden={!enhanced}></div>
</div>

<style>
  .monaco-frame {
    min-height: 0;
  }

  .monaco-frame,
  .monaco-host {
    width: 100%;
    height: 100%;
  }

  textarea {
    width: 100%;
    height: 100%;
    min-height: 0;
    padding: 1rem;
    color: var(--kr-text);
    background: transparent;
    border: 0;
    outline: none;
    resize: none;
    font:
      0.9rem/1.55 ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas,
      "Liberation Mono", monospace;
  }
</style>
