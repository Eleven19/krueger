// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/svelte';
import { afterEach, describe, expect, it, vi } from 'vitest';

import ActivityBar from './ActivityBar.svelte';
import ResultsPanel from './ResultsPanel.svelte';
import type { CompilerEnvelope, MatchView, UnistNode } from '$lib/krueger';
import type { Panel } from '$lib/panels';

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

    expect(screen.queryByRole('tree', { name: 'CST tree' })).toBeNull();
    expect(screen.getByText(/"kind": "pending"/)).not.toBeNull();
    expect(screen.getByText(/"detail": "opaque cst payload"/)).not.toBeNull();
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

    expect(screen.queryByRole('tree', { name: 'CST tree' })).toBeNull();
    expect(screen.getByText('CstModule(raw opaque fallback)')).not.toBeNull();
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

    expect(screen.queryByRole('tree', { name: 'AST tree' })).toBeNull();
    expect(screen.getByText('Module(raw opaque fallback)')).not.toBeNull();
    expect(screen.queryByText('unist bridge unavailable')).toBeNull();
  });
});
