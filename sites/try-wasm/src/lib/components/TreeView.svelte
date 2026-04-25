<script lang="ts">
  import MonacoEditor from './MonacoEditor.svelte';
  import type { CompilerEnvelope, UnistNode } from '$lib/krueger';
  import type { TreeSelection } from '$lib/playground/types';

  type TreeEntry = {
    id: string;
    path: number[];
    node: UnistNode;
    children: TreeEntry[];
  };

  let {
    result,
    label = 'Tree',
    errorTitle = 'Parse errors:',
    onSelectNode,
    selectedSelection = null
  }: {
    result: CompilerEnvelope<unknown>;
    label?: string;
    errorTitle?: string;
    onSelectNode?: (selection: TreeSelection) => void;
    selectedSelection?: TreeSelection | null;
  } = $props();

  let filter = $state('');
  let rawPreferred = $state(false);
  let collapsedIds = $state<string[]>([]);
  const rawViewerControls = {
    expandAll: () => {},
    collapseAll: () => {},
    find: (_query: string) => {}
  };

  const treeRoot = $derived(result.ok && isUnistNode(result.value) ? result.value : null);
  const filterQuery = $derived(filter.trim().toLowerCase());
  const filterActive = $derived(filterQuery.length > 0);
  const filteredTree = $derived(treeRoot ? filterTree(treeRoot, filterQuery, '0') : null);
  const treeAvailable = $derived(treeRoot !== null);
  const showTree = $derived(treeAvailable && !rawPreferred);
  const collapsedSet = $derived(new Set(collapsedIds));
  const rawText = $derived(stringifyValue(result.value));
  const rawLanguage = $derived(typeof result.value === 'string' ? 'plaintext' : 'json');

  function toggleNode(id: string): void {
    const next = new Set(collapsedIds);
    if (next.has(id)) {
      next.delete(id);
    } else {
      next.add(id);
    }
    collapsedIds = Array.from(next);
  }

  function expandAll(): void {
    if (showTree) {
      collapsedIds = [];
      return;
    }

    rawViewerControls.expandAll();
  }

  function collapseAll(): void {
    if (showTree) {
      collapsedIds = treeRoot ? collectCollapsibleIds(treeRoot) : [];
      return;
    }

    rawViewerControls.collapseAll();
  }

  function setMode(next: 'tree' | 'raw'): void {
    rawPreferred = next === 'raw';
    filter = '';
  }

  function isUnistNode(value: unknown): value is UnistNode {
    if (typeof value !== 'object' || value === null) return false;

    const record = value as Record<string, unknown>;
    const data = record.data;

    if (typeof record.type !== 'string') return false;
    if (!Array.isArray(record.children)) return false;
    if (typeof data !== 'object' || data === null) return false;

    const dataRecord = data as Record<string, unknown>;
    return (
      typeof dataRecord.childCount === 'number' &&
      typeof dataRecord.fields === 'object' &&
      dataRecord.fields !== null
    );
  }

  function filterTree(node: UnistNode, query: string, id: string, path: number[] = []): TreeEntry | null {
    const children = node.children
      .map((child, index) => filterTree(child, query, `${id}.${index}`, [...path, index]))
      .filter((child): child is TreeEntry => child !== null);

    if (query.length > 0 && !matchesNode(node, query) && children.length === 0) {
      return null;
    }

    return { id, path, node, children };
  }

  function matchesNode(node: UnistNode, query: string): boolean {
    const haystack = `${node.type} ${node.value ?? ''}`.toLowerCase();
    return haystack.includes(query);
  }

  function collectCollapsibleIds(node: UnistNode, id = '0'): string[] {
    const childIds = node.children.flatMap((child, index) =>
      collectCollapsibleIds(child, `${id}.${index}`)
    );

    return node.children.length > 0 ? [id, ...childIds] : childIds;
  }

  function childCount(node: UnistNode): number {
    return node.data.childCount;
  }

  function childCountLabel(node: UnistNode): string {
    const count = childCount(node);
    return `${count} ${count === 1 ? 'child' : 'children'}`;
  }

  function stringifyValue(value: unknown): string {
    if (value == null) return '';
    if (typeof value === 'string') return value;

    try {
      return JSON.stringify(value, null, 2);
    } catch {
      return String(value);
    }
  }

  function isSelected(entry: TreeEntry): boolean {
    if (selectedSelection == null) return false;
    if (selectedSelection.nodeType !== entry.node.type) return false;
    if (selectedSelection.text !== entry.node.value) return false;
    if (selectedSelection.childCount !== entry.node.data.childCount) return false;
    if (selectedSelection.path.length !== entry.path.length) return false;

    return selectedSelection.path.every((segment, index) => segment === entry.path[index]);
  }
</script>

<section class="tree-view">
  {#if !result.ok}
    <div class="error-card">
      <strong>{errorTitle}</strong>
      <ul>
        {#each result.errors as error}
          <li>{error.message}</li>
        {/each}
      </ul>
    </div>
  {:else if treeAvailable}
    <div class="tree-surface">
      <div class="tree-toolbar">
        <label class="filter-field">
          <span class="sr-only">Filter nodes</span>
          <input
            type="search"
            aria-label="Filter nodes"
            placeholder={showTree ? 'Filter nodes' : 'Find in raw view'}
            value={filter}
            oninput={(event) => {
              filter = (event.currentTarget as HTMLInputElement).value;
              if (!showTree) {
                rawViewerControls.find(filter);
              }
            }}
          />
        </label>

        <div class="toolbar-actions" role="group" aria-label={`${label} tree controls`}>
          <button type="button" class="toolbar-button" onclick={expandAll}>
            Expand all
          </button>
          <button type="button" class="toolbar-button" onclick={collapseAll}>
            Collapse all
          </button>

          <div class="mode-toggle" role="group" aria-label="View mode">
            <button
              type="button"
              class:active={showTree}
              aria-pressed={showTree}
              onclick={() => {
                setMode('tree');
              }}
            >
              Tree
            </button>
            <button
              type="button"
              class:active={!showTree}
              aria-pressed={!showTree}
              onclick={() => {
                setMode('raw');
              }}
            >
              Raw
            </button>
          </div>
        </div>
      </div>

      {#if showTree}
        {#if filteredTree}
          <div class="tree-canvas" role="tree" aria-label={`${label} tree`}>
            {@render renderNode(filteredTree, 1)}
          </div>
        {:else}
          <p class="empty-state">No matching nodes.</p>
        {/if}
      {:else}
        <div class="tree-body tree-editor">
          <MonacoEditor
            value={rawText}
            language={rawLanguage}
            ariaLabel={`${label} raw view`}
            readOnly={true}
            controls={rawViewerControls}
            onChange={() => {}}
          />
        </div>
      {/if}
    </div>
  {:else}
    <div class="tree-body tree-editor">
      <MonacoEditor
        value={rawText}
        language={rawLanguage}
        ariaLabel={`${label} raw view`}
        readOnly={true}
        onChange={() => {}}
      />
    </div>
  {/if}
</section>

{#snippet renderNode(entry: TreeEntry, level: number)}
  {@const hasChildren = entry.children.length > 0}
  {@const expanded = filterActive || !collapsedSet.has(entry.id)}
  {@const selected = isSelected(entry)}

  <div
    class="tree-item"
    role="treeitem"
    aria-level={level}
    aria-expanded={hasChildren ? expanded : undefined}
    aria-selected={selected ? 'true' : undefined}
  >
    <div class="tree-row" style={`--tree-level:${level - 1};`}>
      {#if hasChildren}
        <button
          type="button"
          class="disclosure"
          aria-label={`${expanded ? 'Collapse' : 'Expand'} ${entry.node.type}`}
          onclick={() => {
            toggleNode(entry.id);
          }}
        >
          {expanded ? '▾' : '▸'}
        </button>
      {:else}
        <span class="disclosure-spacer" aria-hidden="true"></span>
      {/if}

      <button
        type="button"
        class:selected
        class="node-button"
        aria-label={`Select ${entry.node.type}`}
        onclick={() =>
          onSelectNode?.({
            path: entry.path,
            nodeType: entry.node.type,
            text: entry.node.value,
            childCount: entry.node.data.childCount
          })}
      >
        <span class="node-type">{entry.node.type}</span>

        {#if entry.node.value != null}
          <span class="node-value" style:color="var(--kr-tree-value)">
            {JSON.stringify(entry.node.value)}
          </span>
        {/if}

        <span class="node-count">{childCountLabel(entry.node)}</span>
      </button>
    </div>

    {#if hasChildren && expanded}
      <div role="group">
        {#each entry.children as child}
          {@render renderNode(child, level + 1)}
        {/each}
      </div>
    {/if}
  </div>
{/snippet}

<style>
  .tree-view {
    display: grid;
    height: 100%;
    min-height: 16rem;
  }

  .tree-surface,
  .tree-body,
  .error-card {
    margin: 0;
    overflow: hidden;
    background: var(--kr-panel-bg-strong);
    border: 1px solid var(--kr-border);
    border-radius: 0.625rem;
  }

  .tree-surface {
    display: grid;
    height: 100%;
    min-height: 16rem;
    grid-template-rows: auto minmax(0, 1fr);
  }

  .tree-toolbar {
    display: flex;
    flex-wrap: wrap;
    gap: 0.75rem;
    align-items: center;
    justify-content: space-between;
    padding: 0.875rem;
    border-bottom: 1px solid var(--kr-border);
  }

  .filter-field {
    flex: 1 1 14rem;
    min-width: 12rem;
  }

  .filter-field input {
    width: 100%;
    height: 2.25rem;
    padding: 0 0.75rem;
    color: var(--kr-text);
    background: var(--kr-panel-bg);
    border: 1px solid var(--kr-border);
    border-radius: 0.5rem;
    font: inherit;
  }

  .filter-field input::placeholder {
    color: var(--kr-muted);
  }

  .toolbar-actions,
  .mode-toggle {
    display: flex;
    flex-wrap: wrap;
    gap: 0.5rem;
    align-items: center;
  }

  .toolbar-button,
  .mode-toggle button,
  .disclosure {
    font: inherit;
    color: var(--kr-text);
    background: var(--kr-panel-bg);
    border: 1px solid var(--kr-border);
    border-radius: 0.5rem;
  }

  .toolbar-button,
  .mode-toggle button {
    min-height: 2.25rem;
    padding: 0 0.75rem;
  }

  .mode-toggle button.active,
  .toolbar-button:hover,
  .mode-toggle button:hover,
  .disclosure:hover {
    border-color: var(--kr-accent);
  }

  .tree-canvas,
  .tree-body,
  .empty-state {
    min-height: 0;
    overflow: auto;
  }

  .tree-canvas {
    padding: 0.5rem 0;
  }

  .tree-item {
    display: block;
  }

  .tree-row {
    display: grid;
    grid-template-columns: 1.5rem minmax(0, 1fr);
    gap: 0.5rem;
    align-items: center;
    min-height: 2rem;
    padding: 0 0.75rem 0 calc(0.75rem + (var(--tree-level) * 1rem));
  }

  .disclosure,
  .disclosure-spacer {
    display: inline-grid;
    place-items: center;
    width: 1.5rem;
    height: 1.5rem;
  }

  .disclosure {
    padding: 0;
    cursor: pointer;
  }

  .node-button {
    display: grid;
    grid-template-columns: minmax(0, auto) minmax(0, 1fr) auto;
    gap: 0.5rem;
    align-items: center;
    width: 100%;
    min-width: 0;
    min-height: 2rem;
    padding: 0;
    color: inherit;
    text-align: left;
    background: transparent;
    border: 0;
    border-radius: 0.375rem;
    font: inherit;
    cursor: pointer;
  }

  .node-button:hover {
    background: color-mix(in srgb, var(--kr-accent) 10%, transparent);
  }

  .node-button.selected {
    background: color-mix(in srgb, var(--kr-accent) 18%, transparent);
  }

  .disclosure-spacer {
    color: transparent;
  }

  .node-type,
  .node-value,
  .node-count {
    min-width: 0;
  }

  .node-type {
    font-weight: 600;
    color: var(--kr-text);
  }

  .node-value {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .node-count {
    color: var(--kr-muted);
    font-size: 0.875rem;
    text-align: right;
    white-space: nowrap;
  }

  .tree-body {
    padding: 0.875rem;
    color: var(--kr-text);
    white-space: pre-wrap;
  }

  .tree-editor {
    display: grid;
    height: 100%;
    padding: 0;
    white-space: normal;
  }

  .empty-state {
    margin: 0;
    padding: 1rem;
    color: var(--kr-muted);
  }

  .error-card {
    padding: 0.875rem;
    color: var(--kr-error-text);
    background: var(--kr-error-bg);
    border-color: var(--kr-error-border);
  }

  .sr-only {
    position: absolute;
    width: 1px;
    height: 1px;
    padding: 0;
    margin: -1px;
    overflow: hidden;
    clip: rect(0, 0, 0, 0);
    white-space: nowrap;
    border: 0;
  }
</style>
