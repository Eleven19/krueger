<script lang="ts">
  import MonacoEditor from './MonacoEditor.svelte';
  import { elmLanguageId } from '$lib/elm-language';

  let {
    source,
    query,
    onSourceChange,
    onQueryChange
  }: {
    source: string;
    query: string;
    onSourceChange: (value: string) => void;
    onQueryChange: (value: string) => void;
  } = $props();
</script>

<section class="editor-group" aria-label="Compiler inputs">
  <label class="editor-field">
    <span>Elm source</span>
    <MonacoEditor
      value={source}
      language={elmLanguageId}
      ariaLabel="Elm source"
      onChange={onSourceChange}
    />
  </label>

  <label class="editor-field">
    <span>Query</span>
    <MonacoEditor
      value={query}
      language="plaintext"
      ariaLabel="Query"
      onChange={onQueryChange}
    />
  </label>
</section>

<style>
  .editor-group {
    display: grid;
    grid-template-rows: minmax(0, 1fr) minmax(9rem, 0.35fr);
    gap: 1px;
    min-width: 0;
    overflow: hidden;
    background: var(--kr-border);
  }

  .editor-field {
    display: grid;
    grid-template-rows: auto minmax(0, 1fr);
    min-height: 0;
    background: var(--kr-editor-bg);
  }

  .editor-field span {
    padding: 0.65rem 0.875rem;
    color: var(--kr-muted);
    font-size: 0.8rem;
    font-weight: 700;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    border-bottom: 1px solid var(--kr-border);
  }

</style>
