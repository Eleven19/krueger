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
  const defaultInnerWidth = window.innerWidth;

  afterEach(() => {
    window.innerWidth = defaultInnerWidth;
    cleanup();
  });

  it('renders a command search field, explorer, inspector, and utility panel together', () => {
    window.innerWidth = 1280;
    render(Page);

    expect(screen.getByRole('searchbox', { name: 'Playground command' })).not.toBeNull();
    expect(screen.queryByRole('combobox', { name: 'Playground command' })).toBeNull();
    expect(screen.getByRole('region', { name: 'Source workspace' })).not.toBeNull();
    expect(screen.getByRole('region', { name: 'Selection inspector' })).not.toBeNull();
    expect(screen.getByRole('tablist', { name: 'Output panels' })).not.toBeNull();
  });

  it('exposes keyboard-accessible splitters for the center and bottom panes', async () => {
    window.innerWidth = 1280;
    render(Page);

    const splitter = screen.getAllByRole('separator')[0] as HTMLElement;
    expect(splitter.getAttribute('aria-valuenow')).toBe('62');
    expect(splitter.getAttribute('aria-orientation')).toBe('vertical');

    await fireEvent.keyDown(splitter, { key: 'ArrowLeft' });

    expect(splitter.getAttribute('aria-valuenow')).toBe('60');
  });

  it('keeps the command search field available while removing the primary splitter in stacked layout', async () => {
    window.innerWidth = 900;
    render(Page);
    await fireEvent(window, new Event('resize'));

    expect(screen.getByRole('searchbox', { name: 'Playground command' })).not.toBeNull();
    expect(screen.queryByRole('separator', { name: 'Resize workspace panels' })).toBeNull();

    const utilitySplitter = screen.getByRole('separator', { name: 'Resize output panels' });
    expect(utilitySplitter.getAttribute('aria-orientation')).toBe('horizontal');
  });
});
