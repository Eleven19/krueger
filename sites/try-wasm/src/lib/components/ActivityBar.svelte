<script lang="ts">
  import { Tabs } from 'bits-ui';

  import { panels, settingsPanel, type Panel } from '$lib/panels';

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

    <span class="activity-spacer" aria-hidden="true"></span>

    <!-- Settings sits at the bottom of the rail like VS Code's gear. It
         participates in the same Tabs root so the right-hand panel can
         render the SettingsPanel when this trigger is active. -->
    <Tabs.Trigger
      class={`activity-button activity-settings ${selectedPanel === settingsPanel.id ? 'is-active' : ''}`}
      value={settingsPanel.id}
      title={settingsPanel.label}
      aria-label={settingsPanel.label}
    >
      <svg
        aria-hidden="true"
        class="activity-icon-svg"
        width="18"
        height="18"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="1.6"
        stroke-linecap="round"
        stroke-linejoin="round"
      >
        <circle cx="12" cy="12" r="3" />
        <path
          d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09a1.65 1.65 0 0 0-1-1.51 1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09a1.65 1.65 0 0 0 1.51-1 1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33h.01a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82v.01a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1Z"
        />
      </svg>
    </Tabs.Trigger>
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

  .activity-icon-svg {
    display: block;
  }

  :global(.activity-spacer) {
    flex: 1 1 auto;
  }

  :global(.activity-settings) {
    border-top: 1px solid var(--kr-border);
    padding-top: 0.25rem;
    margin-top: 0.25rem;
  }
</style>
