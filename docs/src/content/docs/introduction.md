---
title: Introduction
description: What Krueger is, why it exists, and who it's for.
---

Krueger is an Elm-dialect parser and compiler toolchain for Scala. It parses
Elm source into a Concrete Syntax Tree (CST) and a lowered Abstract Syntax
Tree (AST), and exposes a tree-sitter-inspired query DSL for static analysis
over either tree.

## Features

- Full Elm lexer and parser built with [Parsley](https://github.com/j-mie6/parsley) parser combinators.
- Concrete Syntax Tree with cursor-based traversal and a visitor pattern.
- Abstract Syntax Tree with cursor-based traversal and a visitor pattern.
- CST-to-AST lowering.
- Query DSL via `krueger.trees` over a generic `QueryableTree[T]` typeclass.
- Cross-platform: JVM, Scala.js, and Scala Native.

## When to use it

Reach for Krueger when you need to programmatically read or transform Elm
source from Scala — for example, linters, codegen pipelines, formatters,
migration scripts, or editor tooling.
