<script lang="ts">
  import { Tabs } from 'bits-ui';

  import { panels, type Panel } from '$lib/panels';

  let {
    selectedPanel,
    onSelect
  }: {
    selectedPanel: Panel;
    onSelect: (panel: Panel) => void;
  } = $props();
</script>

<Tabs.Root
  class="activity-root"
  value={selectedPanel}
  orientation="vertical"
  activationMode="manual"
  onValueChange={(value) => onSelect(value as Panel)}
>
  <Tabs.List class="activity-bar" aria-label="Try Krueger results">
    {#each panels as panel}
      <Tabs.Trigger
        class={`activity-button ${selectedPanel === panel.id ? 'is-active' : ''}`}
        value={panel.id}
        title={panel.label}
        aria-label={panel.label}
      >
        <span class="activity-icon" aria-hidden="true">{panel.icon}</span>
      </Tabs.Trigger>
    {/each}
  </Tabs.List>
</Tabs.Root>

<style>
  :global(.activity-root) {
    display: contents;
  }

  :global(.activity-bar) {
    display: flex;
    flex-direction: column;
    gap: 0.25rem;
    padding: 0.5rem 0.375rem;
    background: var(--kr-panel-bg);
    border-right: 1px solid var(--kr-border);
  }

  :global(.activity-button) {
    display: grid;
    place-items: center;
    width: 2.5rem;
    height: 2.5rem;
    color: var(--kr-muted);
    background: transparent;
    border: 0;
    border-left: 2px solid transparent;
    border-radius: 0.375rem;
    cursor: pointer;
  }

  :global(.activity-button:hover),
  :global(.activity-button.is-active),
  :global(.activity-button[data-state='active']) {
    color: var(--kr-text);
    background: var(--kr-panel-bg-strong);
    border-left-color: var(--kr-accent);
  }

  .activity-icon {
    font-size: 1.15rem;
  }
</style>
