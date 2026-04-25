// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/svelte';
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

async function openSettingsTab(): Promise<void> {
  const settingsTab = screen.getByRole('tab', { name: 'Settings' });
  await fireEvent.click(settingsTab);
}

describe('/try Wasm GC fallback banner', () => {
  afterEach(() => {
    cleanup();
  });

  it('renders no fallback banner when the browser supports Wasm GC', () => {
    mockedSupportsWasmGc.mockReturnValue(true);

    render(Page);

    expect(screen.queryByRole('status')).toBeNull();
    expect(screen.getByRole('tablist', { name: 'Try Krueger results' })).not.toBeNull();
    expect(screen.getByRole('tabpanel', { name: 'Matches' })).not.toBeNull();
    // The Settings tab is the gear at the bottom of the activity bar.
    expect(screen.getByRole('tab', { name: 'Settings' })).not.toBeNull();
  });

  it('shows the backend selector inside Settings; WASM-GC failure forces JS', async () => {
    mockedSupportsWasmGc.mockReturnValue(false);

    render(Page);

    await openSettingsTab();

    const select = await screen.findByRole<HTMLSelectElement>('combobox', {
      name: 'Compiler backend'
    });
    expect(select.value).toBe('js');
    const wasmOption = Array.from(select.options).find((o) => o.value === 'webgc');
    expect(wasmOption?.disabled).toBe(true);

    // Settings panel surfaces the WASM-GC unsupported notice in place of
    // the previous in-page fallback banner — the in-page status banner is
    // gone now, but the warning still reaches the user.
    const settingsPanel = screen.getByRole('region', { name: 'Settings' });
    expect(settingsPanel.textContent).toContain('does not support WebAssembly GC');
    expect(settingsPanel.textContent).toContain('Chrome 119+, Firefox 120+, or Safari 18.2+');
  });
});
