<script lang="ts">
  import BackendSelect from './BackendSelect.svelte';
  import { backendInfo, type BackendId } from '$lib/backend';

  let {
    backend,
    wasmGcSupported,
    onBackendChange
  }: {
    backend: BackendId;
    wasmGcSupported: boolean | null;
    onBackendChange: (next: BackendId) => void;
  } = $props();

  const activeInfo = $derived(backendInfo(backend));
</script>

<div class="settings-panel" role="region" aria-label="Settings">
  <h2 class="settings-title">Settings</h2>

  <section class="settings-section" aria-labelledby="settings-backend-label">
    <header class="settings-section-header">
      <h3 id="settings-backend-label">Compiler backend</h3>
      <p>Choose which Krueger build runs the parse / query operations.</p>
    </header>

    <BackendSelect
      selected={backend}
      {wasmGcSupported}
      onSelect={onBackendChange}
    />

    <p class="backend-hint">{activeInfo.description}</p>

    {#if wasmGcSupported === false}
      <p class="backend-warning">
        Your browser does not support WebAssembly GC, so the WASM backend is
        disabled. Krueger needs Chrome 119+, Firefox 120+, or Safari 18.2+ for
        the WASM compiler artifact.
      </p>
    {/if}
  </section>

  <section class="settings-section">
    <header class="settings-section-header">
      <h3>About</h3>
      <p>
        The playground links the same Scala compiler twice — once as a
        JavaScript ES module, once as a WebAssembly artifact. Switching the
        backend re-loads the corresponding facade and routes every compile
        call through that bundle. Theme and font preferences live in the
        site header.
      </p>
    </header>
  </section>
</div>

<style>
  .settings-panel {
    padding: 1.25rem 1.5rem;
    color: var(--kr-text);
  }

  .settings-title {
    margin: 0 0 1rem;
    font-size: 1.1rem;
    font-weight: 700;
    letter-spacing: 0.04em;
  }

  .settings-section {
    display: grid;
    gap: 0.65rem;
    padding: 1rem 1.1rem;
    margin-bottom: 1rem;
    background: var(--kr-panel-bg-strong);
    border: 1px solid var(--kr-border);
    border-radius: 0.625rem;
  }

  .settings-section h3 {
    margin: 0;
    font-size: 0.95rem;
    font-weight: 600;
  }

  .settings-section-header p {
    margin: 0.25rem 0 0;
    color: var(--kr-muted);
    font-size: 0.85rem;
    line-height: 1.55;
  }

  .backend-hint {
    margin: 0;
    color: var(--kr-muted);
    font-size: 0.85rem;
    line-height: 1.55;
  }

  .backend-warning {
    margin: 0;
    padding: 0.6rem 0.75rem;
    background: var(--kr-error-bg);
    color: var(--kr-error-text);
    border: 1px solid var(--kr-error-border);
    border-radius: 0.5rem;
    font-size: 0.85rem;
    line-height: 1.5;
  }
</style>
