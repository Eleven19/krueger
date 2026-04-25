<script lang="ts">
  import { onMount } from "svelte";
  import { Effect } from "effect";

  import ActivityBar from "$lib/components/ActivityBar.svelte";
  import EditorGroup from "$lib/components/EditorGroup.svelte";
  import ExplorerPane from "$lib/components/ExplorerPane.svelte";
  import ExplorerToolbar from "$lib/components/ExplorerToolbar.svelte";
  import InspectorPanel from "$lib/components/InspectorPanel.svelte";
  import PaneResizeHandle from "$lib/components/PaneResizeHandle.svelte";
  import SiteHeader from "$lib/components/SiteHeader.svelte";
  import UtilityPanel from "$lib/components/UtilityPanel.svelte";
  import {
    fallbackBackend,
    isAvailable,
    pickInitialBackend,
    type BackendId
  } from "$lib/backend";
  import {
    createKruegerClient,
    type CompilerEnvelope,
    type KruegerClient,
    type MatchView
  } from "$lib/krueger";
  import { commandSurfaceActions, playgroundExamples } from "$lib/playground/catalog";
  import { loadExample, resolveCommandIntent } from "$lib/playground/actions";
  import type { PlaygroundDiagnostic, PlaygroundLog, TreeSelection } from "$lib/playground/types";
  import { defaultPanel, type Panel } from "$lib/panels";
  import { supportsWasmGc } from "$lib/wasm-gc";

  const defaultSource = `module Demo exposing (..)

main = 42
`;

  const defaultQuery = "(CstValueDeclaration) @decl";

  let wasmGcSupported = $state<boolean | null>(null);
  let client = $state<KruegerClient | null>(null);
  let compilerLoadError = $state<string | null>(null);
  let selectedPanel = $state<Panel>(defaultPanel);
  let source = $state(defaultSource);
  let query = $state(defaultQuery);
  let backend = $state<BackendId>(pickInitialBackend(null));
  let commandText = $state('');
  let editorPercent = $state(62);
  let utilityPercent = $state(76);
  let selection = $state<TreeSelection | null>(null);
  let selectionPanel = $state<Panel | null>(null);
  let logs = $state<PlaygroundLog[]>([{ message: 'Playground ready.', kind: 'info' }]);
  let problems = $state<PlaygroundDiagnostic[]>([]);

  const cstResult = $derived(
    compilerEnvelope(() => client?.parseCst(source), "Compiler loading...")
  );
  const astResult = $derived(
    compilerEnvelope(() => client?.parseAst(source), "Compiler loading...")
  );
  const cstUnistResult = $derived(
    compilerEnvelope(
      () => client?.parseCstUnist(source) as CompilerEnvelope<unknown> | undefined,
      "Compiler loading..."
    )
  );
  const astUnistResult = $derived(
    compilerEnvelope(
      () => client?.parseAstUnist(source) as CompilerEnvelope<unknown> | undefined,
      "Compiler loading..."
    )
  );
  const queryResult = $derived(
    compilerEnvelope(() => client?.parseQuery(query), "Compiler loading...")
  );
  const matchResult = $derived(computeMatches(client, cstResult, queryResult));
  const prettyQuery = $derived(
    client && queryResult.ok && queryResult.value != null
      ? client.prettyQuery(queryResult.value)
      : ""
  );
  const activeTreeInspectable = $derived(
    selectedPanel === 'cst'
      ? cstUnistResult.ok || cstResult.ok
      : selectedPanel === 'ast'
        ? astUnistResult.ok || astResult.ok
        : false
  );

  $effect(() => {
    if (selection == null) return;
    if (selectionPanel == null) return;
    if (selectedPanel !== selectionPanel) {
      selection = null;
      selectionPanel = null;
      return;
    }
    if (!activeTreeInspectable) {
      selection = null;
      selectionPanel = null;
    }
  });

  onMount(() => {
    const supported = supportsWasmGc();
    wasmGcSupported = supported;

    // Reconcile the initial backend choice with the just-resolved support
    // probe. If the page started in WASM mode but the host can't run it,
    // fall back to the JS backend before any compile call goes through.
    if (!isAvailable(backend, supported)) {
      backend = fallbackBackend;
    }

    void loadBackend(backend);
  });

  async function loadBackend(next: BackendId): Promise<void> {
    try {
      const loaded = await createKruegerClient(next);
      client = loaded;
      compilerLoadError = null;
    } catch (error: unknown) {
      client = null;
      compilerLoadError = error instanceof Error ? error.message : String(error);
    }
  }

  function compilerEnvelope<T>(
    read: () => CompilerEnvelope<T> | undefined,
    pendingValue: T
  ): CompilerEnvelope<T> {
    if (compilerLoadError !== null) return errorEnvelope("internal", compilerLoadError);
    return read() ?? { ok: true, value: pendingValue, logs: [], errors: [] };
  }

  function computeMatches(
    loadedClient: KruegerClient | null,
    cst: CompilerEnvelope<unknown>,
    parsedQuery: CompilerEnvelope<unknown>
  ): CompilerEnvelope<MatchView[]> {
    if (compilerLoadError !== null) return errorEnvelope("internal", compilerLoadError);
    if (!cst.ok) return { ok: false, value: null, logs: cst.logs, errors: cst.errors };
    if (!parsedQuery.ok) {
      return { ok: false, value: null, logs: parsedQuery.logs, errors: parsedQuery.errors };
    }
    if (loadedClient === null || cst.value == null || parsedQuery.value == null) {
      return { ok: true, value: [], logs: [], errors: [] };
    }
    return loadedClient.runQuery(parsedQuery.value, cst.value);
  }

  function errorEnvelope<T>(phase: string, message: string): CompilerEnvelope<T> {
    return { ok: false, value: null, logs: [], errors: [{ phase, message }] };
  }

  function handleBackendChange(next: BackendId): void {
    if (next === backend) return;
    if (!isAvailable(next, wasmGcSupported)) return;
    selection = null;
    selectionPanel = null;
    backend = next;
    // Drop the current client so panels show the "Compiler loading..."
    // placeholder while the new backend's facade is dynamic-imported.
    client = null;
    void loadBackend(next);
  }

  async function applyExample(exampleId: string): Promise<void> {
    const example = await Effect.runPromise(loadExample(exampleId));
    selection = null;
    selectionPanel = null;
    source = example.source;
    query = example.query;
    logs = [{ message: `Loaded example ${example.label}.`, kind: 'success' }, ...logs];
  }

  async function runCommand(value: string): Promise<void> {
    const exit = await Effect.runPromiseExit(resolveCommandIntent(value));
    if (exit._tag === 'Failure') {
      problems = [
        {
          code: 'github/import-failed',
          message: 'Could not complete the requested import.',
          severity: 'error',
          source: 'github'
        },
        ...problems
      ];
      logs = [{ message: `Command failed: ${value}`, kind: 'error' }, ...logs];
      return;
    }

    const loaded = exit.value;
    if ('repoLabel' in loaded) {
      selection = null;
      selectionPanel = null;
      source = loaded.source;
      logs = [{ message: `Imported ${loaded.path} from ${loaded.repoLabel}.`, kind: 'success' }, ...logs];
      return;
    }

    selection = null;
    selectionPanel = null;
    source = loaded.source;
    query = loaded.query;
    logs = [{ message: `Loaded example ${loaded.label}.`, kind: 'success' }, ...logs];
  }
</script>

<svelte:head>
  <title>Try Krueger</title>
  <meta
    name="description"
    content="Static shell for the Krueger compiler playground (WASM and JavaScript backends)."
  />
</svelte:head>

<SiteHeader
  commandActions={commandSurfaceActions}
  commandValue={commandText}
  onCommandInput={(next) => {
    commandText = next;
  }}
  onCommandSubmit={async (value) => {
    await runCommand(value);
    commandText = value;
  }}
/>

<main class="playground-shell">
  <section
    class="workspace"
    aria-label="Source workspace"
    style={`--editor-percent:${editorPercent}; --utility-percent:${utilityPercent};`}
  >
    <PaneResizeHandle
      value={editorPercent}
      label="Resize workspace panels"
      orientation="vertical"
      onAdjust={(next) => {
        editorPercent = next;
      }}
    />

    <div class="center-stack">
      <div class="editor-explorer-grid">
        <ActivityBar
          {selectedPanel}
          onSelect={(panel) => {
            selectedPanel = panel;
          }}
        />
        <EditorGroup
          {source}
          {query}
          onSourceChange={(next) => {
            selection = null;
            selectionPanel = null;
            source = next;
          }}
          onQueryChange={(next) => {
            selection = null;
            selectionPanel = null;
            query = next;
          }}
        />
        <div class="explorer-column">
          <ExplorerToolbar
            examples={[...playgroundExamples]}
            onExampleClick={applyExample}
            onGithubClick={() => {
              commandText = 'github https://github.com/owner/repo';
            }}
          />
          <ExplorerPane
            {selectedPanel}
            {cstResult}
            {astResult}
            {cstUnistResult}
            {astUnistResult}
            {matchResult}
            {queryResult}
            {prettyQuery}
            {backend}
            {wasmGcSupported}
            onBackendChange={handleBackendChange}
            {selection}
            {selectionPanel}
            onSelectNode={(next) => {
              selection = next;
              selectionPanel = selectedPanel;
            }}
          />
        </div>
      </div>

      <PaneResizeHandle
        value={utilityPercent}
        label="Resize output panels"
        orientation="horizontal"
        onAdjust={(next) => {
          utilityPercent = next;
        }}
      />

      <section class="utility-shell" aria-label="Output utility panel">
        <UtilityPanel {logs} {problems} />
      </section>
    </div>

    <InspectorPanel {selection} />
  </section>
</main>

<style>
  :global(body) {
    margin: 0;
    color: var(--kr-text);
    background: var(--kr-bg);
    font-family:
      Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont,
      "Segoe UI", sans-serif;
  }

  /* Theme tokens are declared on :root so SiteHeader (which lives outside
     `.playground-shell`) can read the same variables. The `data-theme`
     attribute is set by the inline bootstrap in `app.html` before paint,
     using the same `starlight-theme` localStorage key the Astro pages
     write — so dark/light tracks the rest of the docs site.

     HSL values mirror Starlight's `--sl-color-*` palette (extracted from
     `_astro/common.*.css`) so the playground reads as the same site as
     the surrounding docs and `/krueger/api/`. Hue 224 is Starlight's
     default slate-blue gray. */
  :global(:root) {
    color-scheme: dark;

    /* Starlight: --sl-color-black (page bg in dark mode) */
    --kr-bg: hsl(224, 10%, 10%);
    /* Starlight: --sl-color-gray-6 (used as bg-nav / bg-sidebar) */
    --kr-panel-bg: hsl(224, 14%, 16%);
    /* One step darker for the inner activity rail / strip */
    --kr-panel-bg-strong: hsl(224, 12%, 13%);
    /* Editor surface: slightly darker than the page bg for visual depth */
    --kr-editor-bg: hsl(224, 10%, 8%);
    /* Starlight: --sl-color-gray-5 (hairline shade) */
    --kr-border: hsl(224, 10%, 23%);
    /* Starlight: --sl-color-gray-2 (= --sl-color-text) */
    --kr-text: hsl(224, 6%, 77%);
    /* Starlight: --sl-color-gray-3 / gray-4 mid-tone for de-emphasised UI */
    --kr-muted: hsl(224, 6%, 56%);
    /* Starlight default accent (--sl-color-accent in dark mode) */
    --kr-accent: hsl(224, 100%, 60%);
    --kr-accent-soft: hsl(224, 100%, 85%);
    --kr-tree-value: var(--kr-accent-soft);
    /* Brand text color — mirrors Starlight's `--sl-color-text-accent`,
       which resolves to the soft lavender on dark and the saturated
       accent on light. Drives the `Krueger` wordmark in SiteHeader. */
    --kr-brand: var(--kr-accent-soft);
    --kr-error-bg: hsl(0, 60%, 18%);
    --kr-error-border: hsl(0, 84%, 60%);
    --kr-error-text: hsl(0, 92%, 86%);
    --kr-header-h: 3.75rem;
  }

  :global(:root[data-theme='light']) {
    color-scheme: light;

    /* Starlight light: --sl-color-black = white = page bg */
    --kr-bg: hsl(0, 0%, 100%);
    /* Starlight light: --sl-color-gray-7 (lightest panel) */
    --kr-panel-bg: hsl(224, 19%, 97%);
    /* Starlight light: --sl-color-gray-6 (slightly stronger panel) */
    --kr-panel-bg-strong: hsl(224, 20%, 94%);
    --kr-editor-bg: hsl(0, 0%, 100%);
    /* Starlight light: --sl-color-gray-5 hairline */
    --kr-border: hsl(224, 6%, 77%);
    /* Starlight light: --sl-color-gray-2 (text) */
    --kr-text: hsl(224, 10%, 23%);
    --kr-muted: hsl(224, 7%, 36%);
    --kr-accent: hsl(234, 90%, 60%);
    --kr-accent-soft: hsl(234, 88%, 90%);
    --kr-tree-value: hsl(234, 68%, 56%);
    /* In light mode the soft variant is too pale on white; pull from the
       saturated accent instead so the brand stays readable. */
    --kr-brand: var(--kr-accent);
    --kr-error-bg: hsl(0, 86%, 97%);
    --kr-error-border: hsl(0, 84%, 60%);
    --kr-error-text: hsl(0, 70%, 35%);
  }

  .playground-shell {
    /* Page-level chrome lives in SiteHeader; the playground itself fills
       the remainder of the viewport edge-to-edge — no inner heading or
       padding band on top of the workspace. */
    min-height: calc(100vh - var(--kr-header-h));
    display: grid;
    padding: 0;
    background: var(--kr-bg);
  }

  .workspace {
    display: grid;
    grid-template-columns:
      auto
      minmax(0, calc(var(--editor-percent) * 1%))
      minmax(18rem, calc(100% - (var(--editor-percent) * 1%)));
    grid-template-areas: "center-handle center inspector";
    min-height: calc(100vh - var(--kr-header-h));
    overflow: hidden;
    border-top: 0;
    border-bottom: 1px solid var(--kr-border);
  }

  .workspace > :global(.resize-handle) {
    grid-area: center-handle;
    min-height: 100%;
    cursor: col-resize;
    border-inline: 1px solid var(--kr-border);
  }

  .center-stack {
    grid-area: center;
    display: grid;
    grid-template-rows:
      minmax(0, calc(var(--utility-percent) * 1%))
      auto
      minmax(9rem, calc(100% - (var(--utility-percent) * 1%)));
    min-width: 0;
    min-height: 0;
    overflow: hidden;
  }

  .center-stack > :global(.resize-handle) {
    min-width: 100%;
    cursor: row-resize;
    border-block: 1px solid var(--kr-border);
  }

  .editor-explorer-grid {
    display: grid;
    grid-template-columns: auto minmax(28rem, 1.15fr) minmax(22rem, 0.85fr);
    min-width: 0;
    min-height: 0;
    overflow: hidden;
  }

  .explorer-column {
    display: grid;
    grid-template-rows: auto minmax(0, 1fr);
    min-width: 0;
    min-height: 0;
    overflow: hidden;
  }

  .utility-shell {
    min-height: 0;
    overflow: hidden;
  }

  :global(.inspector-panel) {
    grid-area: inspector;
  }

  @media (max-width: 1100px) {
    .workspace {
      grid-template-columns: minmax(0, 1fr);
      grid-template-areas:
        "center"
        "inspector";
    }

    .workspace > :global(.resize-handle) {
      display: none;
    }

    :global(.inspector-panel) {
      min-width: 0;
    }
  }
</style>
