<script lang="ts">
  import { onMount } from 'svelte';

  import {
    applyTheme,
    getStoredPreference,
    setStoredPreference,
    type ThemePreference
  } from '$lib/theme';

  let preference = $state<ThemePreference>('auto');

  onMount(() => {
    preference = getStoredPreference();
    // Re-apply on mount in case the inline bootstrap in app.html ran with
    // a stale value (e.g. cross-tab change). Idempotent.
    applyTheme(preference);

    // Cross-tab sync: when another tab writes the preference, this tab
    // mirrors the change. The browser does NOT fire `storage` for the
    // tab that performed the write, so we don't loop.
    const onStorage = (event: StorageEvent) => {
      if (event.key !== 'starlight-theme') return;
      const next = (event.newValue as ThemePreference | null) ?? 'auto';
      preference = next;
      applyTheme(next);
    };
    window.addEventListener('storage', onStorage);
    return () => window.removeEventListener('storage', onStorage);
  });

  function handleChange(event: Event) {
    const next = (event.currentTarget as HTMLSelectElement).value as ThemePreference;
    preference = next;
    setStoredPreference(next);
    applyTheme(next);
  }
</script>

<label class="theme-toggle">
  <span class="sr-only">Theme</span>
  <svg
    aria-hidden="true"
    class="theme-icon"
    width="16"
    height="16"
    viewBox="0 0 24 24"
    fill="currentColor"
  >
    <!-- Mirrors the monitor icon Starlight ships in `<starlight-theme-select>`. -->
    <path
      d="M21 14h-1V7a3 3 0 0 0-3-3H7a3 3 0 0 0-3 3v7H3a1 1 0 0 0-1 1v2a3 3 0 0 0 3 3h14a3 3 0 0 0 3-3v-2a1 1 0 0 0-1-1ZM6 7a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1v7H6V7Zm14 10a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1v-1h16v1Z"
    />
  </svg>
  <select
    class="theme-control"
    aria-label="Select theme"
    value={preference}
    onchange={handleChange}
  >
    <option value="auto">Auto</option>
    <option value="dark">Dark</option>
    <option value="light">Light</option>
  </select>
  <svg
    aria-hidden="true"
    class="theme-caret"
    width="12"
    height="12"
    viewBox="0 0 24 24"
    fill="currentColor"
  >
    <path d="M17 9.17a1 1 0 0 0-1.41 0L12 12.71 8.46 9.17a1 1 0 1 0-1.41 1.42l4.24 4.24a1.002 1.002 0 0 0 1.42 0L17 10.59a1.002 1.002 0 0 0 0-1.42Z" />
  </svg>
</label>

<style>
  .theme-toggle {
    display: inline-flex;
    align-items: center;
    gap: 0.4rem;
    padding: 0.25rem 0.5rem;
    color: var(--kr-muted);
    border-radius: 0.375rem;
    cursor: pointer;
  }
  .theme-toggle:hover,
  .theme-toggle:focus-within {
    color: var(--kr-text);
    background: var(--kr-panel-bg-strong);
  }

  .theme-icon,
  .theme-caret {
    flex: 0 0 auto;
  }

  .theme-control {
    appearance: none;
    border: 0;
    padding: 0 0.25rem;
    color: inherit;
    background: transparent;
    font-size: 0.85rem;
    font-family: inherit;
    cursor: pointer;
  }
  .theme-control:focus-visible {
    outline: none;
  }

  .sr-only {
    position: absolute;
    width: 1px;
    height: 1px;
    margin: -1px;
    padding: 0;
    overflow: hidden;
    clip: rect(0, 0, 0, 0);
    white-space: nowrap;
    border: 0;
  }
</style>
