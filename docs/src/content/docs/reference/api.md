---
title: API Reference
description: High-level map of the Krueger public API.
---

Krueger's public API is organized into three layers.

## `io.eleven19.krueger.Krueger`

Top-level convenience entrypoint.

- `parseModule(source: String)` — parse to a CST module.
- `parseModuleToAst(source: String)` — parse and lower to an AST module.
- `parseCst(source: String)` — parse to CST and return a `parsley.Result`.

## `io.eleven19.krueger.cst` / `io.eleven19.krueger.ast`

Node hierarchies, cursors, and visitors for each tree representation.

## `io.eleven19.krueger.trees`

Generic tree-query DSL over a `QueryableTree[T]` typeclass. Includes
`QueryParser`, `Matcher`, and the query AST types.

> This page is a placeholder scaffold. Replace with generated or hand-written
> API docs once a source of truth is chosen (Scaladoc, mdoc, etc.).
