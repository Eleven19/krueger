import { describe, expect, it, vi } from 'vitest';

import {
  createElmTokensProvider,
  registerElmLanguage,
  scopeFor,
  tokenSpans,
  type ElmToken
} from './elm-language';
import type { CompilerEnvelope } from './krueger';

function envelope(value: ElmToken[]): CompilerEnvelope<ElmToken[]> {
  return { ok: true, value, logs: [], errors: [] };
}

describe('elm-language shared tokenizer adapter', () => {
  it('maps facade token POJOs to Monaco token spans', () => {
    const spans = tokenSpans('module Main', () =>
      envelope([
        { kind: 'Keyword', lexeme: 'module', start: 0, end: 6 },
        { kind: 'UpperIdentifier', lexeme: 'Main', start: 7, end: 11 }
      ])
    );

    expect(spans).toEqual([
      { startIndex: 0, scopes: 'keyword' },
      { startIndex: 7, scopes: 'type.identifier' }
    ]);
  });

  it('maps recovered unknown tokens to invalid scopes', () => {
    expect(scopeFor('Unknown')).toBe('invalid');
  });

  it('registers a Monaco tokens provider instead of a Monarch regex tokenizer', () => {
    const setTokensProvider = vi.fn();
    const setMonarchTokensProvider = vi.fn();
    const monaco = {
      languages: {
        getLanguages: () => [],
        register: vi.fn(),
        setTokensProvider,
        setMonarchTokensProvider
      }
    };

    registerElmLanguage(monaco as never, () => envelope([]));

    expect(setTokensProvider).toHaveBeenCalledWith('elm', expect.any(Object));
    expect(setMonarchTokensProvider).not.toHaveBeenCalled();
  });

  it('provider tokenizes through the supplied facade tokenizer', () => {
    const provider = createElmTokensProvider(() =>
      envelope([{ kind: 'Operator', lexeme: '->', start: 2, end: 4 }])
    );
    const initialState = provider.getInitialState();

    expect(provider.tokenize('a -> b', initialState)).toEqual({
      endState: initialState,
      tokens: [{ startIndex: 2, scopes: 'operator' }]
    });
  });
});
