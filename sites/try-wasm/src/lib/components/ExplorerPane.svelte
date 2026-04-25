<script lang="ts">
  import MatchesView from './MatchesView.svelte';
  import PrettyQueryView from './PrettyQueryView.svelte';
  import SettingsPanel from './SettingsPanel.svelte';
  import TreeView from './TreeView.svelte';
  import { type BackendId } from '$lib/backend';
  import type { CompilerEnvelope, MatchView } from '$lib/krueger';
  import type { TreeSelection } from '$lib/playground/types';
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
    onBackendChange,
    onSelectNode
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
    onSelectNode?: (selection: TreeSelection) => void;
  } = $props();

  const label = $derived(panelLabel(selectedPanel));
  const cstDisplayResult = $derived(selectTreeDisplayResult(cstUnistResult, cstResult));
  const astDisplayResult = $derived(selectTreeDisplayResult(astUnistResult, astResult));

  function selectTreeDisplayResult(
    preferred: CompilerEnvelope<unknown>,
    fallback: CompilerEnvelope<unknown>
  ): CompilerEnvelope<unknown> {
    if (preferred.ok) return preferred;
    if (fallback.ok) return fallback;
    if (preferred.errors.length > 0) return preferred;
    return fallback;
  }
</script>

<div class="explorer-pane" role="tabpanel" aria-label={label}>
  <header class="explorer-header">
    <span>{label}</span>
  </header>

  <div class="explorer-body">
    {#if selectedPanel === 'matches'}
      <MatchesView result={matchResult} />
    {:else if selectedPanel === 'cst'}
      <TreeView result={cstDisplayResult} label="CST" {onSelectNode} />
    {:else if selectedPanel === 'ast'}
      <TreeView
        result={astDisplayResult}
        label="AST"
        errorTitle="AST errors:"
        {onSelectNode}
      />
    {:else if selectedPanel === 'settings'}
      <SettingsPanel {backend} {wasmGcSupported} {onBackendChange} />
    {:else}
      <PrettyQueryView {queryResult} {prettyQuery} />
    {/if}
  </div>
</div>

<style>
  .explorer-pane {
    min-width: 0;
    overflow: hidden;
    background: var(--kr-panel-bg);
    border-left: 1px solid var(--kr-border);
  }

  .explorer-header {
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

  .explorer-body {
    height: calc(100% - 2.5rem);
    overflow: auto;
    padding: 1rem;
  }
</style>
