import { Effect } from 'effect';

import { playgroundExamples } from './catalog';
import type {
  ExampleNotFoundDiagnostic,
  PlaygroundDiagnostic,
  PlaygroundExample
} from './types';

export type CommandIntent =
  | { type: 'example.open'; exampleId: string }
  | { type: 'github.import'; target: string };

function exampleNotFound(id: string): ExampleNotFoundDiagnostic {
  return {
    code: 'example/not-found',
    message: `Unknown example: ${id}`,
    severity: 'error',
    source: 'example'
  };
}

function unsupportedCommand(type: CommandIntent['type']): PlaygroundDiagnostic {
  return {
    code: 'command/unsupported',
    message: `Unsupported command: ${type}`,
    severity: 'error',
    source: 'runtime'
  };
}

export function parseCommandIntent(input: string): CommandIntent | null {
  const trimmed = input.trim();
  if (trimmed.startsWith('example ')) {
    return { type: 'example.open', exampleId: trimmed.slice('example '.length) };
  }
  if (trimmed.startsWith('github ')) {
    return { type: 'github.import', target: trimmed.slice('github '.length) };
  }
  return null;
}

export const loadExample = (
  id: string
): Effect.Effect<PlaygroundExample, ExampleNotFoundDiagnostic> =>
  Effect.fromNullable(playgroundExamples.find((example) => example.id === id)).pipe(
    Effect.mapError(() => exampleNotFound(id))
  );

export const resolveExampleIntent = (
  input: string
): Effect.Effect<PlaygroundExample, PlaygroundDiagnostic> =>
  Effect.fromNullable(parseCommandIntent(input)).pipe(
    Effect.flatMap((intent) =>
      intent.type === 'example.open'
        ? loadExample(intent.exampleId)
        : Effect.fail(unsupportedCommand(intent.type))
    ),
    Effect.mapError((error) => error)
  );
