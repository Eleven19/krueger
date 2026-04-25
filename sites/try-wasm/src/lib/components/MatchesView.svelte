<script lang="ts">
  import type { CompilerEnvelope, MatchView } from '$lib/krueger';

  let { result }: { result: CompilerEnvelope<MatchView[]> } = $props();
</script>

<section class="matches-view">
  {#if !result.ok}
    <div class="error-card">
      <strong>Query errors:</strong>
      <ul>
        {#each result.errors as error}
          <li>{error.message}</li>
        {/each}
      </ul>
    </div>
  {:else if result.value.length === 0}
    <p class="empty">No matches.</p>
  {:else}
    <ul class="matches-list">
      {#each result.value as match}
        <li class="match-card">
          <span class="match-root">({match.rootNodeType})</span>
          {#if match.rootText}
            <span class="match-text"> — {match.rootText}</span>
          {/if}
          <ul class="captures">
            {#each Object.entries(match.captures) as [name, node]}
              <li>
                <span class="capture-name">@{name}</span>
                <span class="capture-kind"> {node.nodeType}</span>
                {#if node.text}
                  <span class="capture-text"> = {node.text}</span>
                {/if}
              </li>
            {/each}
          </ul>
        </li>
      {/each}
    </ul>
  {/if}
</section>

<style>
  .matches-view,
  .matches-list,
  .captures {
    display: grid;
    gap: 0.75rem;
  }

  .matches-list,
  .captures {
    margin: 0;
    padding: 0;
    list-style: none;
  }

  .match-card,
  .error-card,
  .empty {
    margin: 0;
    padding: 0.875rem;
    background: var(--kr-panel-bg-strong);
    border: 1px solid var(--kr-border);
    border-radius: 0.625rem;
  }

  .match-root,
  .capture-name {
    color: var(--kr-accent);
    font-weight: 700;
  }

  .match-text,
  .capture-text,
  .capture-kind,
  .empty {
    color: var(--kr-muted);
  }

  .captures {
    margin-top: 0.5rem;
    padding-left: 1rem;
  }

  .error-card {
    color: var(--kr-error-text);
    background: var(--kr-error-bg);
    border-color: var(--kr-error-border);
  }
</style>
