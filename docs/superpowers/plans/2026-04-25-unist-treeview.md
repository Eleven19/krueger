# Unist Treeview Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a unist-compatible CST/AST projection and use it to render AST Explorer-style tree panels in the playground.

**Architecture:** Build the feature in staged slices inside one PR. First add a generic `krueger.trees.unist` projection model on top of `QueryableTree[T]`, then add CST/AST adapters, then expose `parseCstUnist` / `parseAstUnist` through both JS and WebGC facades, then update the Svelte treeview to consume the new plain tree data.

**Tech Stack:** Scala 3, Mill, ZIO Test, Scala.js, Svelte 5, Vitest, Testing Library.

---

## File Structure

- Create `krueger/trees/src/io/eleven19/krueger/trees/unist/UnistNode.scala`
  - Owns the JSON-compatible unist data model: `UnistNode`, `UnistData`, `UnistPosition`, `UnistPoint`, and `UnistSpan`.
- Create `krueger/trees/src/io/eleven19/krueger/trees/unist/UnistProjection.scala`
  - Owns the generic projection algorithm from any `QueryableTree[T]` to `UnistNode`.
- Create `krueger/trees/test/src/io/eleven19/krueger/trees/UnistProjectionSpec.scala`
  - Tests generic projection behavior with a local S-expression fixture that exercises atoms, lists, and named pairs.
- Create `krueger/core/src/io/eleven19/krueger/cst/CstUnistProjection.scala`
  - Supplies CST span access and reuses `CstQueryableTree.given`.
- Create `krueger/core/src/io/eleven19/krueger/ast/AstUnistProjection.scala`
  - Supplies AST span access and reuses `AstQueryableTree.given`.
- Create `krueger/core/test/src/io/eleven19/krueger/cst/CstUnistProjectionSpec.scala`
  - Tests parsed CST projection order, fields, values, and positions.
- Create `krueger/core/test/src/io/eleven19/krueger/ast/AstUnistProjectionSpec.scala`
  - Tests parsed AST projection order, fields, values, and positions.
- Modify `krueger/webapp-wasm/src/io/eleven19/krueger/webappwasm/KruegerJs.scala`
  - Adds `parseCstUnist` / `parseAstUnist` and POJO serializers.
- Modify `krueger/webapp-wasm/wasm/src/io/eleven19/krueger/webappwasm/wasm/WasmFacade.scala`
  - Adds the same exported methods to `KruegerWasm`.
- Modify `krueger/webapp-wasm/test/src/io/eleven19/krueger/webappwasm/KruegerJsSpec.scala`
  - Tests facade success, malformed-source failure, and deterministic unist shape.
- Modify `sites/try-wasm/src/lib/krueger.ts`
  - Adds `UnistNode` TypeScript types, facade method validation, client methods, and normalizers.
- Modify `sites/try-wasm/src/lib/krueger.test.ts`
  - Tests real JS facade wrapper for unist methods.
- Modify `sites/try-wasm/src/routes/+page.svelte`
  - Computes `cstUnistResult` and `astUnistResult` for display while keeping existing opaque `cstResult` for query execution.
- Modify `sites/try-wasm/src/lib/components/ResultsPanel.svelte`
  - Passes unist results into `TreeView`.
- Modify `sites/try-wasm/src/lib/components/TreeView.svelte`
  - Replaces raw-only output with an explorer UI plus raw fallback and existing error display.
- Modify `sites/try-wasm/src/lib/components/activity-results.test.ts`
  - Tests nested tree rendering, expand/collapse, search, no-match state, raw fallback, and parse errors.

---

### Task 1: Generic Unist Model And Projection

**Files:**
- Create: `krueger/trees/src/io/eleven19/krueger/trees/unist/UnistNode.scala`
- Create: `krueger/trees/src/io/eleven19/krueger/trees/unist/UnistProjection.scala`
- Create: `krueger/trees/test/src/io/eleven19/krueger/trees/UnistProjectionSpec.scala`

- [ ] **Step 1: Write the failing generic projection tests**

Create `krueger/trees/test/src/io/eleven19/krueger/trees/UnistProjectionSpec.scala`:

```scala
package io.eleven19.krueger.trees

import zio.test.*

import io.eleven19.krueger.trees.unist.*

object UnistProjectionSpec extends ZIOSpecDefault:

    private enum SExpr derives CanEqual:
        case Atom(value: String)
        case ListExpr(head: SExpr, arguments: IndexedSeq[SExpr])
        case Pair(name: SExpr, value: SExpr)

    private object SExpr:
        val HeadField: FieldName      = FieldName.unsafeMake("head")
        val ArgumentsField: FieldName = FieldName.unsafeMake("arguments")
        val NameField: FieldName      = FieldName.unsafeMake("name")
        val ValueField: FieldName     = FieldName.unsafeMake("value")

        given QueryableTree[SExpr] with
            def nodeType(t: SExpr): NodeTypeName = t match
                case _: Atom     => NodeTypeName.unsafeMake("Atom")
                case _: ListExpr => NodeTypeName.unsafeMake("List")
                case _: Pair     => NodeTypeName.unsafeMake("Pair")

            def children(t: SExpr): Seq[SExpr] = t match
                case _: Atom                    => Seq.empty
                case ListExpr(head, arguments)  => head +: arguments
                case Pair(name, value)          => Seq(name, value)

            def fields(t: SExpr): Map[FieldName, Seq[SExpr]] = t match
                case _: Atom                   => Map.empty
                case ListExpr(head, arguments) => Map(HeadField -> Seq(head), ArgumentsField -> arguments)
                case Pair(name, value)         => Map(NameField -> Seq(name), ValueField -> Seq(value))

            def text(t: SExpr): Option[String] = t match
                case Atom(value) => Some(value)
                case _           => None

    import SExpr.*

    private val defAtom = Atom("def")
    private val nameKey = Atom("name")
    private val main    = Atom("main")
    private val namePair = Pair(nameKey, main)
    private val body = ListExpr(
        Atom("+"),
        IndexedSeq(Atom("1"), Atom("2"))
    )
    private val root = ListExpr(defAtom, IndexedSeq(namePair, body))

    private val zeroSpan: UnistSpan = UnistSpan(0, 0)
    private val rootSpan: UnistSpan = UnistSpan(0, 22)

    private given UnistProjection[SExpr] with
        def span(t: SExpr): Option[UnistSpan] = t match
            case `root`    => Some(rootSpan)
            case `defAtom` => Some(zeroSpan)
            case _         => None

    def spec = suite("UnistProjection")(
        test("projects node type, text, ordered children, and child count") {
            val node = UnistProjection.project(root)
            assertTrue(
                node.`type` == "List",
                node.children.map(_.`type`) == IndexedSeq("Atom", "Pair", "List"),
                node.children.head.value.contains("def"),
                node.children(1).children.map(_.value) == IndexedSeq(Some("name"), Some("main")),
                node.data.childCount == 3
            )
        },
        test("maps named fields to direct child indexes deterministically") {
            val node = UnistProjection.project(root)
            val pairNode = node.children(1)
            assertTrue(
                node.data.fields == Map("head" -> IndexedSeq(0), "arguments" -> IndexedSeq(1, 2)),
                pairNode.data.fields == Map("name" -> IndexedSeq(0), "value" -> IndexedSeq(1))
            )
        },
        test("omits position when source text is absent") {
            val node = UnistProjection.project(root)
            assertTrue(node.position.isEmpty)
        },
        test("converts zero-length and non-empty spans to one-based unist points when source text is present") {
            val node = UnistProjection.project(root, source = Some("(def (name main) (+ 1 2))"))
            val leafNode = node.children.head
            assertTrue(
                node.position.exists(_.start == UnistPoint(line = 1, column = 1, offset = Some(0))),
                node.position.exists(_.end == UnistPoint(line = 1, column = 23, offset = Some(22))),
                leafNode.position.exists(_.start == UnistPoint(line = 1, column = 1, offset = Some(0))),
                leafNode.position.exists(_.end == UnistPoint(line = 1, column = 1, offset = Some(0)))
            )
        }
    )
```

- [ ] **Step 2: Run the focused test and confirm red**

Run:

```bash
./mill --no-server krueger.trees.jvm.test.testOnly io.eleven19.krueger.trees.UnistProjectionSpec
```

Expected: compile failure mentioning missing `io.eleven19.krueger.trees.unist` symbols.

- [ ] **Step 3: Add the minimal unist model**

Create `krueger/trees/src/io/eleven19/krueger/trees/unist/UnistNode.scala`:

```scala
package io.eleven19.krueger.trees.unist

final case class UnistPoint(
    line: Int,
    column: Int,
    offset: Option[Int] = None
) derives CanEqual

final case class UnistPosition(
    start: UnistPoint,
    end: UnistPoint
) derives CanEqual

final case class UnistData(
    fields: Map[String, IndexedSeq[Int]] = Map.empty,
    childCount: Int = 0
) derives CanEqual

object UnistData:
    val empty: UnistData = UnistData()

final case class UnistNode(
    `type`: String,
    value: Option[String] = None,
    position: Option[UnistPosition] = None,
    data: UnistData = UnistData.empty,
    children: IndexedSeq[UnistNode] = IndexedSeq.empty
) derives CanEqual

final case class UnistSpan(start: Int, end: Int) derives CanEqual

object UnistSpan:
    def fromOffsetLength(offset: Int, length: Int): UnistSpan =
        UnistSpan(offset, offset + length)
```

- [ ] **Step 4: Add the minimal projection implementation**

Create `krueger/trees/src/io/eleven19/krueger/trees/unist/UnistProjection.scala`:

```scala
package io.eleven19.krueger.trees.unist

import io.eleven19.krueger.trees.FieldName
import io.eleven19.krueger.trees.NodeTypeName
import io.eleven19.krueger.trees.QueryableTree

trait UnistProjection[T]:
    def span(t: T): Option[UnistSpan]

object UnistProjection:

    def apply[T](using projection: UnistProjection[T]): UnistProjection[T] = projection

    def project[T](root: T, source: Option[String] = None)(using
        qt: QueryableTree[T],
        projection: UnistProjection[T]
    ): UnistNode =
        val points = source.map(SourcePoints.from)
        projectNode(root, points)

    private def projectNode[T](node: T, source: Option[SourcePoints])(using
        qt: QueryableTree[T],
        projection: UnistProjection[T]
    ): UnistNode =
        val children = qt.children(node).toIndexedSeq
        val projectedChildren = children.map(child => projectNode(child, source))
        UnistNode(
            `type` = NodeTypeName.unwrap(qt.nodeType(node)),
            value = qt.text(node),
            position = source.flatMap(points => projection.span(node).map(points.position)),
            data = UnistData(
                fields = fieldIndexes(qt.fields(node), children),
                childCount = children.size
            ),
            children = projectedChildren
        )

    private def fieldIndexes[T](
        fields: Map[FieldName, Seq[T]],
        children: IndexedSeq[T]
    ): Map[String, IndexedSeq[Int]] =
        fields.iterator.map { case (name, values) =>
            FieldName.unwrap(name) -> values.toIndexedSeq.flatMap(value => childIndex(value, children))
        }.toMap

    private def childIndex[T](value: T, children: IndexedSeq[T]): Option[Int] =
        val identityIndex = children.indexWhere(child => sameReference(child, value))
        if identityIndex >= 0 then Some(identityIndex)
        else
            val equalityIndex = children.indexWhere(_ == value)
            Option.when(equalityIndex >= 0)(equalityIndex)

    private def sameReference[A](left: A, right: A): Boolean =
        (left, right) match
            case (l: AnyRef, r: AnyRef) => l eq r
            case _                      => false

private final class SourcePoints private (lineStarts: IndexedSeq[Int]):
    def position(span: UnistSpan): UnistPosition =
        UnistPosition(pointAt(span.start), pointAt(span.end))

    private def pointAt(offset: Int): UnistPoint =
        val bounded = math.max(0, offset)
        val lineIndex = lineStarts.lastIndexWhere(_ <= bounded) match
            case -1    => 0
            case found => found
        val lineStart = lineStarts(lineIndex)
        UnistPoint(line = lineIndex + 1, column = bounded - lineStart + 1, offset = Some(bounded))

private object SourcePoints:
    def from(source: String): SourcePoints =
        val starts = IndexedSeq.newBuilder[Int]
        starts += 0
        source.zipWithIndex.foreach { case (ch, index) =>
            if ch == '\n' then starts += index + 1
        }
        SourcePoints(starts.result())
```

- [ ] **Step 5: Run focused tests and confirm green**

Run:

```bash
./mill --no-server krueger.trees.jvm.test.testOnly io.eleven19.krueger.trees.UnistProjectionSpec
```

Expected: all `UnistProjectionSpec` tests pass.

- [ ] **Step 6: Run existing trees tests for regression**

Run:

```bash
./mill --no-server krueger.trees.jvm.test
```

Expected: all `krueger.trees.jvm.test` tests pass.

- [ ] **Step 7: Commit Task 1**

```bash
git add krueger/trees/src/io/eleven19/krueger/trees/unist krueger/trees/test/src/io/eleven19/krueger/trees/UnistProjectionSpec.scala
git commit -m "feat: add generic unist projection"
```

---

### Task 2: CST And AST Unist Projection Instances

**Files:**
- Create: `krueger/core/src/io/eleven19/krueger/cst/CstUnistProjection.scala`
- Create: `krueger/core/src/io/eleven19/krueger/ast/AstUnistProjection.scala`
- Create: `krueger/core/test/src/io/eleven19/krueger/cst/CstUnistProjectionSpec.scala`
- Create: `krueger/core/test/src/io/eleven19/krueger/ast/AstUnistProjectionSpec.scala`

- [ ] **Step 1: Write the failing CST projection tests**

Create `krueger/core/test/src/io/eleven19/krueger/cst/CstUnistProjectionSpec.scala`:

```scala
package io.eleven19.krueger.cst

import parsley.{Failure, Success}
import zio.test.*

import io.eleven19.krueger.Krueger
import io.eleven19.krueger.cst.CstQueryableTree.given
import io.eleven19.krueger.cst.CstUnistProjection.given
import io.eleven19.krueger.trees.unist.UnistPoint
import io.eleven19.krueger.trees.unist.UnistProjection

object CstUnistProjectionSpec extends ZIOSpecDefault:

    private val source =
        """module App exposing (..)
          |
          |main = 42
          |""".stripMargin

    private def parse(src: String): CstModule = Krueger.parseCst(src) match
        case Success(value) => value
        case Failure(msg)  => throw AssertionError(s"parse failed: $msg")

    private val moduleTree = parse(source)

    def spec = suite("CstUnistProjection")(
        test("projects parsed CST root with type, child order, fields, and source position") {
            val node = UnistProjection.project(moduleTree, Some(source))
            assertTrue(
                node.`type` == "CstModule",
                node.children.map(_.`type`).contains("CstModuleDeclaration"),
                node.data.fields.keySet == Set("moduleDecl", "imports", "declarations"),
                node.data.fields("moduleDecl") == IndexedSeq(0),
                node.position.exists(_.start == UnistPoint(1, 1, Some(0)))
            )
        },
        test("projects CstName leaves with value text") {
            val node = UnistProjection.project(moduleTree, Some(source))
            val names = collect(node).filter(_.`type` == "CstName").flatMap(_.value)
            assertTrue(names.contains("App"), names.contains("main"))
        },
        test("preserves QueryableTree child count on every projected node") {
            val node = UnistProjection.project(moduleTree, Some(source))
            assertTrue(collect(node).forall(n => n.data.childCount == n.children.size))
        }
    )

    private def collect(node: io.eleven19.krueger.trees.unist.UnistNode): List[io.eleven19.krueger.trees.unist.UnistNode] =
        node :: node.children.toList.flatMap(collect)
```

- [ ] **Step 2: Write the failing AST projection tests**

Create `krueger/core/test/src/io/eleven19/krueger/ast/AstUnistProjectionSpec.scala`:

```scala
package io.eleven19.krueger.ast

import parsley.{Failure, Success}
import zio.test.*

import io.eleven19.krueger.Krueger
import io.eleven19.krueger.ast.AstQueryableTree.given
import io.eleven19.krueger.ast.AstUnistProjection.given
import io.eleven19.krueger.trees.unist.UnistProjection

object AstUnistProjectionSpec extends ZIOSpecDefault:

    private val source =
        """module App exposing (..)
          |
          |main = 42
          |""".stripMargin

    private def parse(src: String): Module = Krueger.parseAst(src) match
        case Success(value) => value
        case Failure(msg)  => throw AssertionError(s"parse failed: $msg")

    private val moduleTree = parse(source)

    def spec = suite("AstUnistProjection")(
        test("projects parsed AST root with type, fields, and child count") {
            val node = UnistProjection.project(moduleTree, Some(source))
            assertTrue(
                node.`type` == "Module",
                node.data.fields.keySet == Set("exposing", "imports", "declarations"),
                node.data.childCount == node.children.size
            )
        },
        test("projects AST declaration and literal text values") {
            val node = UnistProjection.project(moduleTree, Some(source))
            val values = collect(node).flatMap(_.value)
            assertTrue(values.contains("main"), values.contains("42"))
        },
        test("preserves AST traversal order for the module children") {
            val node = UnistProjection.project(moduleTree, Some(source))
            assertTrue(node.children.map(_.`type`).headOption.contains("ExposingAll"))
        }
    )

    private def collect(node: io.eleven19.krueger.trees.unist.UnistNode): List[io.eleven19.krueger.trees.unist.UnistNode] =
        node :: node.children.toList.flatMap(collect)
```

- [ ] **Step 3: Run focused tests and confirm red**

Run:

```bash
./mill --no-server krueger.core.jvm.test.testOnly io.eleven19.krueger.cst.CstUnistProjectionSpec io.eleven19.krueger.ast.AstUnistProjectionSpec
```

Expected: compile failure mentioning missing `CstUnistProjection` and `AstUnistProjection`.

- [ ] **Step 4: Add CST projection instance**

Create `krueger/core/src/io/eleven19/krueger/cst/CstUnistProjection.scala`:

```scala
package io.eleven19.krueger.cst

import io.eleven19.krueger.cst.CstQueryableTree.given
import io.eleven19.krueger.trees.QueryableTree
import io.eleven19.krueger.trees.unist.UnistProjection
import io.eleven19.krueger.trees.unist.UnistSpan

object CstUnistProjection:
    given projection: UnistProjection[CstNode] with
        def span(t: CstNode): Option[UnistSpan] =
            Some(UnistSpan.fromOffsetLength(t.span.offset, t.span.length))

    export CstQueryableTree.given
```

- [ ] **Step 5: Add AST projection instance**

Create `krueger/core/src/io/eleven19/krueger/ast/AstUnistProjection.scala`:

```scala
package io.eleven19.krueger.ast

import io.eleven19.krueger.ast.AstQueryableTree.given
import io.eleven19.krueger.trees.unist.UnistProjection
import io.eleven19.krueger.trees.unist.UnistSpan

object AstUnistProjection:
    given projection: UnistProjection[AstNode] with
        def span(t: AstNode): Option[UnistSpan] =
            Some(UnistSpan.fromOffsetLength(t.span.offset, t.span.length))

    export AstQueryableTree.given
```

- [ ] **Step 6: Run focused tests and confirm green**

Run:

```bash
./mill --no-server krueger.core.jvm.test.testOnly io.eleven19.krueger.cst.CstUnistProjectionSpec io.eleven19.krueger.ast.AstUnistProjectionSpec
```

Expected: both projection specs pass.

- [ ] **Step 7: Run existing core tests for regression**

Run:

```bash
./mill --no-server krueger.core.jvm.test
```

Expected: all `krueger.core.jvm.test` tests pass.

- [ ] **Step 8: Commit Task 2**

```bash
git add krueger/core/src/io/eleven19/krueger/cst/CstUnistProjection.scala krueger/core/src/io/eleven19/krueger/ast/AstUnistProjection.scala krueger/core/test/src/io/eleven19/krueger/cst/CstUnistProjectionSpec.scala krueger/core/test/src/io/eleven19/krueger/ast/AstUnistProjectionSpec.scala
git commit -m "feat: project cst and ast to unist"
```

---

### Task 3: JS And WebGC Facade Unist Exports

**Files:**
- Modify: `krueger/webapp-wasm/src/io/eleven19/krueger/webappwasm/KruegerJs.scala`
- Modify: `krueger/webapp-wasm/wasm/src/io/eleven19/krueger/webappwasm/wasm/WasmFacade.scala`
- Modify: `krueger/webapp-wasm/test/src/io/eleven19/krueger/webappwasm/KruegerJsSpec.scala`

- [ ] **Step 1: Write failing facade tests**

Append tests inside `KruegerJsSpec`:

```scala
        suite("unist facade exports")(
            test("parseCstUnist returns a plain unist object for valid source") {
                val env  = dyn(KruegerJs.parseCstUnist(validSource))
                val root = env.value.asInstanceOf[js.Dynamic]
                assertTrue(
                    hasEnvelopeShape(env.asInstanceOf[js.Object]),
                    env.ok.asInstanceOf[Boolean],
                    root.`type`.asInstanceOf[String] == "CstModule",
                    js.Array.isArray(root.children),
                    root.data.childCount.asInstanceOf[Int] == root.children.asInstanceOf[js.Array[js.Any]].length
                )
            },
            test("parseAstUnist returns a plain unist object for valid source") {
                val env  = dyn(KruegerJs.parseAstUnist(validSource))
                val root = env.value.asInstanceOf[js.Dynamic]
                assertTrue(
                    hasEnvelopeShape(env.asInstanceOf[js.Object]),
                    env.ok.asInstanceOf[Boolean],
                    root.`type`.asInstanceOf[String] == "Module",
                    js.Array.isArray(root.children)
                )
            },
            test("parseCstUnist preserves parse errors for malformed source") {
                val env = dyn(KruegerJs.parseCstUnist(malformedSource))
                assertTrue(
                    hasEnvelopeShape(env.asInstanceOf[js.Object]),
                    !env.ok.asInstanceOf[Boolean],
                    env.value == null,
                    arrayLen(env.errors) >= 1
                )
            }
        ),
```

- [ ] **Step 2: Run focused test and confirm red**

Run:

```bash
./mill --no-server krueger.webapp-wasm.test.testOnly io.eleven19.krueger.webappwasm.KruegerJsSpec
```

Expected: compile failure mentioning missing `parseCstUnist` and `parseAstUnist`.

- [ ] **Step 3: Add facade methods and serializers**

Modify `KruegerJs.scala`:

```scala
import io.eleven19.krueger.ast.AstNode
import io.eleven19.krueger.ast.AstUnistProjection.given
import io.eleven19.krueger.cst.CstUnistProjection.given
import io.eleven19.krueger.trees.unist.UnistNode
import io.eleven19.krueger.trees.unist.UnistPoint
import io.eleven19.krueger.trees.unist.UnistPosition
import io.eleven19.krueger.trees.unist.UnistProjection
```

Add exports:

```scala
    @JSExport
    def parseCstUnist(src: String): js.Object =
        val parsed = LinkedCompilerBackend.parseCst(src)
        envelopeWithUnist(parsed.value.toOption.map(root => UnistProjection.project(root: CstNode, Some(src))), parsed)

    @JSExport
    def parseAstUnist(src: String): js.Object =
        val parsed = LinkedCompilerBackend.parseAst(src)
        envelopeWithUnist(parsed.value.toOption.map(root => UnistProjection.project(root: AstNode, Some(src))), parsed)
```

Add helpers:

```scala
    private def envelopeWithUnist[Ctx, A](value: Option[UnistNode], r: CompileResult[Ctx, A]): js.Object =
        val env = js.Dynamic.literal()
        value match
            case Some(node) =>
                env.updateDynamic("ok")(true)
                env.updateDynamic("value")(unistNodePojo(node))
            case None =>
                env.updateDynamic("ok")(false)
                env.updateDynamic("value")(null)
        attachLogsAndErrors(env, r)
        env.asInstanceOf[js.Object]

    private def unistNodePojo(node: UnistNode): js.Object =
        val o = js.Dynamic.literal(
            "type" -> node.`type`,
            "data" -> unistDataPojo(node),
            "children" -> node.children.map(unistNodePojo).toJSArray.asInstanceOf[js.Any]
        )
        node.value.foreach(value => o.updateDynamic("value")(value))
        node.position.foreach(position => o.updateDynamic("position")(positionPojo(position)))
        o.asInstanceOf[js.Object]

    private def unistDataPojo(node: UnistNode): js.Object =
        val fields = js.Dynamic.literal()
        node.data.fields.foreach((name, indexes) => fields.updateDynamic(name)(indexes.toJSArray.asInstanceOf[js.Any]))
        js.Dynamic.literal(fields = fields, childCount = node.data.childCount).asInstanceOf[js.Object]

    private def positionPojo(position: UnistPosition): js.Object =
        js.Dynamic.literal(start = pointPojo(position.start), end = pointPojo(position.end)).asInstanceOf[js.Object]

    private def pointPojo(point: UnistPoint): js.Object =
        val o = js.Dynamic.literal(line = point.line, column = point.column)
        point.offset.foreach(offset => o.updateDynamic("offset")(offset))
        o.asInstanceOf[js.Object]
```

- [ ] **Step 4: Add WebGC export methods**

Modify `WasmFacade.scala` so the exported `KruegerWasm` literal includes:

```scala
            parseCstUnist = ((src: String) => KruegerJs.parseCstUnist(src)): js.Function1[String, js.Object],
            parseAstUnist = ((src: String) => KruegerJs.parseAstUnist(src)): js.Function1[String, js.Object],
```

- [ ] **Step 5: Run facade tests and confirm green**

Run:

```bash
./mill --no-server krueger.webapp-wasm.test.testOnly io.eleven19.krueger.webappwasm.KruegerJsSpec
```

Expected: `KruegerJsSpec` passes.

- [ ] **Step 6: Run WebGC facade tests**

Run:

```bash
./mill --no-server krueger.webapp-wasm.wasm.test.testOnly io.eleven19.krueger.webappwasm.KruegerJsSpec
```

Expected: WebGC-linked facade tests pass with the same methods.

- [ ] **Step 7: Commit Task 3**

```bash
git add krueger/webapp-wasm/src/io/eleven19/krueger/webappwasm/KruegerJs.scala krueger/webapp-wasm/wasm/src/io/eleven19/krueger/webappwasm/wasm/WasmFacade.scala krueger/webapp-wasm/test/src/io/eleven19/krueger/webappwasm/KruegerJsSpec.scala
git commit -m "feat: expose unist trees from web facade"
```

---

### Task 4: TypeScript Client Unist Normalization

**Files:**
- Modify: `sites/try-wasm/src/lib/krueger.ts`
- Modify: `sites/try-wasm/src/lib/krueger.test.ts`

- [ ] **Step 1: Write failing TypeScript client tests**

Add to `sites/try-wasm/src/lib/krueger.test.ts`:

```ts
  it('parses CST and AST unist trees through the linked facade artifact', async () => {
    const krueger = await createKruegerClient('js', { facadeUrl });

    const cst = krueger.parseCstUnist(validSource);
    const ast = krueger.parseAstUnist(validSource);

    expectEnvelope(cst);
    expectEnvelope(ast);
    expect(cst.ok).toBe(true);
    expect(ast.ok).toBe(true);
    expect(cst.value?.type).toBe('CstModule');
    expect(ast.value?.type).toBe('Module');
    expect(cst.value?.data.childCount).toBe(cst.value?.children.length);
    expect(cst.value?.position?.start).toMatchObject({ line: 1, column: 1, offset: 0 });
  });

  it('returns existing error envelopes for malformed unist parses', async () => {
    const krueger = await createKruegerClient('js', { facadeUrl });
    const cst = krueger.parseCstUnist(malformedSource);

    expectEnvelope(cst);
    expect(cst.ok).toBe(false);
    expect(cst.value).toBeNull();
    expect(cst.errors[0]?.phase).toBe('cst');
  });
```

- [ ] **Step 2: Run focused tests and confirm red**

Run from `sites/try-wasm`:

```bash
npm run build:wasm && npx vitest run src/lib/krueger.test.ts
```

Expected: TypeScript compile failure mentioning missing `parseCstUnist` / `parseAstUnist`.

- [ ] **Step 3: Add TypeScript unist types and client methods**

Modify `sites/try-wasm/src/lib/krueger.ts`:

```ts
export type UnistPoint = {
  line: number;
  column: number;
  offset?: number;
};

export type UnistPosition = {
  start: UnistPoint;
  end: UnistPoint;
};

export type UnistNode = {
  type: string;
  value?: string;
  position?: UnistPosition;
  data: {
    fields: Record<string, number[]>;
    childCount: number;
  };
  children: UnistNode[];
};
```

Extend `RawKruegerFacade` and `KruegerClient`:

```ts
  parseCstUnist(source: string): unknown;
  parseAstUnist(source: string): unknown;
```

```ts
  parseCstUnist(source: string): CompilerEnvelope<UnistNode>;
  parseAstUnist(source: string): CompilerEnvelope<UnistNode>;
```

Add returned client methods:

```ts
    parseCstUnist(source) {
      return invokeEnvelope<UnistNode>(() => facade.parseCstUnist(source), normalizeUnistNode);
    },
    parseAstUnist(source) {
      return invokeEnvelope<UnistNode>(() => facade.parseAstUnist(source), normalizeUnistNode);
    },
```

Add normalizers:

```ts
function normalizeUnistNode(value: unknown): UnistNode {
  const record = asRecord(value);
  return {
    type: String(record.type ?? ''),
    ...(record.value == null ? {} : { value: String(record.value) }),
    ...(record.position == null ? {} : { position: normalizeUnistPosition(record.position) }),
    data: normalizeUnistData(record.data),
    children: Array.isArray(record.children) ? record.children.map(normalizeUnistNode) : []
  };
}

function normalizeUnistData(value: unknown): UnistNode['data'] {
  const record = asRecord(value);
  const rawFields = asRecord(record.fields);
  return {
    childCount: Number(record.childCount ?? 0),
    fields: Object.fromEntries(
      Object.entries(rawFields).map(([name, indexes]) => [
        name,
        Array.isArray(indexes) ? indexes.map((index) => Number(index)) : []
      ])
    )
  };
}

function normalizeUnistPosition(value: unknown): UnistPosition {
  const record = asRecord(value);
  return {
    start: normalizeUnistPoint(record.start),
    end: normalizeUnistPoint(record.end)
  };
}

function normalizeUnistPoint(value: unknown): UnistPoint {
  const record = asRecord(value);
  return {
    line: Number(record.line ?? 1),
    column: Number(record.column ?? 1),
    ...(record.offset == null ? {} : { offset: Number(record.offset) })
  };
}
```

Update `isKruegerFacade` to require both new methods:

```ts
    typeof record.parseCstUnist === 'function' &&
    typeof record.parseAstUnist === 'function' &&
```

- [ ] **Step 4: Run focused TypeScript tests and confirm green**

Run from `sites/try-wasm`:

```bash
npm run build:wasm && npx vitest run src/lib/krueger.test.ts
```

Expected: `krueger.test.ts` passes.

- [ ] **Step 5: Commit Task 4**

```bash
git add sites/try-wasm/src/lib/krueger.ts sites/try-wasm/src/lib/krueger.test.ts
git commit -m "feat: normalize unist trees in playground client"
```

---

### Task 5: Playground AST Explorer Treeview

**Files:**
- Modify: `sites/try-wasm/src/routes/+page.svelte`
- Modify: `sites/try-wasm/src/lib/components/ResultsPanel.svelte`
- Modify: `sites/try-wasm/src/lib/components/TreeView.svelte`
- Modify: `sites/try-wasm/src/lib/components/activity-results.test.ts`

- [ ] **Step 1: Update tests for tree explorer behavior**

In `activity-results.test.ts`, update `resultProps` so CST/AST values can be `UnistNode` fixtures:

```ts
import type { CompilerEnvelope, MatchView, UnistNode } from '$lib/krueger';

const tree: UnistNode = {
  type: 'CstModule',
  data: { childCount: 2, fields: { moduleDecl: [0], declarations: [1] } },
  children: [
    {
      type: 'CstModuleDeclaration',
      value: 'App',
      data: { childCount: 0, fields: {} },
      children: []
    },
    {
      type: 'CstValueDeclaration',
      value: 'main',
      data: { childCount: 1, fields: { body: [0] } },
      children: [
        {
          type: 'CstIntLiteral',
          value: '42',
          data: { childCount: 0, fields: {} },
          children: []
        }
      ]
    }
  ]
};
```

Add tests:

```ts
  it('renders a collapsible tree for CST output', async () => {
    render(ResultsPanel, resultProps({ selectedPanel: 'cst', cstResult: ok(tree) }));

    expect(screen.getByRole('tree', { name: 'CST tree' })).not.toBeNull();
    expect(screen.getByText('CstModule')).not.toBeNull();
    expect(screen.getByText('CstValueDeclaration')).not.toBeNull();

    await fireEvent.click(screen.getByRole('button', { name: 'Collapse all' }));
    expect(screen.queryByText('CstIntLiteral')).toBeNull();

    await fireEvent.click(screen.getByRole('button', { name: 'Expand all' }));
    expect(screen.getByText('CstIntLiteral')).not.toBeNull();
  });

  it('filters tree nodes with a no-match state', async () => {
    render(ResultsPanel, resultProps({ selectedPanel: 'cst', cstResult: ok(tree) }));

    await fireEvent.input(screen.getByRole('searchbox', { name: 'Filter tree nodes' }), {
      target: { value: 'IntLiteral' }
    });
    expect(screen.getByText('CstIntLiteral')).not.toBeNull();

    await fireEvent.input(screen.getByRole('searchbox', { name: 'Filter tree nodes' }), {
      target: { value: 'NoSuchNode' }
    });
    expect(screen.getByText('No matching nodes.')).not.toBeNull();
  });

  it('falls back to raw text for non-unist values', () => {
    render(ResultsPanel, resultProps({ selectedPanel: 'ast', astResult: ok('Module(...)') }));

    expect(screen.getByRole('button', { name: 'Raw' })).not.toBeNull();
    expect(screen.getByText('Module(...)')).not.toBeNull();
  });
```

- [ ] **Step 2: Run UI tests and confirm red**

Run from `sites/try-wasm`:

```bash
npx vitest run src/lib/components/activity-results.test.ts
```

Expected: assertions fail because `TreeView.svelte` still renders only `<pre>`.

- [ ] **Step 3: Wire unist results through the page and panel**

Modify `sites/try-wasm/src/routes/+page.svelte`:

```svelte
  const cstUnistResult = $derived(
    compilerEnvelope(() => client?.parseCstUnist(source), "Compiler loading...")
  );
  const astUnistResult = $derived(
    compilerEnvelope(() => client?.parseAstUnist(source), "Compiler loading...")
  );
```

Pass both props into `ResultsPanel`:

```svelte
      {cstUnistResult}
      {astUnistResult}
```

Modify `ResultsPanel.svelte` props:

```ts
    cstUnistResult: CompilerEnvelope<unknown>;
    astUnistResult: CompilerEnvelope<unknown>;
```

Render:

```svelte
      <TreeView result={cstUnistResult} label="CST" />
```

```svelte
      <TreeView result={astUnistResult} label="AST" errorTitle="AST errors:" />
```

- [ ] **Step 4: Implement the TreeView explorer**

Replace `TreeView.svelte` script with this structure:

```svelte
<script lang="ts">
  import type { CompilerEnvelope, UnistNode } from '$lib/krueger';

  let {
    result,
    label = 'Parse',
    errorTitle = 'Parse errors:'
  }: {
    result: CompilerEnvelope<unknown>;
    label?: string;
    errorTitle?: string;
  } = $props();

  let query = $state('');
  let rawMode = $state(false);
  let collapsed = $state<Set<string>>(new Set());

  const tree = $derived(isUnistNode(result.value) ? result.value : null);
  const textValue = $derived(result.value == null ? '' : String(result.value));
  const visiblePaths = $derived(tree == null ? new Set<string>() : matchingPaths(tree, query.trim().toLowerCase()));
  const hasVisibleNodes = $derived(tree == null || query.trim() === '' || visiblePaths.size > 0);

  function isUnistNode(value: unknown): value is UnistNode {
    return (
      value !== null &&
      typeof value === 'object' &&
      typeof (value as UnistNode).type === 'string' &&
      Array.isArray((value as UnistNode).children)
    );
  }

  function setAllCollapsed(next: boolean): void {
    if (tree == null) return;
    collapsed = next ? new Set(collectExpandablePaths(tree)) : new Set();
  }

  function toggle(path: string): void {
    const next = new Set(collapsed);
    if (next.has(path)) next.delete(path);
    else next.add(path);
    collapsed = next;
  }

  function collectExpandablePaths(node: UnistNode, path = '0'): string[] {
    const childPaths = node.children.flatMap((child, index) => collectExpandablePaths(child, `${path}.${index}`));
    return node.children.length > 0 ? [path, ...childPaths] : childPaths;
  }

  function matchingPaths(node: UnistNode, needle: string, path = '0'): Set<string> {
    const matches = new Set<string>();
    const childMatches = node.children.map((child, index) => matchingPaths(child, needle, `${path}.${index}`));
    childMatches.forEach((set) => set.forEach((value) => matches.add(value)));
    if (needle === '' || nodeLabel(node).toLowerCase().includes(needle) || matches.size > 0) matches.add(path);
    return matches;
  }

  function nodeLabel(node: UnistNode): string {
    return [node.type, node.value ?? ''].join(' ').trim();
  }
</script>
```

Implement markup with recursive snippet:

```svelte
{#snippet NodeRow(node: UnistNode, path: string, depth: number)}
  {@const expanded = !collapsed.has(path)}
  {@const visible = query.trim() === '' || visiblePaths.has(path)}
  {#if visible}
    <li role="treeitem" aria-expanded={node.children.length > 0 ? expanded : undefined}>
      <div class="node-row" style={`--depth: ${depth}`}>
        {#if node.children.length > 0}
          <button type="button" class="disclosure" aria-label={`${expanded ? 'Collapse' : 'Expand'} ${node.type}`} onclick={() => toggle(path)}>
            {expanded ? '▾' : '▸'}
          </button>
        {:else}
          <span class="disclosure-spacer"></span>
        {/if}
        <span class="node-type">{node.type}</span>
        {#if node.value}
          <span class="node-value">{node.value}</span>
        {/if}
        <span class="node-count">{node.data.childCount}</span>
      </div>
      {#if expanded && node.children.length > 0}
        <ul role="group">
          {#each node.children as child, index}
            {@render NodeRow(child, `${path}.${index}`, depth + 1)}
          {/each}
        </ul>
      {/if}
    </li>
  {/if}
{/snippet}
```

Keep the existing error-card branch. Add toolbar:

```svelte
<div class="tree-toolbar">
  <input type="search" aria-label="Filter tree nodes" bind:value={query} />
  <button type="button" onclick={() => setAllCollapsed(false)}>Expand all</button>
  <button type="button" onclick={() => setAllCollapsed(true)}>Collapse all</button>
  <button type="button" onclick={() => (rawMode = !rawMode)}>{rawMode ? 'Tree' : 'Raw'}</button>
</div>
```

Render tree or raw:

```svelte
{:else if tree != null && !rawMode}
  <div class="tree-shell">
    <!-- toolbar here -->
    {#if hasVisibleNodes}
      <ul class="tree-list" role="tree" aria-label={`${label} tree`}>
        {@render NodeRow(tree, '0', 0)}
      </ul>
    {:else}
      <p class="empty">No matching nodes.</p>
    {/if}
  </div>
{:else}
  <div class="tree-shell">
    <!-- toolbar here when tree exists -->
    <pre class="tree-body">{textValue}</pre>
  </div>
{/if}
```

Style with stable row dimensions, no nested cards:

```css
  .tree-view,
  .tree-shell {
    display: grid;
    gap: 0.75rem;
  }

  .tree-toolbar {
    display: flex;
    gap: 0.5rem;
    align-items: center;
    flex-wrap: wrap;
  }

  .tree-toolbar input {
    min-width: 12rem;
    flex: 1;
    color: var(--kr-text);
    background: var(--kr-panel-bg-strong);
    border: 1px solid var(--kr-border);
    border-radius: 0.375rem;
    padding: 0.45rem 0.6rem;
  }

  .tree-toolbar button,
  .disclosure {
    color: var(--kr-text);
    background: var(--kr-panel-bg-strong);
    border: 1px solid var(--kr-border);
    border-radius: 0.375rem;
  }

  .tree-list,
  .tree-list ul {
    margin: 0;
    padding: 0;
    list-style: none;
  }

  .tree-list {
    min-height: 16rem;
    overflow: auto;
    background: var(--kr-panel-bg-strong);
    border: 1px solid var(--kr-border);
    border-radius: 0.625rem;
    padding: 0.5rem 0;
  }

  .node-row {
    display: grid;
    grid-template-columns: 1.5rem minmax(0, max-content) minmax(0, 1fr) auto;
    gap: 0.5rem;
    align-items: center;
    min-height: 1.75rem;
    padding: 0.125rem 0.75rem 0.125rem calc(0.75rem + var(--depth) * 1rem);
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
    font-size: 0.8rem;
  }

  .node-type {
    color: var(--kr-accent);
    font-weight: 700;
  }

  .node-value,
  .node-count,
  .empty {
    color: var(--kr-muted);
  }
```

- [ ] **Step 5: Run UI tests and confirm green**

Run from `sites/try-wasm`:

```bash
npx vitest run src/lib/components/activity-results.test.ts
```

Expected: `activity-results.test.ts` passes.

- [ ] **Step 6: Run all try-wasm tests**

Run from `sites/try-wasm`:

```bash
npm test
```

Expected: all Vitest tests pass after building compiler artifacts.

- [ ] **Step 7: Commit Task 5**

```bash
git add sites/try-wasm/src/routes/+page.svelte sites/try-wasm/src/lib/components/ResultsPanel.svelte sites/try-wasm/src/lib/components/TreeView.svelte sites/try-wasm/src/lib/components/activity-results.test.ts
git commit -m "feat: render unist tree explorer in playground"
```

---

### Task 6: Final Verification And PR-Ready State

**Files:**
- Modify: `.beads/issues.jsonl`

- [ ] **Step 1: Run Scala verification**

Run:

```bash
./mill --no-server krueger.trees.jvm.test krueger.core.jvm.test krueger.webapp-wasm.test krueger.webapp-wasm.wasm.test
```

Expected: all listed Mill test targets pass.

- [ ] **Step 2: Run playground verification**

Run from `sites/try-wasm`:

```bash
npm test
```

Expected: compiler artifacts build and all Vitest tests pass.

- [ ] **Step 3: Run docs/playground smoke checks**

Run:

```bash
node docs/scripts/check-webapp-wasm-browser.mjs
node docs/scripts/check-playground-e2e.mjs
```

Expected: both scripts exit successfully and report no playground regressions.

- [ ] **Step 4: Close Beads issue**

Run:

```bash
bd close musing-chaum-7e7242-3nb --reason "Added unist projection model, CST/AST adapters, JS/WebGC facade exports, TypeScript normalization, and AST Explorer-style playground treeview with tests."
```

Expected: issue `musing-chaum-7e7242-3nb` is closed.

- [ ] **Step 5: Commit final tracker state**

```bash
git add .beads/issues.jsonl
git commit -m "chore: close unist treeview issue"
```

- [ ] **Step 6: Push branch**

```bash
git push
```

Expected: `feature-unist-treeview` is up to date with `origin/feature-unist-treeview`.

---

## Self-Review Notes

- Spec coverage: Tasks 1 and 2 cover `REQ-unist-001` through `REQ-unist-004`; Tasks 3 and 4 cover `REQ-unist-005` and `REQ-unist-006`; Task 5 covers the user-visible AST Explorer treeview; Task 6 covers final quality gates.
- Type consistency: Scala model names are `UnistNode`, `UnistData`, `UnistPosition`, `UnistPoint`, and `UnistSpan`; TypeScript mirrors the browser-visible subset.
- Test coverage: each behavior slice starts with a failing test, then minimal implementation, then focused and broader verification.
