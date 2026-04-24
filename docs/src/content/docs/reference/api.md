---
title: API Reference
description: High-level map of the Krueger public API, with links to the full generated Scaladoc.
---

Krueger's public API is organized into two Scala modules cross-built for three
platforms. A hand-written overview lives on this page; the complete, generated
**[Scaladoc reference](/krueger/api/)** is a separate artifact with per-platform
trees for **JVM**, **Scala.js**, and **Scala Native**.

## Modules at a glance

### `io.eleven19.krueger.Krueger`

Top-level convenience entrypoint exposed from `krueger-core`.

- `parseModule(source: String)` — parse to a CST module.
- `parseModuleToAst(source: String)` — parse and lower to an AST module.
- `parseCst(source: String)` — parse to CST and return a `parsley.Result`.

### `io.eleven19.krueger.cst` / `io.eleven19.krueger.ast`

Node hierarchies, cursors, and visitors for each tree representation, from
`krueger-core`.

### `io.eleven19.krueger.trees`

Generic tree-query DSL over a `QueryableTree[T]` typeclass, from
`krueger-trees`. Includes `QueryParser`, `Matcher`, and the query AST types.

## Full Scaladoc

Pick a platform — each tree is built from the same `krueger.core` +
`krueger.trees` sources but links against that platform's standard library and
dependencies:

- **[JVM](/krueger/api/jvm/)** — default target; widest third-party interop.
- **[Scala.js](/krueger/api/js/)** — browser / Node.js builds.
- **[Scala Native](/krueger/api/native/)** — native-image builds.

The **[Scaladoc landing page](/krueger/api/)** is the single entry point that
lists all three.
