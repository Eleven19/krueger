<script lang="ts">
  import { onMount } from "svelte";

  import ActivityBar from "$lib/components/ActivityBar.svelte";
  import EditorGroup from "$lib/components/EditorGroup.svelte";
  import ResultsPanel from "$lib/components/ResultsPanel.svelte";
  import SiteHeader from "$lib/components/SiteHeader.svelte";
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
    backend = next;
    // Drop the current client so panels show the "Compiler loading..."
    // placeholder while the new backend's facade is dynamic-imported.
    client = null;
    void loadBackend(next);
  }
</script>

<svelte:head>
  <title>Try Krueger</title>
  <meta
    name="description"
    content="Static shell for the Krueger compiler playground (WASM and JavaScript backends)."
  />
</svelte:head>

<SiteHeader centerTitle="Try Krueger" />

<main class="playground-shell">
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
      {backend}
      {wasmGcSupported}
      onBackendChange={handleBackendChange}
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
    grid-template-columns: auto minmax(28rem, 1.15fr) minmax(22rem, 0.85fr);
    min-height: calc(100vh - var(--kr-header-h));
    overflow: hidden;
    border-top: 0;
    border-bottom: 1px solid var(--kr-border);
  }
</style>
