type MonacoModule = typeof import('monaco-editor/esm/vs/editor/editor.api');

import type { CompilerEnvelope } from './krueger';

export const elmLanguageId = 'elm';

export type ElmTokenKind =
  | 'Keyword'
  | 'LowerIdentifier'
  | 'UpperIdentifier'
  | 'Operator'
  | 'Number'
  | 'StringLiteral'
  | 'CharLiteral'
  | 'Comment'
  | 'Whitespace'
  | 'Newline'
  | 'Punctuation'
  | 'Unknown';

export type ElmToken = {
  kind: ElmTokenKind;
  lexeme: string;
  start: number;
  end: number;
};

export type ElmTokenizer = (line: string) => CompilerEnvelope<ElmToken[]>;

export type MonacoTokenSpan = {
  startIndex: number;
  scopes: string;
};

export function scopeFor(kind: ElmTokenKind): string {
  switch (kind) {
    case 'Keyword':
      return 'keyword';
    case 'LowerIdentifier':
      return 'identifier';
    case 'UpperIdentifier':
      return 'type.identifier';
    case 'Operator':
      return 'operator';
    case 'Number':
      return 'number';
    case 'StringLiteral':
    case 'CharLiteral':
      return 'string';
    case 'Comment':
      return 'comment';
    case 'Whitespace':
    case 'Newline':
      return 'white';
    case 'Punctuation':
      return 'delimiter';
    case 'Unknown':
      return 'invalid';
  }
}

export function tokenSpans(line: string, tokenize: ElmTokenizer = globalTokenizer): MonacoTokenSpan[] {
  const result = tokenize(line);
  if (!result.ok || result.value === null) return [{ startIndex: 0, scopes: 'invalid' }];

  return result.value
    .filter((token) => token.kind !== 'Newline')
    .map((token) => ({ startIndex: token.start, scopes: scopeFor(token.kind) }));
}

export function createElmTokensProvider(tokenize: ElmTokenizer = globalTokenizer): MonacoModule['languages']['TokensProvider'] {
  const state = {
    clone: () => state,
    equals: () => true
  };

  return {
    getInitialState: () => state,
    tokenize: (line) => ({
      endState: state,
      tokens: tokenSpans(line, tokenize)
    })
  };
}

export function registerElmLanguage(monaco: MonacoModule, tokenize: ElmTokenizer = globalTokenizer): void {
  const alreadyRegistered = monaco.languages
    .getLanguages()
    .some((language) => language.id === elmLanguageId);

  if (alreadyRegistered) return;

  monaco.languages.register({ id: elmLanguageId });
  monaco.languages.setTokensProvider(elmLanguageId, createElmTokensProvider(tokenize));
}

function globalTokenizer(line: string): CompilerEnvelope<ElmToken[]> {
  const facade = (globalThis as { Krueger?: { tokenize?: (source: string) => unknown } }).Krueger;
  if (typeof facade?.tokenize !== 'function') {
    return {
      ok: false,
      value: null,
      logs: [],
      errors: [{ phase: 'tokenize', message: 'Krueger tokenizer facade is not loaded' }]
    };
  }

  return normalizeTokenEnvelope(facade.tokenize(line));
}

function normalizeTokenEnvelope(raw: unknown): CompilerEnvelope<ElmToken[]> {
  const record = asRecord(raw);
  const ok = Boolean(record.ok);
  const value = ok && Array.isArray(record.value) ? record.value.map(normalizeToken) : null;

  return {
    ok,
    value,
    logs: Array.isArray(record.logs) ? record.logs.map(String) : [],
    errors: []
  };
}

function normalizeToken(raw: unknown): ElmToken {
  const record = asRecord(raw);
  return {
    kind: normalizeKind(record.kind),
    lexeme: String(record.lexeme ?? ''),
    start: Number(record.start ?? 0),
    end: Number(record.end ?? 0)
  };
}

function normalizeKind(value: unknown): ElmTokenKind {
  const rendered = String(value);
  if (isElmTokenKind(rendered)) return rendered;
  return 'Unknown';
}

function isElmTokenKind(value: string): value is ElmTokenKind {
  return (
    value === 'Keyword' ||
    value === 'LowerIdentifier' ||
    value === 'UpperIdentifier' ||
    value === 'Operator' ||
    value === 'Number' ||
    value === 'StringLiteral' ||
    value === 'CharLiteral' ||
    value === 'Comment' ||
    value === 'Whitespace' ||
    value === 'Newline' ||
    value === 'Punctuation' ||
    value === 'Unknown'
  );
}

function asRecord(value: unknown): Record<string, unknown> {
  return value !== null && typeof value === 'object' ? (value as Record<string, unknown>) : {};
}
