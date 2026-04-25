---
title: Tooling across platforms
description: Where to run Krueger — JVM and Scala libraries, browser playgrounds, and the JS WASM facade.
---

Krueger ships as **Scala libraries** on three platforms (JVM, Scala.js, Scala
Native) and as **browser-facing tooling** through the published docs site and
WASM-backed playgrounds.

## Scala library (JVM, Scala.js, Scala Native)

Use `krueger-core` (and `krueger-trees` for the query DSL) from Mill, sbt, or
Maven. See [Installation](/krueger/installation/) and [Usage](/krueger/usage/).

The top-level entrypoint is `io.eleven19.krueger.Krueger` (`parseModule`,
`parseModuleToAst`, `parseCst`, …). Generated API HTML for each platform
lives under [/krueger/api/](/krueger/api/).

## Published docs and playground

The site at [eleven19.github.io/krueger](https://eleven19.github.io/krueger/)
includes this handbook plus a single browser playground:

| Route | Stack | Purpose |
| ----- | ----- | ------- |
| [`/krueger/try-wasm/`](https://eleven19.github.io/krueger/try-wasm/) | SvelteKit + Scala.js | Interactive playground with a runtime backend selector (WASM or JS). |

To build that tree locally, see [Contributing → Docs site](/krueger/contributing/#docs-site).

## Browser `Krueger` facade (Scala.js / WASM)

The try-wasm playground loads an ES module that exports a **`Krueger`** object
(`@JSExportTopLevel` from
[`KruegerJs.scala`](https://github.com/Eleven19/krueger/blob/main/krueger/webapp-wasm/src/io/eleven19/krueger/webappwasm/KruegerJs.scala)).
Typical methods:

| Method | Role |
| ------ | ---- |
| `parseCst(source)` | Parse Elm source to a CST handle (opaque across the JS boundary). |
| `parseAst(source)` | Parse to an AST handle. |
| `parseQuery(queryText)` | Parse a query expression to a handle. |
| `runQuery(queryHandle, cstRoot)` | Run a parsed query against a CST root from `parseCst`. |
| `prettyQuery(queryHandle)` | Canonical string form of a parsed query (no envelope). |
| `tokenize(source)` | Lexical tokens for editor highlighting. |

Most calls return a plain **envelope** object:

```js
{
  ok: boolean,
  value: /* opaque handle, matches, or tokens — depends on API */,
  logs: string[],
  errors: [{ phase, message, span? }, ...]
}
```

On success, opaque handles must be passed back into `runQuery` / `prettyQuery`
without serializing them through JSON. On failure, inspect `errors` (and
optional `logs`).

## Compiler API and JSON `invoke` (embedders)

Inside the Scala codebase, the compiler exposes a canonical UTF-8 JSON
contract (`invoke(operation, inputJson) → outputJson`) shared by JVM tests,
future host-callable WASM ABIs, and the browser adapter. That contract is
documented for contributors in the repository design note
[`docs/superpowers/specs/2026-04-24-wasm-shared-api-surface-design.md`](https://github.com/Eleven19/krueger/blob/main/docs/superpowers/specs/2026-04-24-wasm-shared-api-surface-design.md).
Most web authors should use the stable **`Krueger.*`** methods above instead of
calling `invoke` directly.
