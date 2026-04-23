# krueger.trees

A tree-sitter-inspired query DSL, generic over any tree with a
`QueryableTree[T]` instance.

## Why

Hand-written assertions like "find the first `CstValueDeclaration`, then
check its body is a `CstIntLiteral` with value 42" are verbose and tightly
coupled to the tree shape. The [tree-sitter query language][ts-queries]
solves the same problem with compact S-expression patterns and captures.
`krueger.trees` brings that style to krueger's CST and AST — and to any
third-party tree that provides an instance of the `QueryableTree` typeclass.

[ts-queries]: https://tree-sitter.github.io/tree-sitter/using-parsers#pattern-matching-with-queries

## The typeclass

```scala
trait QueryableTree[T]:
    def nodeType(t: T): NodeTypeName
    def children(t: T): Seq[T]
    def fields(t: T): Map[FieldName, Seq[T]]
    def text(t: T): Option[String]
```

The four methods describe: the kind of node, its children in traversal
order, its named sub-trees (so patterns can navigate by field name), and
optional leaf text. `NodeTypeName` and `FieldName` are validated newtypes
(non-empty, non-blank, identifier-shaped) — see the `trees` package for
the full set of domain types. krueger ships instances for both CST and
AST; third parties write one for their own tree.

## Query syntax (v1)

| Form                              | Meaning                                                |
| --------------------------------- | ------------------------------------------------------ |
| `(NodeType)`                      | Match any node whose `nodeType` equals `NodeType`      |
| `(NodeType field: (Child))`       | Match a node, and a named sub-tree within it           |
| `@name`                           | Capture the matched node as `name`                     |
| `_` or `(_)`                      | Wildcard — match any node                              |
| `(#eq? @a @b)`                    | Predicate: captured texts must be equal                |
| `(#eq? @a "literal")`             | Predicate: captured text equals a literal              |
| `(#match? @a "regex")`            | Predicate: captured text matches the regex             |
| `;; line comment`                 | Ignored through to end of line                         |

Alternation, quantifiers, anchors, and negation are deferred to v2.

## CST example

```scala
import io.eleven19.krueger.Krueger
import io.eleven19.krueger.cst.CstName
import io.eleven19.krueger.cst.CstQueryableTree.given
import io.eleven19.krueger.trees.query.*
import parsley.Success

val Success(module) = Krueger.parseCst("""
module M exposing (..)

main = 42
""".stripMargin): @unchecked

val Success(query) = QueryParser.parse(
    "(CstValueDeclaration name: (CstName) @n)"
): @unchecked

val names = Matcher
    .matches(query, module)
    .flatMap(_.captures.get("n"))
    .collect { case n: CstName => n.value }
    .toList
// names == List("main")
```

This example is backed by
[`CstQueryableTreeSpec`](../core/test/src/io/eleven19/krueger/cst/CstQueryableTreeSpec.scala)
— "a node-pattern query surfaces every value declaration".

## AST example

```scala
import io.eleven19.krueger.Krueger
import io.eleven19.krueger.ast.ValueDeclaration
import io.eleven19.krueger.ast.AstQueryableTree.given
import io.eleven19.krueger.trees.query.*
import parsley.Success

val Success(module) = Krueger.parseAst("""
module M exposing (..)

main = 42
""".stripMargin): @unchecked

val Success(query) = QueryParser.parse(
    "(ValueDeclaration) @v (#eq? @v \"main\")"
): @unchecked

val found = Matcher.matches(query, module).size
// found == 1
```

This example is backed by
[`AstQueryableTreeSpec`](../core/test/src/io/eleven19/krueger/ast/AstQueryableTreeSpec.scala)
— "a node-pattern query surfaces each value declaration" and the `#eq?`
predicate scenarios in
[`query.feature`](../itest/resources/features/query.feature).

## Using queries in BDD scenarios

`krueger.itest` exposes query-based verbs for Gherkin. A feature file can
drive the parser end-to-end and make structural assertions without touching
step-definition code:

```gherkin
Scenario: CST query surfaces a single value declaration
  Given the Elm source:
    """
    module M exposing (..)

    main = 42
    """
  When the CST is queried with "(CstValueDeclaration name: (CstName) @n)"
  Then the query matches exactly 1 time
  And capture "n" of match 1 is a "CstName"
  And capture "n" of match 1 has text "main"
```

See
[`query.feature`](../itest/resources/features/query.feature)
and
[`QuerySteps`](../itest/src/io/eleven19/krueger/itest/steps/QuerySteps.scala)
for the full v1 verb set.

## Adding QueryableTree for your own tree

1. Pick a `nodeType` naming convention (krueger uses raw case-class simple
   names, e.g. `"CstIntLiteral"`).
2. Implement `children` — a stable traversal order.
3. Decide which case-class parameters become named `fields`. The invariant:
   every value in `fields(t)` must also appear in `children(t)`.
4. Implement `text` for leaf-like nodes whose primary content is a string,
   number, or similar scalar.

A small reference implementation lives in
[`ToyTree`](test/src/io/eleven19/krueger/trees/ToyTree.scala), used as the
fixture for the typeclass's unit tests.

## Predicate registry

Predicates are looked up in a `PredicateRegistry`. The default registry
ships `#eq?` and `#match?`; callers register custom predicates per-query
without touching the matcher:

```scala
val registry = PredicateRegistry.default.withPredicate(
    "#my-pred",
    new PredicateImpl:
        def evaluate[T](args: List[PredicateArg], captures: Map[String, T])(using
            qt: QueryableTree[T]
        ): Boolean = ???
)
Matcher.matches(query, root, registry)
```

## Roadmap

Delivered in v1:

- `QueryableTree[T]` typeclass with validated domain types throughout —
  `NodeTypeName`, `FieldName`, `CaptureName`, `PredicateName` newtypes
  (via [neotype](https://github.com/kitlangton/neotype)) and a validated
  `RegexPattern` that pre-compiles at construction.
- Parsley-backed S-expression parser and matcher.
- Given instances for `CstNode` and `AstNode`.
- `QuerySteps` BDD step pack.

Follow-up in the v2 epic:

- `NodeTypes` type member on `QueryableTree[T]`.
- Mirror/Hearth-backed derived instances.
- Match-type `NodeByName[Name, T]` for typed captures.
- Alternation, quantifiers, anchors, negation.
