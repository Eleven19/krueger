<script lang="ts">
  import { backends, isAvailable, type BackendId } from '$lib/backend';

  let {
    selected,
    wasmGcSupported,
    onSelect
  }: {
    selected: BackendId;
    wasmGcSupported: boolean | null;
    onSelect: (backend: BackendId) => void;
  } = $props();
</script>

<label class="backend-select">
  <span class="backend-label">Backend</span>
  <select
    class="backend-control"
    aria-label="Compiler backend"
    value={selected}
    onchange={(event) => onSelect((event.currentTarget as HTMLSelectElement).value as BackendId)}
  >
    {#each backends as option}
      <option value={option.id} disabled={!isAvailable(option.id, wasmGcSupported)}>
        {option.label}{!isAvailable(option.id, wasmGcSupported) ? ' (unavailable)' : ''}
      </option>
    {/each}
  </select>
</label>

<style>
  .backend-select {
    display: inline-flex;
    align-items: center;
    gap: 0.5rem;
    color: var(--kr-muted);
    font-size: 0.85rem;
  }

  .backend-label {
    font-weight: 700;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: var(--kr-muted);
  }

  .backend-control {
    appearance: none;
    padding: 0.4rem 1.75rem 0.4rem 0.75rem;
    color: var(--kr-text);
    background-color: var(--kr-panel-bg-strong);
    background-image:
      linear-gradient(45deg, transparent 50%, var(--kr-muted) 50%),
      linear-gradient(135deg, var(--kr-muted) 50%, transparent 50%);
    background-position:
      calc(100% - 0.95rem) 50%,
      calc(100% - 0.55rem) 50%;
    background-size:
      0.4rem 0.4rem,
      0.4rem 0.4rem;
    background-repeat: no-repeat;
    border: 1px solid var(--kr-border);
    border-radius: 0.5rem;
    cursor: pointer;
    font-size: 0.9rem;
    line-height: 1.2;
  }

  .backend-control:focus-visible {
    outline: 2px solid var(--kr-accent);
    outline-offset: 2px;
  }
</style>
