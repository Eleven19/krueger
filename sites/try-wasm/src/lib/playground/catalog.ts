import type { CommandAction, PlaygroundExample } from './types';

export const playgroundExamples: ReadonlyArray<PlaygroundExample> = [
  {
    id: 'elm/basic-module',
    label: 'Elm: Basic Module',
    language: 'elm',
    source: `module Demo exposing (..)

main = 42
`,
    query: '(CstValueDeclaration) @decl',
    notes: 'Minimal module for first-run CST/AST exploration.'
  },
  {
    id: 'elm/type-alias',
    label: 'Elm: Type Alias',
    language: 'elm',
    source: `module Demo exposing (User)

type alias User =
    { name : String
    , age : Int
    }
`,
    query: '(TypeAliasDeclaration) @alias',
    notes: 'Shows declaration fields and record structure.'
  },
  {
    id: 'query/value-declaration',
    label: 'Query: Value Declaration',
    language: 'krueger-query',
    source: `module Demo exposing (..)

main = 42
`,
    query: '(CstValueDeclaration) @decl',
    notes: 'Introduces matching against a simple value declaration.'
  },
  {
    id: 'query/capture-walkthrough',
    label: 'Query: Capture Walkthrough',
    language: 'krueger-query',
    source: `module Demo exposing (..)

double value =
    value + value
`,
    query: '(CstValueDeclaration pattern: (_) @pattern expression: (_) @expr) @decl',
    notes: 'Demonstrates multiple captures and field-oriented reading.'
  }
];

export const commandSurfaceActions: ReadonlyArray<CommandAction> = [
  { id: 'example.open', label: 'Open Example', hint: 'Load a curated example' },
  { id: 'github.import', label: 'Import From GitHub', hint: 'Load a repo file into the editor' }
];

export const explorerToolbarActions: ReadonlyArray<CommandAction> = [...commandSurfaceActions];
