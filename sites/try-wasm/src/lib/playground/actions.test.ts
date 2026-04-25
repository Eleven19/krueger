import { describe, expect, expectTypeOf, it } from 'vitest';
import { Effect } from 'effect';

import { loadExample, parseCommandIntent, resolveExampleIntent } from './actions';
import type { ExampleNotFoundDiagnostic, PlaygroundExample } from './types';

describe('playground actions', () => {
  it('loads a curated example by id', async () => {
    const example = await Effect.runPromise(loadExample('elm/basic-module'));

    expect(example).toMatchObject({
      id: 'elm/basic-module',
      source: expect.stringContaining('module Demo exposing')
    });
  });

  it('maps unknown example ids to a stable diagnostic', async () => {
    expectTypeOf(loadExample('missing/example')).toEqualTypeOf<
      Effect.Effect<PlaygroundExample, ExampleNotFoundDiagnostic>
    >();

    const diagnostic = await Effect.runPromise(Effect.flip(loadExample('missing/example')));

    expect(diagnostic).toEqual({
      code: 'example/not-found',
      message: 'Unknown example: missing/example',
      severity: 'error',
      source: 'example'
    });
  });

  it('parses example commands into example intents', () => {
    expect(parseCommandIntent('example elm/type-alias')).toEqual({
      type: 'example.open',
      exampleId: 'elm/type-alias'
    });
  });

  it('resolves example commands into curated examples', async () => {
    const example = await Effect.runPromise(resolveExampleIntent('example elm/type-alias'));

    expect(example).toMatchObject({
      id: 'elm/type-alias',
      source: expect.stringContaining('type alias User')
    });
  });

  it('maps unsupported commands to a stable diagnostic', async () => {
    const diagnostic = await Effect.runPromise(
      Effect.flip(resolveExampleIntent('github https://github.com/owner/repo'))
    );

    expect(diagnostic).toEqual({
      code: 'command/unsupported',
      message: 'Unsupported command: github.import',
      severity: 'error',
      source: 'runtime'
    });
  });
});
