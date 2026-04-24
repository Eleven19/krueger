# Krueger

[![Build Status](https://github.com/Eleven19/krueger/actions/workflows/ci.yml/badge.svg)](https://github.com/Eleven19/krueger/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.eleven19.krueger/krueger-core_3)](https://central.sonatype.com/artifact/io.eleven19.krueger/krueger-core_3)
[![Sonatype Nexus (Snapshots)](https://img.shields.io/nexus/s/io.eleven19.krueger/krueger-core_3?server=https%3A%2F%2Fs01.oss.sonatype.org)](https://s01.oss.sonatype.org/content/repositories/snapshots/io/eleven19/krueger/)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

An Elm dialect parser and compiler toolchain for Scala. Krueger parses Elm source code into both a Concrete Syntax Tree (CST) and Abstract Syntax Tree (AST), with support for JVM, Scala.js, and Scala Native targets.

## Features

- Full Elm dialect lexer and parser built with [Parsley](https://github.com/j-mie6/parsley) parser combinators
- Concrete Syntax Tree (CST) with cursor-based traversal and visitor pattern
- Abstract Syntax Tree (AST) with cursor-based traversal and visitor pattern
- CST-to-AST lowering
- Tree-sitter-inspired query DSL over a generic `QueryableTree[T]` typeclass (see [krueger.trees](krueger/trees/README.md))
- Cross-platform support (JVM, Scala.js, and Scala Native)

## Getting Started

### Requirements

- JDK 25+ (Temurin recommended)
- [Mill](https://mill-build.org/) 1.1.5+
- For Scala Native: `clang`, `libunwind`, and a boehm-gc library (`libgc` on Linux)

### Installation

Add the dependency to your build:

**Mill**

```scala
ivy"io.eleven19.krueger::krueger-core:VERSION"
```

**sbt**

```scala
"io.eleven19.krueger" %% "krueger-core" % "VERSION"
```

**Maven**

```xml
<dependency>
    <groupId>io.eleven19.krueger</groupId>
    <artifactId>krueger-core_3</artifactId>
    <version>VERSION</version>
</dependency>
```

### Usage

```scala
import io.eleven19.krueger.Krueger

val source = """module Main exposing (..)

import Html exposing (text)

main = text "Hello, World!"
"""

// Parse to CST
val cst = Krueger.parseModule(source)

// Parse to AST
val ast = Krueger.parseModuleToAst(source)
```

## Building from Source

```sh
# Compile (JVM)
mill krueger.core.jvm.compile

# Run tests (JVM)
mill krueger.core.jvm.test

# Compile for Scala.js
mill krueger.core.js.compile

# Compile for Scala Native
mill krueger.core.native.compile
```

## Testing

Krueger has two complementary test layers.

### Unit tests (ZIO Test, cross-platform)

Sources under `krueger/core/test/src/` are shared by all three platforms:

```sh
mill krueger.core.jvm.test
mill krueger.core.js.test
mill krueger.core.native.test
```

### BDD integration tests (cucumber-scala on JUnit 5, JVM)

Gherkin feature files under `krueger/itest/resources/features/` exercise the
parser end-to-end through a `TestDriver`. Step definitions live in
`krueger/itest/src/io/eleven19/krueger/itest/steps/`.

```sh
mill krueger.itest
```

To add new scenarios, drop a `.feature` file into `resources/features/` and
add or extend a step-definition class in `steps/`.

## Querying trees

Krueger ships a tree-sitter-inspired query DSL via the `krueger.trees`
module. A compact S-expression syntax selects nodes from either the CST
or the AST (or any other tree with a `QueryableTree[T]` instance):

```scala
import io.eleven19.krueger.Krueger
import io.eleven19.krueger.cst.CstName
import io.eleven19.krueger.cst.CstQueryableTree.given
import io.eleven19.krueger.trees.query.*
import parsley.Success

val Success(module) = Krueger.parseCst(source): @unchecked
val Success(query)  = QueryParser.parse(
    "(CstValueDeclaration name: (CstName) @n)"
): @unchecked

val names = Matcher
    .matches(query, module)
    .flatMap(_.captures.get("n"))
    .collect { case n: CstName => n.value }
    .toList
```

Full documentation, query-language reference, and BDD examples live in
[krueger/trees/README.md](krueger/trees/README.md).

## Project Structure

```
krueger/
  core/              # Core module (JVM + JS + Native)
    src/             # Source code
      ast/           # AST nodes, cursor, and visitor
      cst/           # CST nodes, cursor, and visitor
      lexer/         # Elm lexer
      parser/        # Module, expression, declaration, and pattern parsers
    test/            # ZIO Test suite
  trees/             # Generic tree-query DSL (JVM + JS + Native)
    src/             # QueryableTree typeclass, Query AST, Parsley parser, Matcher
    test/            # Typeclass + parser + matcher unit tests
```

## License

This project is licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).
