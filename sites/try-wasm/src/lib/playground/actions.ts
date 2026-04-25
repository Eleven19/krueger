import { Effect } from 'effect';

import { playgroundExamples } from './catalog';
import type { ExampleNotFoundDiagnostic, PlaygroundExample } from './types';

function exampleNotFound(id: string): ExampleNotFoundDiagnostic {
  return {
    code: 'example/not-found',
    message: `Unknown example: ${id}`,
    severity: 'error',
    source: 'example'
  };
}

export const loadExample = (
  id: string
): Effect.Effect<PlaygroundExample, ExampleNotFoundDiagnostic> =>
  Effect.fromNullable(playgroundExamples.find((example) => example.id === id)).pipe(
    Effect.mapError(() => exampleNotFound(id))
  );
