// @vitest-environment jsdom

import { render, screen } from '@testing-library/svelte';
import { describe, expect, it, vi } from 'vitest';

import Page from './+page.svelte';
import { supportsWasmGc } from '$lib/wasm-gc';

vi.mock('$lib/wasm-gc', async () => {
  const actual = await vi.importActual<typeof import('$lib/wasm-gc')>('$lib/wasm-gc');
  return {
    ...actual,
    supportsWasmGc: vi.fn()
  };
});

const mockedSupportsWasmGc = vi.mocked(supportsWasmGc);

describe('/try-wasm Wasm GC fallback banner', () => {
  it('renders no fallback banner when the browser supports Wasm GC', () => {
    mockedSupportsWasmGc.mockReturnValue(true);

    render(Page);

    expect(screen.queryByRole('status')).toBeNull();
    expect(screen.getByText(/Interactive editor components land/i)).not.toBeNull();
  });

  it('renders an accessible fallback banner with a Laminar playground link when unsupported', () => {
    mockedSupportsWasmGc.mockReturnValue(false);

    render(Page);

    const banner = screen.getByRole('status');
    expect(banner.textContent).toContain('This browser does not support WebAssembly GC');
    expect(banner.textContent).toContain('Chrome 119+, Firefox 120+, or Safari 18.2+');
    expect(screen.getByRole('link', { name: /Open Try Krueger/i }).getAttribute('href')).toBe('/try/');
  });
});
