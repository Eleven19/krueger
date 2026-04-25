export type PlaygroundExample = {
  id: string;
  label: string;
  language: 'elm' | 'krueger-query';
  source: string;
  query: string;
  notes?: string;
};

export type CommandAction = {
  id: 'example.open' | 'github.import';
  label: string;
  hint: string;
};

export type PlaygroundDiagnostic = {
  code: string;
  message: string;
  detail?: string;
  severity: 'info' | 'warning' | 'error';
  source: 'example' | 'github' | 'compiler' | 'runtime';
};

export type ExampleNotFoundDiagnostic = {
  code: 'example/not-found';
  message: string;
  detail?: string;
  severity: 'error';
  source: 'example';
};

export type PlaygroundLog = {
  message: string;
  kind: 'info' | 'success' | 'warning' | 'error';
};

export type UtilityTab = 'logs' | 'problems';

export type TreeSelection = {
  path: number[];
  nodeType: string;
  text?: string;
  childCount: number;
  span?: { start: number; end: number };
};
