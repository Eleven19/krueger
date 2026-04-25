// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/svelte';
import { afterEach, describe, expect, it, vi } from 'vitest';

import ActivityBar from './ActivityBar.svelte';
import ResultsPanel from './ResultsPanel.svelte';
import type { CompilerEnvelope, MatchView, UnistNode } from '$lib/krueger';
import type { Panel } from '$lib/panels';
import Page from '../../routes/+page.svelte';

vi.mock('$lib/krueger', async (importOriginal) => {
  const actual = await importOriginal<typeof import('$lib/krueger')>();
  const ok = (value: unknown) => ({ ok: true, value, logs: [], errors: [] });

  return {
    ...actual,
    createKruegerClient: vi.fn(async () => ({
      backend: 'js',
      parseCst: () => ok({}),
      parseAst: () => ok({}),
      parseCstUnist: () =>
        ok({ type: 'CstModule', data: { fields: {}, childCount: 1 }, children: [] }),
      parseAstUnist: () =>
        ok({ type: 'Module', data: { fields: {}, childCount: 1 }, children: [] }),
      parseQuery: () => ok({}),
      runQuery: () => ok([]),
      prettyQuery: () => '(CstValueDeclaration) @decl',
      tokenize: () => ok([])
    }))
  };
});

const ok = <T>(value: T): CompilerEnvelope<T> => ({ ok: true, value, logs: [], errors: [] });
const error = (message: string, phase = 'cst'): CompilerEnvelope<unknown> => ({
  ok: false,
  value: null,
  logs: [],
  errors: [{ phase, message }]
});

const match: MatchView = {
  rootNodeType: 'CstValueDeclaration',
  rootText: 'main = 42',
  captures: {
    decl: {
      nodeType: 'CstValueDeclaration',
      childCount: 2,
      text: 'main = 42'
    }
  }
};

const cstTree: UnistNode = {
  type: 'CstModule',
  position: {
    start: { line: 1, column: 1, offset: 0 },
    end: { line: 3, column: 10, offset: 33 }
  },
  data: {
    fields: {
      declarations: [0]
    },
    childCount: 1
  },
  children: [
    {
      type: 'CstValueDeclaration',
      position: {
        start: { line: 3, column: 1, offset: 23 },
        end: { line: 3, column: 10, offset: 32 }
      },
      data: {
        fields: {
          pattern: [0],
          expression: [1]
        },
        childCount: 2
      },
      children: [
        {
          type: 'CstLowerPattern',
          value: 'main',
          position: {
            start: { line: 3, column: 1, offset: 23 },
            end: { line: 3, column: 5, offset: 27 }
          },
          data: {
            fields: {},
            childCount: 0
          },
          children: []
        },
        {
          type: 'CstIntegerConstantExpr',
          value: '42',
          position: {
            start: { line: 3, column: 8, offset: 30 },
            end: { line: 3, column: 10, offset: 32 }
          },
          data: {
            fields: {},
            childCount: 0
          },
          children: []
        }
      ]
    }
  ]
};

const astTree: UnistNode = {
  type: 'Module',
  data: {
    fields: {
      declarations: [0]
    },
    childCount: 1
  },
  children: [
    {
      type: 'ValueDeclaration',
      data: {
        fields: {
          body: [0]
        },
        childCount: 1
      },
      children: [
        {
          type: 'IntConstant',
          value: '42',
          data: {
            fields: {},
            childCount: 0
          },
          children: []
        }
      ]
    }
  ]
};

function resultProps(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    selectedPanel: 'matches' satisfies Panel,
    cstResult: ok('CstModule(...)'),
    astResult: ok('Module(...)'),
    cstUnistResult: ok(cstTree),
    astUnistResult: ok(astTree),
    matchResult: ok([match]),
    queryResult: ok({}),
    prettyQuery: '(CstValueDeclaration) @decl',
    backend: 'js',
    wasmGcSupported: true,
    onBackendChange: vi.fn(),
    ...overrides
  };
}

describe('try-wasm ActivityBar and ResultsPanel components', () => {
  afterEach(() => {
    cleanup();
  });

  it('switches panels through the VS Code-style activity bar', async () => {
    const onSelect = vi.fn();

    render(ActivityBar, { selectedPanel: 'matches', onSelect });
    await fireEvent.click(screen.getByRole('tab', { name: 'CST' }));

    expect(onSelect).toHaveBeenCalledWith('cst');
  });

  it('renders matches by default and shows a no-match placeholder for empty results', () => {
    render(ResultsPanel, resultProps({ matchResult: ok([]) }));

    expect(screen.getByRole('tabpanel', { name: 'Matches' })).not.toBeNull();
    expect(screen.getByText('No matches.')).not.toBeNull();
  });

  it('renders human-readable compiler errors instead of a blank tree', () => {
    render(
      ResultsPanel,
      resultProps({
        selectedPanel: 'cst',
        cstResult: error('unexpected end of input'),
        cstUnistResult: error('unexpected end of input')
      })
    );

    expect(screen.getByRole('tabpanel', { name: 'CST' })).not.toBeNull();
    expect(screen.getByText('Parse errors:')).not.toBeNull();
    expect(screen.getByText('unexpected end of input')).not.toBeNull();
  });

  it('renders the canonical query echo panel', () => {
    render(ResultsPanel, resultProps({ selectedPanel: 'prettyQuery' }));

    expect(screen.getByRole('tabpanel', { name: 'Canonical Query' })).not.toBeNull();
    expect(screen.getByText('(CstValueDeclaration) @decl')).not.toBeNull();
  });

  it('renders a collapsible unist tree for CST output', async () => {
    render(ResultsPanel, resultProps({ selectedPanel: 'cst' }));

    expect(screen.getByRole('tabpanel', { name: 'CST' })).not.toBeNull();
    expect(screen.getByRole('tree', { name: 'CST tree' })).not.toBeNull();
    expect(screen.getByText('CstModule')).not.toBeNull();
    expect(screen.getByText('CstValueDeclaration')).not.toBeNull();
    expect(screen.getByText('"main"')).not.toBeNull();
    expect(screen.getByText('2 children')).not.toBeNull();

    await fireEvent.click(screen.getByRole('button', { name: 'Collapse CstModule' }));

    expect(screen.queryByText('CstValueDeclaration')).toBeNull();

    await fireEvent.click(screen.getByRole('button', { name: 'Expand CstModule' }));

    expect(screen.getByText('CstValueDeclaration')).not.toBeNull();
  });

  it('styles quoted node values through the dedicated tree value color token', () => {
    render(ResultsPanel, resultProps({ selectedPanel: 'cst' }));

    const quotedValue = screen.getByText('"main"');

    expect(quotedValue.getAttribute('style')).toContain('var(--kr-tree-value)');
  });

  it('renders raw mode through the monaco-backed read-only viewer for JSON payloads', async () => {
    render(ResultsPanel, resultProps({ selectedPanel: 'cst' }));

    await fireEvent.click(screen.getByRole('button', { name: 'Raw' }));

    const rawView = screen.getByRole('textbox', { name: 'CST raw view' }) as HTMLTextAreaElement;
    const search = screen.getByRole('searchbox', { name: 'Filter nodes' }) as HTMLInputElement;

    expect(screen.queryByRole('tree', { name: 'CST tree' })).toBeNull();
    expect(search.disabled).toBe(false);
    expect(rawView.readOnly).toBe(true);
    expect(rawView.value).toContain('"type": "CstModule"');
    expect(rawView.value).toContain('"childCount": 1');
  });

  it('uses raw-mode search as find input for monaco-backed raw output', async () => {
    render(ResultsPanel, resultProps({ selectedPanel: 'cst' }));

    await fireEvent.click(screen.getByRole('button', { name: 'Raw' }));

    const search = screen.getByRole('searchbox', { name: 'Filter nodes' });
    const rawView = screen.getByRole('textbox', { name: 'CST raw view' }) as HTMLTextAreaElement;
    const expectedIndex = rawView.value.indexOf('childCount');

    await fireEvent.input(search, { target: { value: 'childCount' } });

    expect(search.hasAttribute('disabled')).toBe(false);
    expect(rawView.selectionStart).toBe(expectedIndex);
    expect(rawView.selectionEnd).toBe(expectedIndex + 'childCount'.length);
  });

  it('filters visible nodes and shows a no-match state when the search misses', async () => {
    render(ResultsPanel, resultProps({ selectedPanel: 'cst' }));

    const search = screen.getByRole('searchbox', { name: 'Filter nodes' });
    await fireEvent.input(search, { target: { value: 'Integer' } });

    expect(screen.getByText('CstIntegerConstantExpr')).not.toBeNull();
    expect(screen.queryByText('CstLowerPattern')).toBeNull();

    await fireEvent.input(search, { target: { value: 'Nope' } });

    expect(screen.getByText('No matching nodes.')).not.toBeNull();
    expect(screen.queryByRole('tree', { name: 'CST tree' })).toBeNull();
  });

  it('falls back to the raw renderer for non-unist values', () => {
    render(
      ResultsPanel,
      resultProps({
        selectedPanel: 'cst',
        cstUnistResult: ok({ kind: 'pending', detail: 'opaque cst payload' })
      })
    );

    const rawView = screen.getByRole('textbox', { name: 'CST raw view' }) as HTMLTextAreaElement;

    expect(screen.queryByRole('tree', { name: 'CST tree' })).toBeNull();
    expect(rawView.readOnly).toBe(true);
    expect(rawView.value).toContain('"kind": "pending"');
    expect(rawView.value).toContain('"detail": "opaque cst payload"');
  });

  it('falls back to raw CST and AST output when unist parsing fails but opaque parsing succeeds', () => {
    const { unmount } = render(
      ResultsPanel,
      resultProps({
        selectedPanel: 'cst',
        cstResult: ok('CstModule(raw opaque fallback)'),
        cstUnistResult: error('unist bridge unavailable')
      })
    );

    const cstRawView = screen.getByRole('textbox', { name: 'CST raw view' }) as HTMLTextAreaElement;

    expect(screen.queryByRole('tree', { name: 'CST tree' })).toBeNull();
    expect(cstRawView.value).toBe('CstModule(raw opaque fallback)');
    expect(screen.queryByText('unist bridge unavailable')).toBeNull();

    unmount();

    render(
      ResultsPanel,
      resultProps({
        selectedPanel: 'ast',
        astResult: ok('Module(raw opaque fallback)'),
        astUnistResult: error('unist bridge unavailable', 'ast')
      })
    );

    const astRawView = screen.getByRole('textbox', { name: 'AST raw view' }) as HTMLTextAreaElement;

    expect(screen.queryByRole('tree', { name: 'AST tree' })).toBeNull();
    expect(astRawView.value).toBe('Module(raw opaque fallback)');
    expect(screen.queryByText('unist bridge unavailable')).toBeNull();
  });
});

describe('try-wasm page explorer composition', () => {
  afterEach(() => {
    cleanup();
  });

  it('sends tree selection into the inspector and keeps logs/problems in a bottom tabset', async () => {
    render(Page);

    const treeToggle = await screen.findByRole('button', { name: 'Select CstModule' });
    await fireEvent.click(treeToggle);

    expect(screen.getByRole('region', { name: 'Selection inspector' }).textContent).toContain(
      'CstModule'
    );
    expect(screen.getByRole('region', { name: 'Selection inspector' }).textContent).toContain(
      'root'
    );
    expect(document.querySelector('[role="treeitem"][aria-selected="true"]')?.textContent).toContain(
      'CstModule'
    );
    expect(document.querySelectorAll('[role="treeitem"][aria-selected="false"]')).toHaveLength(0);
    expect(screen.getByRole('tab', { name: 'Logs' })).not.toBeNull();
    expect(screen.getByRole('tab', { name: 'Problems' })).not.toBeNull();
  });

  it('keeps CST and AST as explorer-facing activity rail labels with explicit tooltips', () => {
    render(Page);

    expect(screen.getByRole('tab', { name: 'CST' }).getAttribute('title')).toBe('CST');
    expect(screen.getByRole('tab', { name: 'AST' }).getAttribute('title')).toBe('AST');
  });

  it('clears stale selection when the active pane or source changes', async () => {
    render(Page);

    await fireEvent.click(await screen.findByRole('button', { name: 'Select CstModule' }));
    expect(screen.getByRole('region', { name: 'Selection inspector' }).textContent).toContain(
      'CstModule'
    );

    await fireEvent.click(screen.getByRole('tab', { name: 'Matches' }));
    expect(screen.getByRole('region', { name: 'Selection inspector' }).textContent).toContain(
      'Select a node to inspect its structure.'
    );

    await fireEvent.click(screen.getByRole('tab', { name: 'CST' }));
    await fireEvent.click(await screen.findByRole('button', { name: 'Select CstModule' }));
    await fireEvent.input(screen.getByRole('textbox', { name: 'Elm source' }), {
      target: {
        value: `module Demo exposing (..)

main = 43
`
      }
    });

    expect(screen.getByRole('region', { name: 'Selection inspector' }).textContent).toContain(
      'Select a node to inspect its structure.'
    );
    expect(document.querySelector('[role="treeitem"][aria-selected="true"]')).toBeNull();
  });
});
