import { describe, expect, it } from 'vitest';
import { Effect } from 'effect';

import { loadExample } from './actions';

describe('playground actions', () => {
  it('loads a curated example by id', async () => {
    const example = await Effect.runPromise(loadExample('elm/basic-module'));

    expect(example).toMatchObject({
      id: 'elm/basic-module',
      source: expect.stringContaining('module Demo exposing')
    });
  });

  it('maps unknown example ids to a stable diagnostic', async () => {
    const exit = await Effect.runPromiseExit(loadExample('missing/example'));

    expect(exit._tag).toBe('Failure');
    expect(JSON.stringify(exit)).toContain('example/not-found');
    expect(JSON.stringify(exit)).toContain('Unknown example: missing/example');
  });
});
