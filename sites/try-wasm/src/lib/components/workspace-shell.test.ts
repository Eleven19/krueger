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

    const splitter = screen.getByRole('separator', { name: 'Resize workspace panels' }) as HTMLElement;
    expect(splitter.getAttribute('aria-valuenow')).toBe('62');
    expect(splitter.getAttribute('aria-orientation')).toBe('vertical');

    await fireEvent.keyDown(splitter, { key: 'ArrowLeft' });

    expect(splitter.getAttribute('aria-valuenow')).toBe('60');

    const utilitySplitter = screen.getByRole('separator', { name: 'Resize output panels' });
    expect(utilitySplitter.getAttribute('aria-valuenow')).toBe('76');
    expect(utilitySplitter.getAttribute('aria-orientation')).toBe('horizontal');

    await fireEvent.keyDown(utilitySplitter, { key: 'ArrowLeft' });

    expect(utilitySplitter.getAttribute('aria-valuenow')).toBe('76');

    await fireEvent.keyDown(utilitySplitter, { key: 'ArrowUp' });

    expect(utilitySplitter.getAttribute('aria-valuenow')).toBe('74');
  });

  it('keeps the command search field available without a JS resize dependency for stacked layout', () => {
    window.innerWidth = 900;
    const addEventListenerSpy = vi.spyOn(window, 'addEventListener');
    render(Page);

    expect(screen.getByRole('searchbox', { name: 'Playground command' })).not.toBeNull();
    expect(addEventListenerSpy).not.toHaveBeenCalledWith('resize', expect.any(Function));

    const utilitySplitter = screen.getByRole('separator', { name: 'Resize output panels' });
    expect(utilitySplitter.getAttribute('aria-orientation')).toBe('horizontal');

    addEventListenerSpy.mockRestore();
  });

  it('switches the placeholder output panel tabs cleanly', async () => {
    window.innerWidth = 1280;
    render(Page);

    const logsTab = screen.getByRole('tab', { name: 'Logs' });
    const problemsTab = screen.getByRole('tab', { name: 'Problems' });

    expect(logsTab.getAttribute('aria-selected')).toBe('true');
    expect(screen.getByRole('tabpanel', { name: 'Logs' })).not.toBeNull();

    await fireEvent.click(problemsTab);

    expect(problemsTab.getAttribute('aria-selected')).toBe('true');
    expect(logsTab.getAttribute('aria-selected')).toBe('false');
    expect(screen.getByRole('tabpanel', { name: 'Problems' })).not.toBeNull();
  });
});
