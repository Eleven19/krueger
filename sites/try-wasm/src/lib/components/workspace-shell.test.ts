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
    vi.restoreAllMocks();
    cleanup();
  });

  it('renders a command search field, explorer, inspector, and utility panel together', () => {
    window.innerWidth = 1280;
    render(Page);

    expect(screen.getByRole('combobox', { name: 'Playground command' })).not.toBeNull();
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

    expect(screen.getByRole('combobox', { name: 'Playground command' })).not.toBeNull();
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

  it('loads a curated example from the command surface', async () => {
    render(Page);

    const command = screen.getByRole('combobox', { name: 'Playground command' });
    await fireEvent.input(command, { target: { value: 'example elm/type-alias' } });
    await fireEvent.keyDown(command, { key: 'Enter' });
    await screen.findByText('Loaded example Elm: Type Alias.');

    expect((screen.getByRole('textbox', { name: 'Elm source' }) as HTMLTextAreaElement).value).toContain(
      'type alias User'
    );
    expect(screen.getByText('Loaded example Elm: Type Alias.')).not.toBeNull();
  });

  it('loads the same curated example from the explorer toolbar', async () => {
    render(Page);

    await fireEvent.click(screen.getByRole('button', { name: 'Load Elm: Basic Module' }));
    await screen.findByText('Loaded example Elm: Basic Module.');

    expect((screen.getByRole('textbox', { name: 'Elm source' }) as HTMLTextAreaElement).value).toContain(
      'main = 42'
    );
  });

  it('preserves the current editor content when a GitHub import fails', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('missing', { status: 404 }));

    render(Page);

    const command = screen.getByRole('combobox', { name: 'Playground command' });
    await fireEvent.input(command, {
      target: { value: 'github https://github.com/elm/core/blob/main/src/Missing.elm' }
    });
    await fireEvent.keyDown(command, { key: 'Enter' });

    expect((screen.getByRole('textbox', { name: 'Elm source' }) as HTMLTextAreaElement).value).toContain(
      'main = 42'
    );
    expect(screen.getByRole('tab', { name: 'Problems' })).not.toBeNull();
  });

  it('surfaces the real invalid-target diagnostic and preserves edited source for non-GitHub imports', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch');

    render(Page);

    const sourceEditor = screen.getByRole('textbox', { name: 'Elm source' }) as HTMLTextAreaElement;
    await fireEvent.input(sourceEditor, {
      target: { value: 'module Keep exposing (..)\n\nkeep = 99\n' }
    });

    const command = screen.getByRole('combobox', { name: 'Playground command' });
    await fireEvent.input(command, {
      target: { value: 'github https://example.com/elm/core/blob/main/src/Basics.elm' }
    });
    await fireEvent.keyDown(command, { key: 'Enter' });

    expect(sourceEditor.value).toBe('module Keep exposing (..)\n\nkeep = 99\n');
    expect(fetchSpy).not.toHaveBeenCalled();
    expect(screen.getByText('github/invalid-target: Unsupported GitHub target: https://example.com/elm/core/blob/main/src/Basics.elm')).not.toBeNull();

    await fireEvent.click(screen.getByRole('tab', { name: 'Problems' }));

    expect(screen.getByText('Unsupported GitHub target: https://example.com/elm/core/blob/main/src/Basics.elm')).not.toBeNull();
    expect(screen.queryByText('Could not complete the requested import.')).toBeNull();
  });

  it('keeps the output tabset accessible after switching explorer modes', async () => {
    render(Page);

    await fireEvent.click(screen.getByRole('tab', { name: 'AST' }));
    await fireEvent.click(screen.getByRole('tab', { name: 'Problems' }));

    expect(screen.getByRole('tablist', { name: 'Output panels' })).not.toBeNull();
    expect(screen.getByRole('tabpanel', { name: 'Problems' }).textContent).toContain(
      'No problems.'
    );
  });
});
