---
title: Examples
description: End-to-end walkthroughs of Krueger in action.
---

## Extract all top-level value names

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

More examples — including AST-layer queries and cross-tree traversals — will
be added here.
