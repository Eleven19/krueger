<script lang="ts">
  import type { CompilerEnvelope } from '$lib/krueger';

  let {
    result,
    errorTitle = 'Parse errors:'
  }: {
    result: CompilerEnvelope<unknown>;
    errorTitle?: string;
  } = $props();

  const textValue = $derived(result.value == null ? '' : String(result.value));
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
  {:else}
    <pre class="tree-body">{textValue}</pre>
  {/if}
</section>

<style>
  .tree-body,
  .error-card {
    margin: 0;
    padding: 0.875rem;
    overflow: auto;
    background: var(--kr-panel-bg-strong);
    border: 1px solid var(--kr-border);
    border-radius: 0.625rem;
  }

  .tree-body {
    min-height: 16rem;
    color: var(--kr-text);
    white-space: pre-wrap;
  }

  .error-card {
    color: var(--kr-error-text);
    background: var(--kr-error-bg);
    border-color: var(--kr-error-border);
  }
</style>
