<svelte:head>
  <title>Try Krueger WASM</title>
  <meta
    name="description"
    content="Static shell for the Krueger WebAssembly playground."
  />
</svelte:head>

<main class="shell">
  <p class="eyebrow">Try Krueger</p>
  <h1>WASM playground</h1>
  <p>
    This static SvelteKit shell is ready for the Krueger compiler artifacts
    written by <code>./mill krueger.webapp-wasm.writeToWasmSite</code>.
  </p>
  <p class="status">Interactive editor components land in the next slice.</p>
</main>

<style>
  :global(body) {
    margin: 0;
    color: #e5e7eb;
    background: #111827;
    font-family:
      Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont,
      "Segoe UI", sans-serif;
  }

  .shell {
    min-height: 100vh;
    display: grid;
    align-content: center;
    gap: 1rem;
    max-width: 48rem;
    margin: 0 auto;
    padding: 2rem;
  }

  .eyebrow {
    margin: 0;
    color: #93c5fd;
    font-size: 0.875rem;
    font-weight: 700;
    letter-spacing: 0.12em;
    text-transform: uppercase;
  }

  h1 {
    margin: 0;
    font-size: clamp(2.5rem, 10vw, 5rem);
    line-height: 0.95;
  }

  p {
    margin: 0;
    color: #cbd5e1;
    font-size: 1.125rem;
    line-height: 1.7;
  }

  code {
    color: #bfdbfe;
  }

  .status {
    color: #9ca3af;
  }
</style>
