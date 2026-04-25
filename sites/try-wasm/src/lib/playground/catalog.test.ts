import { describe, expect, it } from 'vitest';

import {
  commandSurfaceActions,
  explorerToolbarActions,
  playgroundExamples
} from './catalog';

describe('playground catalog', () => {
  it('exposes deterministic examples for source and query walkthroughs', () => {
    expect(playgroundExamples.map((example) => example.id)).toEqual([
      'elm/basic-module',
      'elm/type-alias',
      'query/value-declaration',
      'query/capture-walkthrough'
    ]);
    expect(playgroundExamples.find((example) => example.id === 'query/capture-walkthrough'))
      .toMatchObject({
        language: 'krueger-query',
        query: '(CstValueDeclaration pattern: (_) @pattern expression: (_) @expr) @decl'
      });
  });

  it('keeps command and explorer actions aligned for discoverability', () => {
    expect(commandSurfaceActions).toEqual([
      { id: 'example.open', label: 'Open Example', hint: 'Load a curated example' },
      {
        id: 'github.import',
        label: 'Import From GitHub',
        hint: 'Load a repo file into the editor'
      }
    ]);
    expect(explorerToolbarActions).toEqual(commandSurfaceActions);
  });
});
