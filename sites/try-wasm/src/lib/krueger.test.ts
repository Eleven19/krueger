import { pathToFileURL } from 'node:url';
import { resolve } from 'node:path';

import { describe, expect, it } from 'vitest';

import { createElmTokensProvider } from './elm-language';
import { createKruegerClient, type CompilerEnvelope } from './krueger';

const wasmRoot = resolve(process.cwd(), 'static', 'wasm');
const facadeUrl = pathToFileURL(resolve(wasmRoot, 'facade', 'main.js')).href;

const validSource = `module Demo exposing (..)

main = 42
`;

const malformedSource = `module Demo exposing (..)

main =
`;

const validQuery = '(CstValueDeclaration) @decl';

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

  it('returns existing error envelopes for malformed unist parses', async () => {
    const krueger = await createKruegerClient('js', { facadeUrl });
    const cst = krueger.parseCstUnist(malformedSource);

    expectEnvelope(cst);
    expect(cst.ok).toBe(false);
    expect(cst.value).toBeNull();
    expect(cst.errors[0]?.phase).toBe('cst');
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
});
