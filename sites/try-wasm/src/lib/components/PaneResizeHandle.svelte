<script lang="ts">
  import { onMount } from 'svelte';

  let {
    value,
    min = 20,
    max = 80,
    label,
    orientation = 'horizontal',
    onAdjust
  }: {
    value: number;
    min?: number;
    max?: number;
    label: string;
    orientation?: 'horizontal' | 'vertical';
    onAdjust: (next: number) => void;
  } = $props();

  function bump(delta: number): void {
    onAdjust(Math.min(max, Math.max(min, value + delta)));
  }

  let element = $state<HTMLDivElement | null>(null);

  function handleKeydown(event: KeyboardEvent): void {
    if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') bump(-2);
    if (event.key === 'ArrowRight' || event.key === 'ArrowDown') bump(2);
  }

  onMount(() => {
    element?.addEventListener('keydown', handleKeydown);

    return () => {
      element?.removeEventListener('keydown', handleKeydown);
    };
  });
</script>

<!-- svelte-ignore a11y_no_noninteractive_tabindex a11y_no_noninteractive_element_interactions -->
<div
  bind:this={element}
  class="resize-handle"
  role="separator"
  tabindex="0"
  aria-label={label}
  aria-orientation={orientation}
  aria-valuemin={min}
  aria-valuemax={max}
  aria-valuenow={value}
></div>

<style>
  .resize-handle {
    position: relative;
    min-width: 0.8rem;
    min-height: 0.8rem;
    background:
      linear-gradient(
        180deg,
        transparent 0,
        transparent 35%,
        color-mix(in srgb, var(--kr-border) 90%, white 10%) 35%,
        color-mix(in srgb, var(--kr-border) 90%, white 10%) 65%,
        transparent 65%,
        transparent 100%
      );
    outline: none;
  }

  .resize-handle:hover,
  .resize-handle:focus-visible {
    background:
      linear-gradient(
        180deg,
        transparent 0,
        transparent 35%,
        var(--kr-accent) 35%,
        var(--kr-accent) 65%,
        transparent 65%,
        transparent 100%
      );
  }
</style>
