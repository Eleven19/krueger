import { Buffer } from 'node:buffer';

import { describe, expect, it, vi } from 'vitest';
import { Effect } from 'effect';

import { importGithubFile, parseGithubTarget } from './github';

describe('github import service', () => {
  it('parses repo and file URLs into a stable target shape', () => {
    expect(parseGithubTarget('https://github.com/elm/core/blob/main/src/Basics.elm')).toEqual({
      owner: 'elm',
      repo: 'core',
      ref: 'main',
      path: 'src/Basics.elm'
    });
  });

  it('rejects non-GitHub hosts even when the pathname looks like a blob URL', () => {
    expect(parseGithubTarget('https://example.com/elm/core/blob/main/src/Basics.elm')).toBeNull();
  });

  it('imports file content from the GitHub contents API', async () => {
    const fetchImpl = vi.fn(async () =>
      new Response(
        JSON.stringify({ content: Buffer.from('module Demo exposing (..)\n').toString('base64') }),
        { status: 200 }
      )
    );

    const file = await Effect.runPromise(
      importGithubFile('https://github.com/elm/core/blob/main/src/Basics.elm', fetchImpl)
    );

    expect(file.path).toBe('src/Basics.elm');
    expect(file.source).toContain('module Demo exposing');
  });

  it('returns a typed diagnostic when import fails', async () => {
    const fetchImpl = vi.fn(async () => new Response('missing', { status: 404 }));
    const exit = await Effect.runPromiseExit(
      importGithubFile('https://github.com/elm/core/blob/main/src/Missing.elm', fetchImpl)
    );

    expect(exit._tag).toBe('Failure');
    expect(JSON.stringify(exit)).toContain('github/import-failed');
    expect(JSON.stringify(exit)).toContain('src/Missing.elm');
  });
});
