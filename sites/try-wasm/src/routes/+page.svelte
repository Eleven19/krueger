<script lang="ts">
  import { onMount } from "svelte";

  import ActivityBar from "$lib/components/ActivityBar.svelte";
  import BackendSelect from "$lib/components/BackendSelect.svelte";
  import EditorGroup from "$lib/components/EditorGroup.svelte";
  import ResultsPanel from "$lib/components/ResultsPanel.svelte";
  import {
    backendInfo,
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
  import { defaultPanel, type Panel } from "$lib/panels";
  import {
    shouldLoadWasmCompiler,
    supportsWasmGc,
    wasmGcRequirementsText
  } from "$lib/wasm-gc";

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
  let backendSwitchTick = $state(0);

  const canInitializeCompiler = $derived(shouldLoadWasmCompiler(wasmGcSupported));
  const cstResult = $derived(
    compilerEnvelope(() => {
      void backendSwitchTick;
      return client?.parseCst(source);
    }, "Compiler loading...")
  );
  const astResult = $derived(
    compilerEnvelope(() => {
      void backendSwitchTick;
      return client?.parseAst(source);
    }, "Compiler loading...")
  );
  const queryResult = $derived(
    compilerEnvelope(() => {
      void backendSwitchTick;
      return client?.parseQuery(query);
    }, "Compiler loading...")
  );
  const matchResult = $derived(computeMatches(client, cstResult, queryResult));
  const prettyQuery = $derived(
    client && queryResult.ok && queryResult.value != null
      ? client.prettyQuery(queryResult.value)
      : ""
  );
  const activeBackendInfo = $derived(backendInfo(backend));

  onMount(() => {
    const supported = supportsWasmGc();
    wasmGcSupported = supported;

    // Reconcile the initial backend choice with the just-resolved support
    // probe. If the page started in WASM mode but the host can't run it,
    // fall back to the JS backend before any compile call goes through.
    if (!isAvailable(backend, supported)) {
      backend = fallbackBackend;
    }

    void createKruegerClient()
      .then((loaded) => {
        client = loaded;
        loaded.setBackend(backend);
      })
      .catch((error: unknown) => {
        compilerLoadError = error instanceof Error ? error.message : String(error);
      });
  });

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
    backend = next;
    if (client !== null) {
      const accepted = client.setBackend(next);
      if (!accepted) {
        compilerLoadError = `Backend "${next}" rejected by the loaded compiler facade.`;
        return;
      }
    }
    // Bump the recompute key so $derived expressions re-run their reads
    // even when the editor inputs are unchanged — a backend swap should
    // refresh every panel.
    backendSwitchTick += 1;
  }
</script>

<svelte:head>
  <title>Try Krueger</title>
  <meta
    name="description"
    content="Static shell for the Krueger compiler playground (WASM and JavaScript backends)."
  />
</svelte:head>

<main class="playground-shell">
  {#if wasmGcSupported === false}
    <aside class="fallback" role="status" aria-live="polite">
      <strong>This browser does not support WebAssembly GC.</strong>
      <span>
        Krueger needs {wasmGcRequirementsText} for the WASM backend. The
        JavaScript backend is selected automatically and works in every
        modern browser.
      </span>
    </aside>
  {/if}

  <header class="app-header">
    <div>
      <p class="eyebrow">Try Krueger</p>
      <h1>Compiler playground</h1>
    </div>
    <div class="app-header-trail">
      <p>
        Paste Elm source, write a Krueger query, and inspect matches, CST,
        AST, and the canonical query echo. Pick a compiler backend below.
      </p>
      <div class="backend-row">
        <BackendSelect
          selected={backend}
          {wasmGcSupported}
          onSelect={handleBackendChange}
        />
        <span class="backend-hint">{activeBackendInfo.description}</span>
      </div>
    </div>
  </header>

  <section class="workspace" aria-label="Try Krueger workspace">
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
        source = next;
      }}
      onQueryChange={(next) => {
        query = next;
      }}
    />
    <ResultsPanel
      {selectedPanel}
      {cstResult}
      {astResult}
      {matchResult}
      {queryResult}
      {prettyQuery}
    />
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

  .playground-shell {
    --kr-bg: var(--sl-color-bg, #0f172a);
    --kr-panel-bg: var(--sl-color-bg-sidebar, #111827);
    --kr-panel-bg-strong: var(--sl-color-bg-nav, #1f2937);
    --kr-editor-bg: var(--sl-color-black, #020617);
    --kr-border: var(--sl-color-gray-5, #334155);
    --kr-text: var(--sl-color-white, #e5e7eb);
    --kr-muted: var(--sl-color-gray-2, #94a3b8);
    --kr-accent: var(--sl-color-accent-high, #60a5fa);
    --kr-accent-soft: var(--sl-color-accent-low, #bfdbfe);
    --kr-error-bg: #451a1a;
    --kr-error-border: #ef4444;
    --kr-error-text: #fecaca;

    min-height: 100vh;
    display: grid;
    gap: 1rem;
    padding: 2rem;
    background: var(--kr-bg);
  }

  .fallback {
    display: grid;
    gap: 0.75rem;
    padding: 1rem;
    color: #111827;
    background: #fef3c7;
    border: 1px solid #f59e0b;
    border-radius: 0.75rem;
  }

  .fallback span {
    color: #374151;
    line-height: 1.5;
  }

  .app-header {
    display: flex;
    align-items: end;
    justify-content: space-between;
    gap: 2rem;
    flex-wrap: wrap;
  }

  .app-header > div:first-child {
    display: grid;
    gap: 0.35rem;
  }

  .app-header-trail {
    display: grid;
    gap: 0.75rem;
    justify-items: end;
    max-width: 36rem;
  }

  .backend-row {
    display: flex;
    align-items: center;
    gap: 1rem;
    flex-wrap: wrap;
    justify-content: flex-end;
  }

  .backend-hint {
    color: var(--kr-muted);
    font-size: 0.8rem;
    line-height: 1.4;
  }

  .eyebrow {
    margin: 0;
    color: var(--kr-accent);
    font-size: 0.875rem;
    font-weight: 700;
    letter-spacing: 0.12em;
    text-transform: uppercase;
  }

  h1 {
    margin: 0;
    font-size: clamp(2rem, 5vw, 3.5rem);
    line-height: 0.95;
  }

  p {
    margin: 0;
    max-width: 42rem;
    color: var(--kr-muted);
    font-size: 1rem;
    line-height: 1.7;
  }

  .workspace {
    display: grid;
    grid-template-columns: auto minmax(28rem, 1.15fr) minmax(22rem, 0.85fr);
    min-height: calc(100vh - 12rem);
    overflow: hidden;
    border: 1px solid var(--kr-border);
    border-radius: 0.875rem;
    box-shadow: 0 24px 80px rgb(0 0 0 / 0.24);
  }
</style>
