// @vitest-environment jsdom

import { fireEvent, render, screen } from '@testing-library/svelte';
import { describe, expect, it, vi } from 'vitest';

import ActivityBar from './ActivityBar.svelte';
import ResultsPanel from './ResultsPanel.svelte';
import type { CompilerEnvelope, MatchView } from '$lib/krueger';
import type { Panel } from '$lib/panels';

const ok = <T>(value: T): CompilerEnvelope<T> => ({ ok: true, value, logs: [], errors: [] });
const error = (message: string): CompilerEnvelope<unknown> => ({
  ok: false,
  value: null,
  logs: [],
  errors: [{ phase: 'cst', message }]
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

function resultProps(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    selectedPanel: 'matches' satisfies Panel,
    cstResult: ok('CstModule(...)'),
    astResult: ok('Module(...)'),
    matchResult: ok([match]),
    queryResult: ok({}),
    prettyQuery: '(CstValueDeclaration) @decl',
    ...overrides
  };
}

describe('try-wasm ActivityBar and ResultsPanel components', () => {
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
        cstResult: error('unexpected end of input')
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
});
