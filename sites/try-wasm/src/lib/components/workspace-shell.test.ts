// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/svelte';
import { afterEach, describe, expect, it, vi } from 'vitest';

import Page from '../../routes/+page.svelte';

vi.mock('$lib/krueger', async () => {
  const ok = (value: unknown) => ({ ok: true, value, logs: [], errors: [] });
  return {
    createKruegerClient: vi.fn(async () => ({
      backend: 'js',
      parseCst: () => ok({}),
      parseAst: () => ok({}),
      parseCstUnist: () => ok({ type: 'CstModule', data: { fields: {}, childCount: 1 }, children: [] }),
      parseAstUnist: () => ok({ type: 'Module', data: { fields: {}, childCount: 1 }, children: [] }),
      parseQuery: () => ok({}),
      runQuery: () => ok([]),
      prettyQuery: () => '(CstValueDeclaration) @decl',
      tokenize: () => ok([])
    }))
  };
});

describe('playground workspace shell', () => {
  afterEach(() => cleanup());

  it('renders a command surface, explorer, inspector, and utility panel together', () => {
    render(Page);

    expect(screen.getByRole('combobox', { name: 'Playground command' })).not.toBeNull();
    expect(screen.getByRole('region', { name: 'Source workspace' })).not.toBeNull();
    expect(screen.getByRole('region', { name: 'Selection inspector' })).not.toBeNull();
    expect(screen.getByRole('tablist', { name: 'Output panels' })).not.toBeNull();
  });

  it('exposes keyboard-accessible splitters for the center and bottom panes', async () => {
    render(Page);

    const splitter = screen.getAllByRole('separator')[0] as HTMLElement;
    expect(splitter.getAttribute('aria-valuenow')).toBe('62');

    await fireEvent.keyDown(splitter, { key: 'ArrowLeft' });

    expect(splitter.getAttribute('aria-valuenow')).toBe('60');
  });
});
