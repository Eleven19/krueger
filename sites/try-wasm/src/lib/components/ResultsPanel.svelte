<script lang="ts">
  import MatchesView from './MatchesView.svelte';
  import PrettyQueryView from './PrettyQueryView.svelte';
  import SettingsPanel from './SettingsPanel.svelte';
  import TreeView from './TreeView.svelte';
  import { type BackendId } from '$lib/backend';
  import type { CompilerEnvelope, MatchView } from '$lib/krueger';
  import { panelLabel, type Panel } from '$lib/panels';

  let {
    selectedPanel,
    cstResult,
    astResult,
    cstUnistResult,
    astUnistResult,
    matchResult,
    queryResult,
    prettyQuery,
    backend,
    wasmGcSupported,
    onBackendChange
  }: {
    selectedPanel: Panel;
    cstResult: CompilerEnvelope<unknown>;
    astResult: CompilerEnvelope<unknown>;
    cstUnistResult: CompilerEnvelope<unknown>;
    astUnistResult: CompilerEnvelope<unknown>;
    matchResult: CompilerEnvelope<MatchView[]>;
    queryResult: CompilerEnvelope<unknown>;
    prettyQuery: string;
    backend: BackendId;
    wasmGcSupported: boolean | null;
    onBackendChange: (next: BackendId) => void;
  } = $props();

  const label = $derived(panelLabel(selectedPanel));
</script>

<div class="results-panel" role="tabpanel" aria-label={label}>
  <header class="results-header">
    <span>{label}</span>
  </header>

  <div class="results-body">
    {#if selectedPanel === 'matches'}
      <MatchesView result={matchResult} />
    {:else if selectedPanel === 'cst'}
      <TreeView result={cstUnistResult} label="CST" />
    {:else if selectedPanel === 'ast'}
      <TreeView result={astUnistResult} label="AST" errorTitle="AST errors:" />
    {:else if selectedPanel === 'settings'}
      <SettingsPanel {backend} {wasmGcSupported} {onBackendChange} />
    {:else}
      <PrettyQueryView {queryResult} {prettyQuery} />
    {/if}
  </div>
</div>

<style>
  .results-panel {
    min-width: 0;
    overflow: hidden;
    background: var(--kr-panel-bg);
    border-left: 1px solid var(--kr-border);
  }

  .results-header {
    display: flex;
    align-items: center;
    min-height: 2.5rem;
    padding: 0 1rem;
    color: var(--kr-muted);
    font-size: 0.8rem;
    font-weight: 700;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    border-bottom: 1px solid var(--kr-border);
  }

  .results-body {
    height: calc(100% - 2.5rem);
    overflow: auto;
    padding: 1rem;
  }
</style>
