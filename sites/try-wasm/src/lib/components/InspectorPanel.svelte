<script lang="ts">
  import type { TreeSelection } from '$lib/playground/types';

  let { selection }: { selection: TreeSelection | null } = $props();
</script>

<section class="inspector-panel" aria-label="Selection inspector">
  {#if selection}
    <h2>{selection.nodeType}</h2>
    <p>{selection.text ?? 'No text value'}</p>
    <dl>
      <div><dt>Path</dt><dd>{selection.path.length === 0 ? 'root' : selection.path.join('.')}</dd></div>
      <div><dt>Children</dt><dd>{selection.childCount}</dd></div>
    </dl>
  {:else}
    <p>Select a node to inspect its structure.</p>
  {/if}
</section>

<style>
  .inspector-panel {
    min-width: 18rem;
    height: 100%;
    padding: 1rem;
    background: linear-gradient(180deg, var(--kr-panel-bg), var(--kr-editor-bg));
    border-left: 1px solid var(--kr-border);
    box-sizing: border-box;
  }

  h2,
  p,
  dl {
    margin: 0;
  }

  h2 {
    margin-bottom: 0.75rem;
    font-size: 1rem;
  }

  p {
    line-height: 1.5;
  }

  dl {
    display: grid;
    gap: 0.75rem;
    margin-top: 1rem;
  }

  dl div {
    display: grid;
    gap: 0.25rem;
  }

  dt {
    color: var(--kr-muted);
    font-size: 0.8rem;
    font-weight: 700;
    letter-spacing: 0.08em;
    text-transform: uppercase;
  }

  dd {
    margin: 0;
  }

  @media (max-width: 1100px) {
    .inspector-panel {
      min-width: 0;
      min-height: 10rem;
      border-left: 0;
      border-top: 1px solid var(--kr-border);
    }
  }
</style>
