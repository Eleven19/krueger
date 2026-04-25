import { describe, expect, expectTypeOf, it } from 'vitest';
import { Effect } from 'effect';

import { loadExample } from './actions';
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
});
