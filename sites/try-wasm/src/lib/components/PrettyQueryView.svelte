<script lang="ts">
  import type { CompilerEnvelope } from '$lib/krueger';

  let {
    queryResult,
    prettyQuery
  }: {
    queryResult: CompilerEnvelope<unknown>;
    prettyQuery: string;
  } = $props();
</script>

<section class="pretty-query">
  {#if !queryResult.ok}
    <div class="error-card">
      <strong>Query parse errors:</strong>
      <ul>
        {#each queryResult.errors as error}
          <li>{error.message}</li>
        {/each}
      </ul>
    </div>
  {:else}
    <pre>{prettyQuery}</pre>
  {/if}
</section>

<style>
  pre,
  .error-card {
    margin: 0;
    padding: 0.875rem;
    overflow: auto;
    background: var(--kr-panel-bg-strong);
    border: 1px solid var(--kr-border);
    border-radius: 0.625rem;
  }

  pre {
    color: var(--kr-text);
    white-space: pre-wrap;
  }

  .error-card {
    color: var(--kr-error-text);
    background: var(--kr-error-bg);
    border-color: var(--kr-error-border);
  }
</style>
