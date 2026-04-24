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
| `[(A) (B)]`                       | Alternation — match either `(A)` or `(B)`              |
| `(Parent (A)?)`                   | Optional child — match zero or one occurrence          |
| `(Parent (A)*)`                   | Zero-or-more children                                  |
| `(Parent (A)+)`                   | One-or-more children                                   |
| `(Parent (A) . (B))`              | Anchor: `(A)` and `(B)` must match adjacent children   |
| `(Parent !field)`                 | Negated field: named field must be absent or empty     |
| `(NodeType) (NodeType2)`          | Multi-pattern: two independent patterns in one query   |
| `(#eq? @a @b)`                    | Predicate: captured texts must be equal                |
| `(#eq? @a "literal")`             | Predicate: captured text equals a literal              |
| `(#match? @a "regex")`            | Predicate: captured text matches the regex             |
| `(#not-eq? @a "literal")`         | Predicate: captured text must differ from a literal    |
| `(#not-match? @a "regex")`        | Predicate: captured text must not match the regex      |
| `;; line comment`                 | Ignored through to end of line                         |

Anchor support is currently limited to `.` between two unfielded child patterns;
other placements fail with `invalid anchor placement`.
Directives (for example `#set!`) are currently unsupported and fail with an
explicit `Unsupported directive: ...` parse diagnostic.

## Canonical query rendering

Two complementary APIs produce canonical S-expression output from a parsed `Query`:

- **`QueryPretty.render(query)`** — backed by Kindlings `FastShowPretty`; supports
  `RenderConfig.Compact` (one clause per line) and `RenderConfig.Normal` (with indentation).
  Used by `TestDriver.canonicalizeQuerySource()` in BDD scenarios.
- **`QueryPrinter.print(query)`** — a lightweight, dependency-free alternative that
  produces a single-line compact string with spaces between tokens.

Both guarantee round-trip: `parse -> render -> parse` preserves query meaning and
normalises whitespace/trivia.

## Query visitor and cursor

The query module now exposes visitor and zipper-style traversal utilities:

- `QueryVisitor` provides typed dispatch for `Query`, `Pattern`,
  `FieldPattern`, `Predicate`, and `PredicateArg`.
- `QueryVisitor.children` is the canonical node expansion used by:
  - `foldLeft` / `count`
  - `collect` / `collectPostOrder`
  - `traverse` (effectful pre-order walk)
- `QueryCursor` provides navigation over `QueryNode`:
  - `firstChild`, `lastChild`, `nextSibling`, `previousSibling`, `parent`
  - `depth`, `isRoot`, `isLeaf`, `root`
  - `preOrder` for deterministic cursor traversal

## PureLogic-backed query effect

`QueryLogic` defines a PureLogic-backed alias and helpers for query analysis and
execution workflows:

- `QueryEffect[Ctx, Log, Err, A]` is backed by PureLogic capabilities.
- Context threading is provided by stateful `readContext` / `setContext` /
  `updateContext`.
- Log accumulation uses `log`.
- Error gathering uses `error` (accumulate) and `failFast` (abort).
- `run(initialContext)` returns context, logs, gathered errors, and final value.

## Staged query execution pipeline

`QueryExecutionPipeline` structures query execution into explicit phases:

1. `normalize`
2. `analyze`
3. `validate`
4. `lower`
5. `execute`

Each stage emits deterministic logs and returns typed intermediate values
(`Analysis`, `Plan`, `Lowered`), so future planner/optimizer work can insert new
phases without changing caller entry points.

## CST example

```scala
import io.eleven19.krueger.Krueger
import io.eleven19.krueger.cst.CstName
import io.eleven19.krueger.cst.CstQueryableTree.given
import io.eleven19.krueger.trees.CaptureName
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
    .flatMap(m => CaptureName.make("n").toOption.flatMap(cn => m.captures.get(cn)))
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
ships `#eq?`, `#match?`, `#not-eq?`, and `#not-match?`; callers register custom predicates per-query
without touching the matcher:

```scala
val registry = PredicateRegistry.default.withPredicate(
    PredicateName.make("#my-pred?").toOption.get,
    new PredicateImpl:
        def evaluate[T](args: PredicateArgs, captures: Map[CaptureName, T])(using
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
- Parsley-backed S-expression parser and matcher with full predicate support
  (`#eq?`, `#not-eq?`, `#match?`, `#not-match?`).
- Alternation (`[(A) (B)]`), quantifiers (`?`, `*`, `+`), anchors (`.`),
  negated fields (`!field`), and multi-pattern queries.
- `QueryPrinter` (lightweight, dependency-free) and `QueryPretty` (Kindlings-backed,
  configurable) for round-trip canonicalization.
- `QueryVisitor` (typed dispatch + fold/collect/traverse) and `QueryCursor` (zipper
  navigation) for query AST analysis.
- `QueryLogic` PureLogic effect and `QueryExecutionPipeline` staged execution.
- Given instances for `CstNode` and `AstNode`.
- `QuerySteps` BDD step pack with canonicalization verbs.

Follow-up in the v2 epic:

- `NodeTypes` type member on `QueryableTree[T]`.
- Mirror/Hearth-backed derived instances.
- Match-type `NodeByName[Name, T]` for typed captures.
- Trailing-anchor support (`.` at the end of a child sequence).
- Custom predicate syntax in query text.
