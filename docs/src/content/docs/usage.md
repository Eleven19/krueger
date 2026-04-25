---
title: Usage
description: Parse Elm source to CST or AST from Scala.
---

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

See the [API Reference](/krueger/reference/api/) for the full surface area,
[Examples](/krueger/examples/) for richer end-to-end walkthroughs, and
[Tooling](/krueger/tooling/) for browser playgrounds and the WASM-backed `Krueger` API.
