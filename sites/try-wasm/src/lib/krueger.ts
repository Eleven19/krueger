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
  facadeUrl?: string;
};

type RawKruegerFacade = {
  parseCst(source: string): unknown;
  parseAst(source: string): unknown;
  parseQuery(query: string): unknown;
  runQuery(query: unknown, root: unknown): unknown;
  prettyQuery(query: unknown): string;
  tokenize(source: string): unknown;
  /** Optional — present only when the deployed facade was built after the
   *  backend selector was wired up. The client treats absence as "no
   *  switching support" and reports a structured error. */
  setBackend?(id: string): boolean;
  /** Optional — same compatibility caveat as `setBackend`. */
  currentBackend?(): string;
};

export type KruegerClient = {
  parseCst(source: string): CompilerEnvelope<unknown>;
  parseAst(source: string): CompilerEnvelope<unknown>;
  parseQuery(query: string): CompilerEnvelope<unknown>;
  runQuery(query: unknown, root: unknown): CompilerEnvelope<MatchView[]>;
  prettyQuery(query: unknown): string;
  tokenize(source: string): CompilerEnvelope<ElmToken[]>;
  /** Currently active backend id (best-effort: returns "unknown" if the
   *  loaded facade is too old to expose the accessor). */
  currentBackend(): string;
  /** Switch the active backend. Resolves to `true` when the facade accepted
   *  the id, `false` otherwise. */
  setBackend(id: BackendId): boolean;
};

export async function createKruegerClient(options: KruegerClientOptions = {}): Promise<KruegerClient> {
  const facade = await loadFacade(options.facadeUrl ?? defaultFacadeUrl());

  return {
    parseCst(source) {
      return invokeEnvelope(() => facade.parseCst(source));
    },
    parseAst(source) {
      return invokeEnvelope(() => facade.parseAst(source));
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
    },
    currentBackend() {
      return typeof facade.currentBackend === 'function' ? facade.currentBackend() : 'unknown';
    },
    setBackend(id) {
      return typeof facade.setBackend === 'function' ? facade.setBackend(id) : false;
    }
  };
}

function defaultFacadeUrl(): string {
  if (typeof globalThis.location === 'undefined') return '/wasm/facade/main.js';
  return new URL('wasm/facade/main.js', globalThis.location.href).href;
}

async function loadFacade(facadeUrl: string): Promise<RawKruegerFacade> {
  const mod = await import(/* @vite-ignore */ facadeUrl);
  const candidate = mod.Krueger ?? globalThis.Krueger;

  if (!isKruegerFacade(candidate)) {
    throw new Error(`Krueger facade was not exported by ${facadeUrl}`);
  }

  globalThis.Krueger = candidate;
  return candidate;
}

function invokeEnvelope<T>(call: () => unknown, normalizeValue: (value: unknown) => T = (value) => value as T): CompilerEnvelope<T> {
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
  // `setBackend` / `currentBackend` are intentionally optional — older
  // deployed facades may predate the backend selector. The loader still
  // accepts them so the playground keeps working against a facade that has
  // not been re-linked yet, just without the toggle.
  return (
    typeof record.parseCst === 'function' &&
    typeof record.parseAst === 'function' &&
    typeof record.parseQuery === 'function' &&
    typeof record.runQuery === 'function' &&
    typeof record.prettyQuery === 'function' &&
    typeof record.tokenize === 'function'
  );
}

declare global {
  // Scala.js can publish the facade either as an ESM export or as a global,
  // depending on linker mode. Supporting both keeps the wrapper deployable.
  var Krueger: RawKruegerFacade | undefined;
}
