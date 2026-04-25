// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/svelte';
import { afterEach, describe, expect, it, vi } from 'vitest';

import Page from './+page.svelte';
import { supportsWasmGc } from '$lib/wasm-gc';
import type { BackendId } from '$lib/backend';

vi.mock('$lib/krueger', async () => {
  const ok = (value: unknown) => ({ ok: true, value, logs: [], errors: [] });
  return {
    createKruegerClient: vi.fn(async (backend: BackendId) => ({
      backend,
      parseCst: () => ok('CstModule(...)'),
      parseAst: () => ok('Module(...)'),
      parseQuery: () => ok({}),
      runQuery: () => ok([]),
      prettyQuery: () => '(CstValueDeclaration) @decl',
      tokenize: () => ok([])
    }))
  };
});

vi.mock('$lib/wasm-gc', async () => {
  const actual = await vi.importActual<typeof import('$lib/wasm-gc')>('$lib/wasm-gc');
  return {
    ...actual,
    supportsWasmGc: vi.fn()
  };
});

const mockedSupportsWasmGc = vi.mocked(supportsWasmGc);

describe('/try-wasm Wasm GC fallback banner', () => {
  afterEach(() => {
    cleanup();
  });

  it('renders no fallback banner when the browser supports Wasm GC', () => {
    mockedSupportsWasmGc.mockReturnValue(true);

    render(Page);

    expect(screen.queryByRole('status')).toBeNull();
    expect(screen.getByRole('tablist', { name: 'Try Krueger results' })).not.toBeNull();
    expect(screen.getByRole('tabpanel', { name: 'Matches' })).not.toBeNull();
    expect(screen.getByRole('combobox', { name: 'Compiler backend' })).not.toBeNull();
  });

  it('renders an accessible fallback banner pointing at the JS backend when WASM-GC is unsupported', () => {
    mockedSupportsWasmGc.mockReturnValue(false);

    render(Page);

    const banner = screen.getByRole('status');
    expect(banner.textContent).toContain('This browser does not support WebAssembly GC');
    expect(banner.textContent).toContain('Chrome 119+, Firefox 120+, or Safari 18.2+');
    // The banner no longer links to the legacy Laminar /try/ playground.
    expect(banner.querySelector('a')).toBeNull();

    const select = screen.getByRole<HTMLSelectElement>('combobox', {
      name: 'Compiler backend'
    });
    expect(select.value).toBe('js');
    const wasmOption = Array.from(select.options).find((o) => o.value === 'webgc');
    expect(wasmOption?.disabled).toBe(true);
  });
});
