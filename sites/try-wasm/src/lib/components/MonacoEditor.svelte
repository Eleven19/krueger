<script lang="ts">
  import { onMount } from 'svelte';

  import { elmLanguageId, registerElmLanguage } from '$lib/elm-language';
  import { tokenizerReadyEvent } from '$lib/krueger';
  import { subscribeToResolvedTheme } from '$lib/theme';

  type MonacoControls = {
    expandAll: () => void;
    collapseAll: () => void;
    find: (query: string) => void;
  };

  let {
    value,
    language,
    ariaLabel,
    onChange,
    readOnly = false,
    controls = null
  }: {
    value: string;
    language: string;
    ariaLabel: string;
    onChange: (value: string) => void;
    readOnly?: boolean;
    controls?: MonacoControls | null;
  } = $props();

  let host: HTMLDivElement;
  let fallbackInput: HTMLTextAreaElement;
  let enhanced = $state(false);
  let editor: import('monaco-editor/esm/vs/editor/editor.api').editor.IStandaloneCodeEditor | null = null;
  let monacoApi: typeof import('monaco-editor/esm/vs/editor/editor.api') | null = null;

  function refreshElmTokenization(): void {
    if (language !== elmLanguageId || editor === null || monacoApi === null) return;

    const model = editor.getModel();
    if (model === null) return;

    monacoApi.editor.setModelLanguage(model, 'plaintext');
    monacoApi.editor.setModelLanguage(model, elmLanguageId);
  }

  function revealFallbackMatch(query: string): void {
    if (!fallbackInput) return;

    if (query.length === 0) {
      fallbackInput.setSelectionRange(0, 0);
      return;
    }

    const index = fallbackInput.value.indexOf(query);
    if (index < 0) return;

    fallbackInput.focus();
    fallbackInput.setSelectionRange(index, index + query.length);
  }

  function wireControls(): void {
    if (controls === null) return;

    controls.expandAll = () => {
      void editor?.getAction('editor.unfoldAll').run();
    };
    controls.collapseAll = () => {
      void editor?.getAction('editor.foldAll').run();
    };
    controls.find = (query: string) => {
      if (editor === null) {
        revealFallbackMatch(query);
        return;
      }

      if (query.length === 0) {
        editor.trigger('treeview-raw', 'closeFindWidget', undefined);
        return;
      }

      editor.trigger('treeview-raw', 'editor.actions.findWithArgs', {
        searchString: query,
        shouldFocus: 0,
        shouldRevealMatch: true,
        isRegex: false,
        matchCase: false,
        wholeWord: false
      });
    };
  }

  function resetControls(): void {
    if (controls === null) return;

    controls.expandAll = () => {};
    controls.collapseAll = () => {};
    controls.find = () => {};
  }

  onMount(() => {
    resetControls();
    wireControls();
    if (import.meta.env.MODE === 'test') return;

    let disposed = false;
    let subscription: { dispose: () => void } | null = null;
    let themeUnsubscribe: (() => void) | null = null;
    const onTokenizerReady = () => {
      refreshElmTokenization();
    };

    if (language === elmLanguageId && typeof globalThis.addEventListener === 'function') {
      globalThis.addEventListener(tokenizerReadyEvent, onTokenizerReady);
    }

    const loadContribution =
      language === 'json'
        ? import('monaco-editor/esm/vs/language/json/monaco.contribution')
        : Promise.resolve();

    void loadContribution.then(() => import('monaco-editor/esm/vs/editor/editor.api')).then((monaco) => {
      if (disposed) return;
      monacoApi = monaco;

      if (language === elmLanguageId) {
        registerElmLanguage(monaco);
      }

      editor = monaco.editor.create(host, {
        value,
        language,
        ariaLabel,
        automaticLayout: true,
        fontSize: 14,
        minimap: { enabled: false },
        readOnly,
        scrollBeyondLastLine: false,
        theme: 'vs-dark'
      });
      enhanced = true;
      wireControls();

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

      refreshElmTokenization();
    });

    return () => {
      disposed = true;
      subscription?.dispose();
      themeUnsubscribe?.();
      if (language === elmLanguageId && typeof globalThis.removeEventListener === 'function') {
        globalThis.removeEventListener(tokenizerReadyEvent, onTokenizerReady);
      }
      resetControls();
      editor?.dispose();
    };
  });

  $effect(() => {
    if (editor !== null && editor.getValue() !== value) {
      editor.setValue(value);
    }
  });

  $effect(() => {
    editor?.updateOptions({ readOnly });
  });

  $effect(() => {
    if (editor === null || monacoApi === null) return;
    const model = editor.getModel();
    if (model !== null) {
      monacoApi.editor.setModelLanguage(model, language);
    }
  });
</script>

<div class="monaco-frame">
  <textarea
    bind:this={fallbackInput}
    hidden={enhanced}
    spellcheck="false"
    aria-label={ariaLabel}
    readonly={readOnly}
    {value}
    oninput={(event) => onChange(event.currentTarget.value)}
  ></textarea>
  <div bind:this={host} class="monaco-host" hidden={!enhanced}></div>
</div>

<style>
  .monaco-frame {
    display: grid;
    min-height: 0;
  }

  .monaco-frame,
  .monaco-host {
    width: 100%;
    height: 100%;
  }

  .monaco-host {
    min-height: 0;
  }

  textarea {
    box-sizing: border-box;
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
