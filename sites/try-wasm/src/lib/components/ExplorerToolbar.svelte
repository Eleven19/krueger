<script lang="ts">
  import type { PlaygroundExample } from '$lib/playground/types';

  let {
    examples,
    onExampleClick,
    onGithubClick
  }: {
    examples: PlaygroundExample[];
    onExampleClick: (exampleId: string) => void;
    onGithubClick: () => void;
  } = $props();
</script>

<div class="explorer-toolbar" aria-label="Explorer tools">
  {#each examples.slice(0, 2) as example}
    <button type="button" onclick={() => onExampleClick(example.id)}>
      Load {example.label}
    </button>
  {/each}
  <button type="button" onclick={onGithubClick}>Import From GitHub</button>
</div>

<style>
  .explorer-toolbar {
    display: flex;
    flex-wrap: wrap;
    gap: 0.5rem;
    padding: 0.75rem 1rem;
    background: var(--kr-panel-bg);
    border-left: 1px solid var(--kr-border);
    border-bottom: 1px solid var(--kr-border);
  }

  button {
    min-height: 2rem;
    padding: 0 0.75rem;
    color: var(--kr-text);
    background: var(--kr-panel-bg-strong);
    border: 1px solid var(--kr-border);
    border-radius: 999px;
    font: inherit;
    cursor: pointer;
  }

  button:hover {
    border-color: var(--kr-accent);
  }
</style>
