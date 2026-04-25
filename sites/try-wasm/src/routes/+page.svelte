<script lang="ts">
  import { browser } from "$app/environment";
  import { onMount } from "svelte";

  import ActivityBar from "$lib/components/ActivityBar.svelte";
  import EditorGroup from "$lib/components/EditorGroup.svelte";
  import ResultsPanel from "$lib/components/ResultsPanel.svelte";
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

  const canInitializeCompiler = $derived(shouldLoadWasmCompiler(wasmGcSupported));
  const cstResult = $derived(
    compilerEnvelope(() => client?.parseCst(source), "Compiler loading...")
  );
  const astResult = $derived(
    compilerEnvelope(() => client?.parseAst(source), "Compiler loading...")
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

  onMount(() => {
    const supported = supportsWasmGc();
    wasmGcSupported = supported;

    if (shouldLoadWasmCompiler(supported)) {
      void createKruegerClient()
        .then((loaded) => {
          client = loaded;
        })
        .catch((error: unknown) => {
          compilerLoadError = error instanceof Error ? error.message : String(error);
        });
    }
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
</script>

<svelte:head>
  <title>Try Krueger WASM</title>
  <meta
    name="description"
    content="Static shell for the Krueger WebAssembly playground."
  />
</svelte:head>

<main class="playground-shell">
  {#if wasmGcSupported === false && !canInitializeCompiler}
    <aside class="fallback" role="status" aria-live="polite">
      <strong>This browser does not support WebAssembly GC.</strong>
      <span>
        Try Krueger WASM needs {wasmGcRequirementsText}. You can still use the
        Scala.js playground instead.
      </span>
      <a href="/try/">Open Try Krueger</a>
    </aside>
  {/if}

  <header class="app-header">
    <div>
      <p class="eyebrow">Try Krueger</p>
      <h1>WASM playground</h1>
    </div>
    <p>
      Paste Elm source, write a Krueger query, and inspect matches, CST, AST,
      and the canonical query echo from the shared compiler facade.
    </p>
  </header>

  <section class="workspace" aria-label="Try Krueger WASM workspace">
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

  .fallback a {
    color: #1d4ed8;
    font-weight: 700;
  }

  .app-header {
    display: flex;
    align-items: end;
    justify-content: space-between;
    gap: 2rem;
  }

  .app-header div {
    display: grid;
    gap: 0.35rem;
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
    min-height: calc(100vh - 10rem);
    overflow: hidden;
    border: 1px solid var(--kr-border);
    border-radius: 0.875rem;
    box-shadow: 0 24px 80px rgb(0 0 0 / 0.24);
  }
</style>
