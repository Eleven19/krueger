import { pathToFileURL } from 'node:url';
import { resolve } from 'node:path';

import { describe, expect, it, vi } from 'vitest';

import { createElmTokensProvider } from './elm-language';
import { createKruegerClient, tokenizerReadyEvent, type CompilerEnvelope } from './krueger';

const wasmRoot = resolve(process.cwd(), 'static', 'wasm');
const facadeUrl = pathToFileURL(resolve(wasmRoot, 'facade', 'main.js')).href;

const validSource = `module Demo exposing (..)

main = 42
`;

const malformedSource = `module Demo exposing (..)

main =
`;

const validQuery = '(CstValueDeclaration) @decl';

const legacyFacadeUrl = `data:text/javascript,${encodeURIComponent(`
  export const Krueger = {
    parseCst(source) {
      return { ok: true, value: { kind: 'cst', source }, logs: ['legacy-cst'], errors: [] };
    },
    parseAst(source) {
      return { ok: true, value: { kind: 'ast', source }, logs: ['legacy-ast'], errors: [] };
    },
    parseQuery(query) {
      return { ok: true, value: { query }, logs: ['legacy-query'], errors: [] };
    },
    runQuery(query, root) {
      return {
        ok: true,
        value: [{ rootNodeType: root.kind ?? 'legacy-root', rootText: query.query ?? '', captures: {} }],
        logs: ['legacy-runQuery'],
        errors: []
      };
    },
    prettyQuery(query) {
      return \`legacy:\${query.query ?? ''}\`;
    },
    tokenize(source) {
      return {
        ok: true,
        value: [{ kind: 'Keyword', lexeme: source, start: 0, end: source.length }],
        logs: ['legacy-tokenize'],
        errors: []
      };
    }
  };
`)}`;

function expectEnvelope(env: CompilerEnvelope<unknown>) {
  expect(typeof env.ok).toBe('boolean');
  expect(Array.isArray(env.logs)).toBe(true);
  expect(Array.isArray(env.errors)).toBe(true);
}

describe('krueger.ts real compiler facade wrapper', () => {
  it('parses CST, AST, and queries through the linked facade artifact', async () => {
    const krueger = await createKruegerClient('js', { facadeUrl });

    const cst = krueger.parseCst(validSource);
    const ast = krueger.parseAst(validSource);
    const query = krueger.parseQuery(validQuery);

    expectEnvelope(cst);
    expectEnvelope(ast);
    expectEnvelope(query);
    expect(cst.ok).toBe(true);
    expect(ast.ok).toBe(true);
    expect(query.ok).toBe(true);
    expect(cst.value).toBeTruthy();
    expect(ast.value).toBeTruthy();
    expect(query.value).toBeTruthy();
  });

  it('parses CST and AST unist trees through the linked facade artifact', async () => {
    const krueger = await createKruegerClient('js', { facadeUrl });

    const cst = krueger.parseCstUnist(validSource);
    const ast = krueger.parseAstUnist(validSource);

    expectEnvelope(cst);
    expectEnvelope(ast);
    expect(cst.ok).toBe(true);
    expect(ast.ok).toBe(true);
    expect(cst.value?.type).toBe('CstModule');
    expect(ast.value?.type).toBe('Module');
    expect(cst.value?.data.childCount).toBe(cst.value?.children.length);
    expect(cst.value?.position?.start).toMatchObject({ line: 1, column: 1, offset: 0 });
  });

  it('returns structured errors for malformed source without throwing', async () => {
    const krueger = await createKruegerClient('js', { facadeUrl });

    expect(() => krueger.parseCst(malformedSource)).not.toThrow();
    const cst = krueger.parseCst(malformedSource);

    expectEnvelope(cst);
    expect(cst.ok).toBe(false);
    expect(cst.value).toBeNull();
    expect(cst.errors.length).toBeGreaterThan(0);
    expect(cst.errors[0]?.phase).toBe('cst');
    expect(cst.errors[0]?.message).toContain('unexpected end of input');
  });

  it('returns existing error envelopes for malformed CST and AST unist parses', async () => {
    const krueger = await createKruegerClient('js', { facadeUrl });
    const cst = krueger.parseCstUnist(malformedSource);
    const ast = krueger.parseAstUnist(malformedSource);

    expectEnvelope(cst);
    expectEnvelope(ast);
    expect(cst.ok).toBe(false);
    expect(ast.ok).toBe(false);
    expect(cst.value).toBeNull();
    expect(ast.value).toBeNull();
    expect(cst.errors[0]?.phase).toBe('cst');
    expect(ast.errors[0]?.phase).toBe('ast');
    expect(ast.errors[0]?.message).toContain('unexpected end of input');
  });

  it('supports stale facades without unist methods while degrading unist calls to internal error envelopes', async () => {
    const krueger = await createKruegerClient('js', { facadeUrl: legacyFacadeUrl });

    const cst = krueger.parseCst(validSource);
    const ast = krueger.parseAst(validSource);
    const query = krueger.parseQuery(validQuery);
    const matches = krueger.runQuery(query.value, cst.value);
    const tokens = krueger.tokenize('module');
    const cstUnist = krueger.parseCstUnist(validSource);
    const astUnist = krueger.parseAstUnist(validSource);

    expect(cst).toMatchObject({ ok: true, value: { kind: 'cst', source: validSource }, logs: ['legacy-cst'] });
    expect(ast).toMatchObject({ ok: true, value: { kind: 'ast', source: validSource }, logs: ['legacy-ast'] });
    expect(query).toMatchObject({ ok: true, value: { query: validQuery }, logs: ['legacy-query'] });
    expect(matches).toMatchObject({
      ok: true,
      value: [{ rootNodeType: 'cst', rootText: validQuery, captures: {} }],
      logs: ['legacy-runQuery']
    });
    expect(krueger.prettyQuery(query.value)).toBe(`legacy:${validQuery}`);
    expect(tokens).toMatchObject({
      ok: true,
      value: [{ kind: 'Keyword', lexeme: 'module', start: 0, end: 6 }],
      logs: ['legacy-tokenize']
    });
    expect(cstUnist).toMatchObject({
      ok: false,
      value: null,
      logs: [],
      errors: [{ phase: 'internal', message: 'Krueger facade method parseCstUnist is unavailable' }]
    });
    expect(astUnist).toMatchObject({
      ok: false,
      value: null,
      logs: [],
      errors: [{ phase: 'internal', message: 'Krueger facade method parseAstUnist is unavailable' }]
    });
  });

  it('runs a valid query and preserves deterministic match order', async () => {
    const krueger = await createKruegerClient('js', { facadeUrl });
    const cst = krueger.parseCst(validSource);
    const query = krueger.parseQuery(validQuery);

    const first = krueger.runQuery(query.value, cst.value);
    const second = krueger.runQuery(query.value, cst.value);

    expectEnvelope(first);
    expect(first.ok).toBe(true);
    expect(first.value.length).toBeGreaterThan(0);
    expect(first.value).toEqual(second.value);
    expect(first.value[0]?.rootNodeType).toBe('CstValueDeclaration');
  });

  it('returns a query envelope error for malformed queries and never throws on empty inputs', async () => {
    const krueger = await createKruegerClient('js', { facadeUrl });

    expect(() => krueger.parseQuery('(unbalanced')).not.toThrow();
    const malformedQuery = krueger.parseQuery('(unbalanced');
    const emptySource = krueger.parseCst('');
    const emptyQuery = krueger.parseQuery('');

    expectEnvelope(malformedQuery);
    expectEnvelope(emptySource);
    expectEnvelope(emptyQuery);
    expect(malformedQuery.ok).toBe(false);
    expect(malformedQuery.errors.length).toBeGreaterThan(0);
  });

  it('returns an error envelope when runQuery receives a failed query handle', async () => {
    const krueger = await createKruegerClient('js', { facadeUrl });
    const cst = krueger.parseCst(validSource);
    const malformedQuery = krueger.parseQuery('(unbalanced');

    expect(() => krueger.runQuery(malformedQuery.value, cst.value)).not.toThrow();
    const result = krueger.runQuery(malformedQuery.value, cst.value);

    expectEnvelope(result);
    expect(result.ok).toBe(false);
    expect(result.value).toBeNull();
    expect(result.errors.length).toBeGreaterThan(0);
  });

  it('pretty-prints parsed queries through the same loaded facade instance', async () => {
    const krueger = await createKruegerClient('js', { facadeUrl });
    const query = krueger.parseQuery(validQuery);

    expect(query.ok).toBe(true);
    expect(krueger.prettyQuery(query.value)).toContain('CstValueDeclaration');
  });

  it('exposes shared tokenizer tokens from the loaded facade instance', async () => {
    const krueger = await createKruegerClient('js', { facadeUrl });

    const tokens = krueger.tokenize('module Demo = 1');

    expectEnvelope(tokens);
    expect(tokens.ok).toBe(true);
    expect(tokens.value?.map((token) => token.kind)).toEqual([
      'Keyword',
      'UpperIdentifier',
      'Operator',
      'Number'
    ]);
    expect(tokens.value?.[0]).toMatchObject({ lexeme: 'module', start: 0, end: 6 });
  });

  it('backs the Monaco token provider with the real loaded facade tokenizer by default', async () => {
    await createKruegerClient('js', { facadeUrl });

    const provider = createElmTokensProvider();
    const state = provider.getInitialState();

    expect(provider.tokenize('module Demo = 1', state).tokens).toEqual([
      { startIndex: 0, scopes: 'keyword' },
      { startIndex: 7, scopes: 'type.identifier' },
      { startIndex: 12, scopes: 'operator' },
      { startIndex: 14, scopes: 'number' }
    ]);
  });

  it('dispatches a tokenizer-ready event when the facade finishes loading', async () => {
    const originalDispatchEvent = globalThis.dispatchEvent;
    const dispatchEvent = vi.fn(() => true);
    Object.assign(globalThis, { dispatchEvent });
    try {
      await createKruegerClient('js', { facadeUrl: legacyFacadeUrl });
    } finally {
      Object.assign(globalThis, { dispatchEvent: originalDispatchEvent });
    }

    expect(dispatchEvent).toHaveBeenCalledTimes(1);
    expect(dispatchEvent.mock.calls[0]?.[0]).toMatchObject({ type: tokenizerReadyEvent });
  });
});
