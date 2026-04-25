<script lang="ts">
  import { Tabs } from 'bits-ui';
  import type { PlaygroundDiagnostic, PlaygroundLog, UtilityTab } from '$lib/playground/types';

  let {
    logs,
    problems,
    activeTab = 'logs'
  }: {
    logs: PlaygroundLog[];
    problems: PlaygroundDiagnostic[];
    activeTab?: UtilityTab;
  } = $props();

  let currentTab = $state<UtilityTab>('logs');

  $effect(() => {
    currentTab = activeTab;
  });
</script>

<Tabs.Root class="utility-root" value={currentTab} onValueChange={(value) => (currentTab = value as UtilityTab)}>
  <Tabs.List aria-label="Output panels">
    <Tabs.Trigger value="logs">Logs</Tabs.Trigger>
    <Tabs.Trigger value="problems">Problems</Tabs.Trigger>
  </Tabs.List>
  <Tabs.Content value="logs">
    {#each logs as entry}
      <p>{entry.message}</p>
    {/each}
  </Tabs.Content>
  <Tabs.Content value="problems">
    {#if problems.length === 0}
      <p>No problems.</p>
    {:else}
      <ul>
        {#each problems as problem}
          <li>{problem.message}</li>
        {/each}
      </ul>
    {/if}
  </Tabs.Content>
</Tabs.Root>

<style>
  :global(.utility-root) {
    display: grid;
    grid-template-rows: auto minmax(0, 1fr);
    min-height: 0;
    background: linear-gradient(180deg, var(--kr-panel-bg), var(--kr-panel-bg-strong));
  }

  :global(.utility-root [role='tablist']) {
    display: flex;
    gap: 0.5rem;
    padding: 0.75rem 1rem;
    border-bottom: 1px solid var(--kr-border);
  }

  :global(.utility-root [role='tab']) {
    padding: 0.45rem 0.8rem;
    color: var(--kr-muted);
    background: transparent;
    border: 1px solid var(--kr-border);
    border-radius: 999px;
    font: inherit;
    cursor: pointer;
  }

  :global(.utility-root [role='tab'][data-state='active']) {
    color: var(--kr-text);
    background: color-mix(in srgb, var(--kr-panel-bg-strong) 86%, white 14%);
    border-color: color-mix(in srgb, var(--kr-accent) 45%, var(--kr-border));
  }

  :global(.utility-root [role='tabpanel']) {
    min-height: 0;
    padding: 1rem;
    overflow: auto;
    color: var(--kr-muted);
  }

  p,
  ul {
    margin: 0;
  }

  ul {
    padding-left: 1.25rem;
  }
</style>
