import type { BackendId } from './backend';
import type { ElmToken } from './elm-language';

export type CompilerError = {
  phase: string;
  message: string;
  span?: {
    start: number;
    end: number;
  };
};

export type CompilerEnvelope<T> = {
  ok: boolean;
  value: T | null;
  logs: string[];
  errors: CompilerError[];
};

export type UnistPoint = {
  line: number;
  column: number;
  offset?: number;
};

export type UnistPosition = {
  start: UnistPoint;
  end: UnistPoint;
};

export type UnistNode = {
  type: string;
  value?: string;
  position?: UnistPosition;
  data: {
    fields: Record<string, number[]>;
    childCount: number;
  };
  children: UnistNode[];
};

export type CapturedNode = {
  nodeType: string;
  childCount: number;
  text?: string;
};

export type MatchView = {
  rootNodeType: string;
  rootText?: string;
  captures: Record<string, CapturedNode>;
};

export type KruegerClientOptions = {
  /**
   * Override the URL used to load the compiler facade ES module. Path is
   * relative to the document. Each backend has its own default URL — supply
   * this only for tests or non-standard hosting layouts.
   */
  facadeUrl?: string;
};

type RawKruegerFacade = {
  parseCst(source: string): unknown;
  parseAst(source: string): unknown;
  parseCstUnist?(source: string): unknown;
  parseAstUnist?(source: string): unknown;
  parseQuery(query: string): unknown;
  runQuery(query: unknown, root: unknown): unknown;
  prettyQuery(query: unknown): string;
  tokenize(source: string): unknown;
};

export type KruegerClient = {
  /** Backend id this client was constructed for. Stable for the lifetime of the client. */
  readonly backend: BackendId;
  parseCst(source: string): CompilerEnvelope<unknown>;
  parseAst(source: string): CompilerEnvelope<unknown>;
  parseCstUnist(source: string): CompilerEnvelope<UnistNode>;
  parseAstUnist(source: string): CompilerEnvelope<UnistNode>;
  parseQuery(query: string): CompilerEnvelope<unknown>;
  runQuery(query: unknown, root: unknown): CompilerEnvelope<MatchView[]>;
  prettyQuery(query: unknown): string;
  tokenize(source: string): CompilerEnvelope<ElmToken[]>;
};

const DEFAULT_FACADE_URLS: Record<BackendId, string> = {
  // JS-linked Scala.js facade (top-level export name: `Krueger`).
  js: 'wasm/facade/main.js',
  // Wasm-linked Scala.js facade (top-level export name: `KruegerWasm`).
  webgc: 'wasm/webgc/main.js'
};

const FACADE_EXPORT_NAMES: Record<BackendId, 'Krueger' | 'KruegerWasm'> = {
  js: 'Krueger',
  webgc: 'KruegerWasm'
};

export async function createKruegerClient(
  backend: BackendId,
  options: KruegerClientOptions = {}
): Promise<KruegerClient> {
  const url = options.facadeUrl ?? defaultFacadeUrl(backend);
  const facade = await loadFacade(url, FACADE_EXPORT_NAMES[backend]);

  return {
    backend,
    parseCst(source) {
      return invokeEnvelope(() => facade.parseCst(source));
    },
    parseAst(source) {
      return invokeEnvelope(() => facade.parseAst(source));
    },
    parseCstUnist(source) {
      return invokeEnvelope<UnistNode>(
        () => invokeFacadeMethod(facade, 'parseCstUnist', source),
        normalizeUnistNode
      );
    },
    parseAstUnist(source) {
      return invokeEnvelope<UnistNode>(
        () => invokeFacadeMethod(facade, 'parseAstUnist', source),
        normalizeUnistNode
      );
    },
    parseQuery(query) {
      return invokeEnvelope(() => facade.parseQuery(query));
    },
    runQuery(query, root) {
      return invokeEnvelope<MatchView[]>(() => facade.runQuery(query, root), normalizeMatches);
    },
    prettyQuery(query) {
      return facade.prettyQuery(query);
    },
    tokenize(source) {
      return invokeEnvelope<ElmToken[]>(() => facade.tokenize(source), normalizeTokens);
    }
  };
}

function defaultFacadeUrl(backend: BackendId): string {
  const relative = DEFAULT_FACADE_URLS[backend];
  if (typeof globalThis.location === 'undefined') return `/${relative}`;
  return new URL(relative, globalThis.location.href).href;
}

async function loadFacade(
  facadeUrl: string,
  exportName: 'Krueger' | 'KruegerWasm'
): Promise<RawKruegerFacade> {
  const mod = (await import(/* @vite-ignore */ facadeUrl)) as Record<string, unknown>;
  const candidate = mod[exportName] ?? (globalThis as Record<string, unknown>)[exportName];

  if (!isKruegerFacade(candidate)) {
    throw new Error(`${exportName} facade was not exported by ${facadeUrl}`);
  }

  // Mirror the active facade onto a stable global so anything that runs
  // outside the React-/Svelte-component stack (notably the Monaco tokens
  // provider in `elm-language.ts:globalTokenizer`) can reach it without
  // plumbing the client through Monaco's API. We always write `Krueger`
  // regardless of which underlying export was loaded — both shapes are
  // identical at the call sites, so callers don't have to special-case
  // backend ids.
  (globalThis as Record<string, unknown>).Krueger = candidate;
  return candidate;
}

function invokeEnvelope<T>(
  call: () => unknown,
  normalizeValue: (value: unknown) => T = (value) => value as T
): CompilerEnvelope<T> {
  try {
    return normalizeEnvelope(call(), normalizeValue);
  } catch (error) {
    return {
      ok: false,
      value: null,
      logs: [],
      errors: [
        {
          phase: 'internal',
          message: error instanceof Error ? error.message : String(error)
        }
      ]
    };
  }
}

function invokeFacadeMethod<TArgs extends unknown[]>(
  facade: RawKruegerFacade,
  methodName: string,
  ...args: TArgs
): unknown {
  const method = facade[methodName as keyof RawKruegerFacade];
  if (typeof method !== 'function') {
    throw new Error(`Krueger facade method ${methodName} is unavailable`);
  }
  return method.apply(facade, args);
}

function normalizeEnvelope<T>(raw: unknown, normalizeValue: (value: unknown) => T): CompilerEnvelope<T> {
  const record = asRecord(raw);
  const ok = Boolean(record.ok);
  const value = ok && record.value != null ? normalizeValue(record.value) : null;

  return {
    ok,
    value,
    logs: normalizeStrings(record.logs),
    errors: normalizeErrors(record.errors)
  };
}

function normalizeMatches(value: unknown): MatchView[] {
  if (!Array.isArray(value)) return [];

  return value.map((raw) => {
    const record = asRecord(raw);
    return {
      rootNodeType: String(record.rootNodeType ?? ''),
      ...(record.rootText == null ? {} : { rootText: String(record.rootText) }),
      captures: normalizeCaptures(record.captures)
    };
  });
}

function normalizeUnistNode(value: unknown): UnistNode {
  const record = asRecord(value);
  return {
    type: String(record.type ?? ''),
    ...(record.value == null ? {} : { value: String(record.value) }),
    ...(record.position == null ? {} : { position: normalizeUnistPosition(record.position) }),
    data: normalizeUnistData(record.data),
    children: Array.isArray(record.children) ? record.children.map(normalizeUnistNode) : []
  };
}

function normalizeUnistData(value: unknown): UnistNode['data'] {
  const record = asRecord(value);
  const rawFields = asRecord(record.fields);
  return {
    childCount: Number(record.childCount ?? 0),
    fields: Object.fromEntries(
      Object.entries(rawFields).map(([name, indexes]) => [
        name,
        Array.isArray(indexes) ? indexes.map((index) => Number(index)) : []
      ])
    )
  };
}

function normalizeUnistPosition(value: unknown): UnistPosition {
  const record = asRecord(value);
  return {
    start: normalizeUnistPoint(record.start),
    end: normalizeUnistPoint(record.end)
  };
}

function normalizeUnistPoint(value: unknown): UnistPoint {
  const record = asRecord(value);
  return {
    line: Number(record.line ?? 1),
    column: Number(record.column ?? 1),
    ...(record.offset == null ? {} : { offset: Number(record.offset) })
  };
}

function normalizeTokens(value: unknown): ElmToken[] {
  if (!Array.isArray(value)) return [];

  return value.map((raw) => {
    const record = asRecord(raw);
    return {
      kind: normalizeTokenKind(record.kind),
      lexeme: String(record.lexeme ?? ''),
      start: Number(record.start ?? 0),
      end: Number(record.end ?? 0)
    };
  });
}

function normalizeTokenKind(value: unknown): ElmToken['kind'] {
  const rendered = String(value);
  if (
    rendered === 'Keyword' ||
    rendered === 'LowerIdentifier' ||
    rendered === 'UpperIdentifier' ||
    rendered === 'Operator' ||
    rendered === 'Number' ||
    rendered === 'StringLiteral' ||
    rendered === 'CharLiteral' ||
    rendered === 'Comment' ||
    rendered === 'Whitespace' ||
    rendered === 'Newline' ||
    rendered === 'Punctuation' ||
    rendered === 'Unknown'
  ) {
    return rendered;
  }
  return 'Unknown';
}

function normalizeCaptures(value: unknown): Record<string, CapturedNode> {
  const captures = asRecord(value);
  return Object.fromEntries(
    Object.entries(captures).map(([name, raw]) => {
      const record = asRecord(raw);
      return [
        name,
        {
          nodeType: String(record.nodeType ?? ''),
          childCount: Number(record.childCount ?? 0),
          ...(record.text == null ? {} : { text: String(record.text) })
        }
      ];
    })
  );
}

function normalizeErrors(value: unknown): CompilerError[] {
  if (!Array.isArray(value)) return [];

  return value.map((raw) => {
    const record = asRecord(raw);
    return {
      phase: String(record.phase ?? 'internal'),
      message: String(record.message ?? ''),
      ...(record.span == null ? {} : { span: normalizeSpan(record.span) })
    };
  });
}

function normalizeSpan(value: unknown): { start: number; end: number } {
  const record = asRecord(value);
  return {
    start: Number(record.start ?? 0),
    end: Number(record.end ?? 0)
  };
}

function normalizeStrings(value: unknown): string[] {
  return Array.isArray(value) ? value.map(String) : [];
}

function asRecord(value: unknown): Record<string, unknown> {
  return value !== null && typeof value === 'object' ? (value as Record<string, unknown>) : {};
}

function isKruegerFacade(value: unknown): value is RawKruegerFacade {
  const record = asRecord(value);
  return (
    typeof record.parseCst === 'function' &&
    typeof record.parseAst === 'function' &&
    typeof record.parseQuery === 'function' &&
    typeof record.runQuery === 'function' &&
    typeof record.prettyQuery === 'function' &&
    typeof record.tokenize === 'function'
  );
}
