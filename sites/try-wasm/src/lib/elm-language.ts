type MonacoModule = typeof import('monaco-editor/esm/vs/editor/editor.api');

export const elmLanguageId = 'elm';

export const elmKeywords = [
  'alias',
  'as',
  'case',
  'effect',
  'else',
  'exposing',
  'if',
  'import',
  'in',
  'infix',
  'left',
  'let',
  'module',
  'non',
  'of',
  'port',
  'right',
  'then',
  'type',
  'where'
];

export const elmOperators = [
  '->',
  '<-',
  '::',
  '=',
  '|',
  '\\',
  '.',
  '..',
  '+',
  '-',
  '*',
  '/',
  '//',
  '^',
  '==',
  '/=',
  '<',
  '>',
  '<=',
  '>=',
  '&&',
  '||',
  '++',
  '<|',
  '|>',
  '>>',
  '<<'
];

export function registerElmLanguage(monaco: MonacoModule): void {
  const alreadyRegistered = monaco.languages
    .getLanguages()
    .some((language) => language.id === elmLanguageId);

  if (alreadyRegistered) return;

  monaco.languages.register({ id: elmLanguageId });
  monaco.languages.setMonarchTokensProvider(elmLanguageId, {
    keywords: elmKeywords,
    operators: elmOperators,
    tokenizer: {
      root: [
        [/^[a-z][\w]*/, { cases: { '@keywords': 'keyword', '@default': 'identifier' } }],
        [/^[A-Z][\w]*/, 'type'],
        [/^\d+(\.\d+)?/, 'number'],
        [/^"([^"\\]|\\.)*"/, 'string'],
        [/^--.*$/, 'comment'],
        [/^[+\-*/<>=&|^!~%?:.\\]+/, 'operator']
      ]
    }
  });
}
