import { Effect } from 'effect';

import type { GithubTarget, ImportedGithubFile, PlaygroundDiagnostic } from './types';

export function parseGithubTarget(input: string): GithubTarget | null {
  try {
    const url = new URL(input);
    const parts = url.pathname.split('/').filter(Boolean);
    if (parts.length < 5 || parts[2] !== 'blob') return null;
    const [owner, repo, , ref, ...pathParts] = parts;
    return { owner, repo, ref, path: pathParts.join('/') };
  } catch {
    return null;
  }
}

export const importGithubFile = (
  input: string,
  fetchImpl: typeof fetch = fetch
): Effect.Effect<ImportedGithubFile, PlaygroundDiagnostic> =>
  Effect.fromNullable(parseGithubTarget(input)).pipe(
    Effect.mapError(() => ({
      code: 'github/invalid-target',
      message: `Unsupported GitHub target: ${input}`,
      severity: 'error',
      source: 'github'
    })),
    Effect.flatMap((target) =>
      Effect.tryPromise({
        try: async () => {
          const response = await fetchImpl(
            `https://api.github.com/repos/${target.owner}/${target.repo}/contents/${target.path}?ref=${target.ref}`
          );
          if (!response.ok) throw new Error(`${response.status} ${response.statusText}`);
          const payload = (await response.json()) as { content: string };
          return {
            path: target.path,
            source: atob(payload.content.replace(/\n/g, '')),
            repoLabel: `${target.owner}/${target.repo}`
          };
        },
        catch: (error): PlaygroundDiagnostic => ({
          code: 'github/import-failed',
          message: `Could not import ${target.path}`,
          detail: error instanceof Error ? error.message : String(error),
          severity: 'error',
          source: 'github'
        })
      })
    )
  );
