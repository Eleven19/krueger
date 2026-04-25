# PR-A: Kyo Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire Kyo (`io.getkyo:kyo-* :: >= 1.0-RC1+214-534321a9-SNAPSHOT`) and scribe (`com.outr::scribe :: 3.16.1`) into the Krueger Mill build, then add behavior-preserving Kyo-aware variants of the visitor model, a `KyoQueryableTree` extension, a `Stage[I, O, S]` abstraction in `compiler-api`, and a scribe-backed `Log` handler — all alongside the existing pure APIs.

**Architecture:** PR-A is additive only. Pure visitors (`CstVisitor` / `AstVisitor` / `QueryVisitor`) keep their cursor API unchanged. We add `Kyo*Visitor` siblings that accept `Node => A < S` callbacks and a `KyoQueryableTree` extension over `QueryableTree[T]`. `Stage[I, O, S]` in `compiler-api` provides `>>>` composition with effect-row preservation. Logging flows through Kyo's `Log` effect; production layers wire `ScribeLogHandler` (a thin in-house bridge to scribe), tests wire `InMemoryLogRecorder`. Cross-platform: JVM + JS + Native where the Kyo modules support the target.

**Tech Stack:**
- Scala 3.8.3 (existing project compiler)
- Mill 1.1.5 (existing build)
- Kyo (`io.getkyo:kyo-prelude` + `kyo-core` minimum): cross JVM/JS/Native
- scribe (`com.outr::scribe` 3.16.1): cross JVM/JS/Native
- ZIO Test 2.1.24 (existing test framework)
- parsley 4.6.2 (existing parser combinator lib — unaffected by this PR)

---

## Pre-Flight

Before starting, confirm:

- `git status` is clean.
- `./mill --no-server krueger.core.jvm.test.testForked` passes on a fresh checkout (sanity baseline).
- `./mill --no-server krueger.trees.jvm.test.testForked` passes.
- `./mill --no-server krueger.compiler-api.jvm.test.testForked` passes.
- `bd ready` works and shows `K-1.1` (`musing-chaum-7e7242-070`) as the unblocked entry point.

If any of those fail, stop and surface the failure to the user — do not start implementation against a red baseline.

## File Structure

```
mill-build/src/build/
  Modules.scala                                              MODIFY: add Kyo+scribe versions, Sonatype snapshots resolver

krueger/core/
  package.mill                                               MODIFY: add Kyo+scribe deps to mvnDeps
  src/io/eleven19/krueger/
    log/
      ScribeLogHandler.scala                                 NEW
      ScribeLogLayer.scala                                   NEW
      InMemoryLogRecorder.scala                              NEW
    cst/
      KyoCstVisitor.scala                                    NEW
    ast/
      KyoAstVisitor.scala                                    NEW
  test/src/io/eleven19/krueger/
    log/
      ScribeLogHandlerSpec.scala                             NEW
    cst/
      KyoCstVisitorSpec.scala                                NEW
    ast/
      KyoAstVisitorSpec.scala                                NEW

krueger/trees/
  package.mill                                               MODIFY: add Kyo deps to mvnDeps
  src/io/eleven19/krueger/trees/
    KyoQueryableTree.scala                                   NEW
    query/
      KyoQueryVisitor.scala                                  NEW
  test/src/io/eleven19/krueger/trees/
    KyoQueryableTreeSpec.scala                               NEW
    query/
      KyoQueryVisitorSpec.scala                              NEW

krueger/compiler-api/
  package.mill                                               MODIFY: add Kyo deps to mvnDeps
  src/io/eleven19/krueger/compiler/
    Stage.scala                                              NEW
  test/src/io/eleven19/krueger/compiler/
    StageSpec.scala                                          NEW

docs/conventions/
  kyo-services.md                                            NEW (worked-example doc)
```

Total: 4 modify, 17 new files (10 source/test pairs + 1 doc + 6 specs).

---

### Task 1: Wire Kyo + scribe deps and Sonatype snapshots resolver

**Files:**
- Modify: `mill-build/src/build/Modules.scala:1-60`
- Modify: `krueger/core/package.mill:8-12` (mvnDeps)
- Modify: `krueger/trees/package.mill:8-13` (mvnDeps)
- Modify: `krueger/compiler-api/package.mill:11-14` (mvnDeps)

- [ ] **Step 1.1: Add version constants and resolver to `Modules.scala`**

Open `mill-build/src/build/Modules.scala`. After the `import` lines and before `trait CommonScalaModule`, add the version constants. Then override `repositoriesTask` on `CommonScalaModule` to include the Sonatype snapshots URL.

Replace the existing `package build / import / trait CommonScalaModule` block top with:

```scala
package build

import mill.*
import mill.scalalib.*
import mill.scalajslib.*
import mill.scalanativelib.*
import coursier.maven.MavenRepository

object KruegerVersions:
    /** Minimum required Kyo version. Later snapshot or stable releases are acceptable.
      * Lower bound exists because this build introduced kyo-schema on the Kyo mainline.
      */
    val Kyo: String = "1.0-RC1+214-534321a9-SNAPSHOT"

    /** Pinned scribe version for cross-platform logging (JVM + JS + Native). */
    val Scribe: String = "3.16.1"

trait CommonScalaModule extends ScalaModule with scalafmt.ScalafmtModule {
  override def scalaVersion = Task {
    "3.8.3"
  }

  override def scalacOptions = Task {
    Seq(
      "-Wvalue-discard",
      "-Wnonunit-statement",
      "-Wconf:msg=(unused.*value|discarded.*value|pure.*statement):error",
      "-language:strictEquality",
      "-deprecation",
      "-feature",
      "-Werror"
    )
  }

  override def repositoriesTask: Task[Seq[coursier.Repository]] = Task.Anon {
    super.repositoriesTask() ++ Seq(
      MavenRepository("https://oss.sonatype.org/content/repositories/snapshots/")
    )
  }
}
```

- [ ] **Step 1.2: Run a Mill resolve to confirm the resolver finds the snapshot artifact**

Run:

```bash
./mill --no-server resolve krueger.core.jvm.compileClasspath
```

Expected: command succeeds (no error). The output lists the existing classpath. We have not yet added Kyo to `mvnDeps`, so Kyo will not appear yet — but the Sonatype resolver must be reachable.

- [ ] **Step 1.3: Add Kyo + scribe to `krueger/core/package.mill`**

Open `krueger/core/package.mill`. Find `def mvnDeps = Seq(...)` inside `trait CoreModule`. Replace it with:

```scala
    def mvnDeps = Seq(
        mvn"com.github.j-mie6::parsley::4.6.2",
        mvn"io.getkyo::kyo-prelude::${_root_.build.KruegerVersions.Kyo}",
        mvn"io.getkyo::kyo-core::${_root_.build.KruegerVersions.Kyo}",
        mvn"com.outr::scribe::${_root_.build.KruegerVersions.Scribe}"
    )
```

- [ ] **Step 1.4: Add Kyo to `krueger/trees/package.mill`**

Open `krueger/trees/package.mill`. Find `def mvnDeps = Seq(...)` inside `trait TreesModule`. Replace it with:

```scala
    def mvnDeps = Seq(
        mvn"com.github.j-mie6::parsley::4.6.2",
        mvn"io.github.kitlangton::neotype::0.4.10",
        mvn"com.kubuszok::kindlings-fast-show-pretty::0.1.0",
        mvn"com.github.ghostdogpr::purelogic::0.1.0",
        mvn"io.getkyo::kyo-prelude::${_root_.build.KruegerVersions.Kyo}",
        mvn"io.getkyo::kyo-core::${_root_.build.KruegerVersions.Kyo}"
    )
```

- [ ] **Step 1.5: Add Kyo to `krueger/compiler-api/package.mill`**

Open `krueger/compiler-api/package.mill`. Find `override def mvnDeps = Task { super.mvnDeps() ++ Seq(...) }` inside `trait CompilerApiModule`. Replace it with:

```scala
    override def mvnDeps = Task {
        super.mvnDeps() ++ Seq(
            mvn"com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-core::2.38.9",
            mvn"com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-macros::2.38.9",
            mvn"io.getkyo::kyo-prelude::${_root_.build.KruegerVersions.Kyo}",
            mvn"io.getkyo::kyo-core::${_root_.build.KruegerVersions.Kyo}"
        )
    }
```

- [ ] **Step 1.6: Compile the JVM targets to confirm Kyo resolves**

Run:

```bash
./mill --no-server krueger.core.jvm.compile krueger.trees.jvm.compile krueger.compiler-api.jvm.compile
```

Expected: BUILD SUCCESS. Kyo + scribe are now on the classpath of all three modules.

- [ ] **Step 1.7: Compile the JS + Native targets (best effort)**

Run, in this order, capturing output:

```bash
./mill --no-server krueger.trees.js.compile krueger.core.js.compile krueger.compiler-api.js.compile
./mill --no-server krueger.trees.native.compile krueger.core.native.compile krueger.compiler-api.native.compile
```

Expected: SUCCESS on all six. If a Native compile fails because the Kyo SNAPSHOT does not publish a Native artifact for some module, stop and surface the failure to the user. Do NOT silently drop Native — degrading platform support is a deliberate decision the user must approve. The likely follow-up is to gate `kyo-core` to JVM/JS only on Native variants by overriding `mvnDeps` in the `native` object inside the affected `package.mill`. Document the decision in a follow-up commit and continue.

- [ ] **Step 1.8: Run the full test suite to confirm no regression**

Run:

```bash
./mill --no-server krueger.trees.jvm.test.testForked krueger.core.jvm.test.testForked krueger.compiler-api.jvm.test.testForked
```

Expected: all pre-existing tests still pass.

- [ ] **Step 1.9: Commit**

```bash
git add mill-build/src/build/Modules.scala krueger/core/package.mill krueger/trees/package.mill krueger/compiler-api/package.mill
git commit -m "build: add Kyo + scribe deps with Sonatype snapshots resolver

Adds shared KruegerVersions object holding minimum Kyo version
(>= 1.0-RC1+214-534321a9-SNAPSHOT, the kyo-schema floor) and the
pinned scribe version (3.16.1). Wires kyo-prelude + kyo-core into
core / trees / compiler-api and scribe into core only (logging
backend lives in core.log per service-pattern conventions).

Tracks GitHub #24 (EPIC-1, K-1.1) and bd musing-chaum-7e7242-070."
```

---

### Task 2: Add `Stage[I, O, S]` abstraction to `compiler-api`

**Files:**
- Create: `krueger/compiler-api/src/io/eleven19/krueger/compiler/Stage.scala`
- Test: `krueger/compiler-api/test/src/io/eleven19/krueger/compiler/StageSpec.scala`

- [ ] **Step 2.1: Write the failing test**

Create `krueger/compiler-api/test/src/io/eleven19/krueger/compiler/StageSpec.scala`:

```scala
package io.eleven19.krueger.compiler

import kyo.*
import zio.test.*
import zio.test.Assertion.*

object StageSpec extends ZIOSpecDefault:

    private val identityStage: Stage[Int, Int, Any] =
        Stage.identity[Int]

    private val pureStage: Stage[Int, String, Any] =
        Stage.pure((i: Int) => s"value=$i")

    def spec = suite("Stage")(

        test("identity returns the input unchanged"):
            val program: Int < Any = identityStage.run(42)
            val out = IO.Unsafe.run(program)(using AllowUnsafe.embrace.danger).eval
            assertTrue(out == 42)
        ,

        test("pure applies a pure function"):
            val program: String < Any = pureStage.run(7)
            val out = IO.Unsafe.run(program)(using AllowUnsafe.embrace.danger).eval
            assertTrue(out == "value=7")
        ,

        test(">>> composes two stages preserving effect rows"):
            val composed: Stage[Int, String, Any] = identityStage >>> pureStage
            val program = composed.run(13)
            val out = IO.Unsafe.run(program)(using AllowUnsafe.embrace.danger).eval
            assertTrue(out == "value=13")
        ,

        test("composition order is left-to-right"):
            val plusOne: Stage[Int, Int, Any] = Stage.pure((i: Int) => i + 1)
            val toStr:   Stage[Int, String, Any] = Stage.pure((i: Int) => i.toString)
            val pipeline: Stage[Int, String, Any] = plusOne >>> toStr
            val program = pipeline.run(4)
            val out = IO.Unsafe.run(program)(using AllowUnsafe.embrace.danger).eval
            assertTrue(out == "5")
    )
```

- [ ] **Step 2.2: Run the test to verify it fails**

Run:

```bash
./mill --no-server krueger.compiler-api.jvm.test.testForked io.eleven19.krueger.compiler.StageSpec
```

Expected: FAIL with `not found: type Stage` or `not found: value Stage`.

- [ ] **Step 2.3: Write the minimal `Stage` implementation**

Create `krueger/compiler-api/src/io/eleven19/krueger/compiler/Stage.scala`:

```scala
package io.eleven19.krueger.compiler

import kyo.*

/** A composable pipeline stage from `I` to `O` tracking the effect row `S`.
  *
  * `Stage` is the building block for compiler pipelines (lex → tokenize → parse → lower → ...).
  * Composition with `>>>` preserves effect-row tracking at the type level: composing a stage
  * that needs `IO` with one that needs `Abort[E]` produces a stage requiring both.
  */
trait Stage[-I, +O, S]:
    def run(input: I): O < S

    final def >>>[O2, S2](next: Stage[O, O2, S2]): Stage[I, O2, S & S2] =
        new Stage[I, O2, S & S2]:
            def run(input: I): O2 < (S & S2) =
                Stage.this.run(input).map(next.run)

object Stage:

    /** A stage that returns its input unchanged with no effects. */
    def identity[A]: Stage[A, A, Any] =
        new Stage[A, A, Any]:
            def run(input: A): A < Any = input

    /** Lift a pure function into a stage with no effects. */
    def pure[A, B](f: A => B): Stage[A, B, Any] =
        new Stage[A, B, Any]:
            def run(input: A): B < Any = f(input)

    /** Lift an effect-tracked function into a stage. */
    def fromKyo[A, B, S](f: A => B < S): Stage[A, B, S] =
        new Stage[A, B, S]:
            def run(input: A): B < S = f(input)
```

- [ ] **Step 2.4: Run the test to verify it passes**

Run:

```bash
./mill --no-server krueger.compiler-api.jvm.test.testForked io.eleven19.krueger.compiler.StageSpec
```

Expected: PASS, all 4 cases green.

- [ ] **Step 2.5: Compile JS + Native variants**

Run:

```bash
./mill --no-server krueger.compiler-api.js.compile krueger.compiler-api.native.compile
```

Expected: BUILD SUCCESS on both. If Native fails on Kyo dep availability, follow the Step 1.7 escalation rule.

- [ ] **Step 2.6: Commit**

```bash
git add krueger/compiler-api/src/io/eleven19/krueger/compiler/Stage.scala krueger/compiler-api/test/src/io/eleven19/krueger/compiler/StageSpec.scala
git commit -m "feat(compiler-api): add Stage[I, O, S] composable pipeline abstraction

Stage wraps I => O < S with a >>> operator that preserves effect-row
tracking at the type level. Identity, pure, and fromKyo constructors
cover the common cases. Future EPIC-6 parser-pipeline work composes
stages instead of hand-wired functions.

Tracks GitHub #24 (EPIC-1, K-1.4)."
```

---

### Task 3: Add `ScribeLogHandler` + recorder + production layer

**Files:**
- Create: `krueger/core/src/io/eleven19/krueger/log/ScribeLogHandler.scala`
- Create: `krueger/core/src/io/eleven19/krueger/log/ScribeLogLayer.scala`
- Create: `krueger/core/src/io/eleven19/krueger/log/InMemoryLogRecorder.scala`
- Test: `krueger/core/test/src/io/eleven19/krueger/log/ScribeLogHandlerSpec.scala`

- [ ] **Step 3.1: Write the failing test**

Create `krueger/core/test/src/io/eleven19/krueger/log/ScribeLogHandlerSpec.scala`:

```scala
package io.eleven19.krueger.log

import kyo.*
import zio.test.*
import zio.test.Assertion.*

object ScribeLogHandlerSpec extends ZIOSpecDefault:

    def spec = suite("ScribeLogHandler / InMemoryLogRecorder")(

        test("InMemoryLogRecorder captures every level emitted via Kyo Log"):
            val recorder = InMemoryLogRecorder.unsafeMake()
            val program: Unit < Any =
                Env.runLayer(InMemoryLogRecorder.layer(recorder)) {
                    for
                        _ <- Log.trace("trace-msg")
                        _ <- Log.debug("debug-msg")
                        _ <- Log.info("info-msg")
                        _ <- Log.warn("warn-msg")
                        _ <- Log.error("error-msg")
                    yield ()
                }
            val _ = IO.Unsafe.run(program)(using AllowUnsafe.embrace.danger).eval
            val events = recorder.snapshot()
            assertTrue(events.map(_.message) ==
                List("trace-msg", "debug-msg", "info-msg", "warn-msg", "error-msg"))
        ,

        test("InMemoryLogRecorder preserves emission order"):
            val recorder = InMemoryLogRecorder.unsafeMake()
            val program =
                Env.runLayer(InMemoryLogRecorder.layer(recorder)) {
                    for
                        _ <- Log.info("first")
                        _ <- Log.info("second")
                        _ <- Log.info("third")
                    yield ()
                }
            val _ = IO.Unsafe.run(program)(using AllowUnsafe.embrace.danger).eval
            assertTrue(recorder.snapshot().map(_.message) == List("first", "second", "third"))
        ,

        test("ScribeLogHandler does not throw on every level"):
            val program =
                Env.runLayer(ScribeLogLayer.default) {
                    for
                        _ <- Log.trace("trace-msg")
                        _ <- Log.debug("debug-msg")
                        _ <- Log.info("info-msg")
                        _ <- Log.warn("warn-msg")
                        _ <- Log.error("error-msg")
                    yield ()
                }
            // We are not asserting on scribe output (sinks are configured
            // outside this test). We only assert the bridge runs end-to-end
            // without throwing.
            val _ = IO.Unsafe.run(program)(using AllowUnsafe.embrace.danger).eval
            assertCompletes
    )
```

- [ ] **Step 3.2: Run the test to verify it fails**

Run:

```bash
./mill --no-server krueger.core.jvm.test.testForked io.eleven19.krueger.log.ScribeLogHandlerSpec
```

Expected: FAIL with `not found: object InMemoryLogRecorder` (and similar for `ScribeLogLayer`).

- [ ] **Step 3.3: Implement `InMemoryLogRecorder`**

Create `krueger/core/src/io/eleven19/krueger/log/InMemoryLogRecorder.scala`:

```scala
package io.eleven19.krueger.log

import kyo.*
import scala.collection.mutable

/** Captured log emission for tests. */
final case class LogRecord(
    level: Log.Level,
    message: String,
    cause: Option[Throwable] = None
) derives CanEqual

/** In-memory recorder for `Log` emissions. Used in tests to assert on log
  * level + message + cause.
  *
  * Construct via [[InMemoryLogRecorder.unsafeMake]] outside of effects;
  * provide via [[InMemoryLogRecorder.layer]] to inject as a `Log` handler.
  */
final class InMemoryLogRecorder private (private val buffer: mutable.ArrayBuffer[LogRecord]):

    /** Snapshot the events captured so far. Subsequent emissions do not
      * mutate the returned sequence.
      */
    def snapshot(): Seq[LogRecord] = synchronized { buffer.toList }

    /** Clear the recorder. Tests typically construct a fresh recorder per
      * scenario, but this is provided for repeated assertions in suites
      * that share state.
      */
    def clear(): Unit = synchronized { buffer.clear() }

    private[log] def append(record: LogRecord): Unit = synchronized {
        buffer += record
    }

object InMemoryLogRecorder:

    /** Construct a fresh recorder. Side-effecting because the buffer is
      * mutable; intended for use outside of Kyo computations.
      */
    def unsafeMake(): InMemoryLogRecorder =
        new InMemoryLogRecorder(mutable.ArrayBuffer.empty)

    /** Build a `Log` handler layer that funnels every emission to the
      * supplied recorder.
      */
    def layer(recorder: InMemoryLogRecorder): Layer[Log, Any] =
        Layer {
            new Log:
                def trace(msg: => String): Unit < IO =
                    IO(recorder.append(LogRecord(Log.Level.Trace, msg)))
                def debug(msg: => String): Unit < IO =
                    IO(recorder.append(LogRecord(Log.Level.Debug, msg)))
                def info(msg: => String): Unit < IO =
                    IO(recorder.append(LogRecord(Log.Level.Info, msg)))
                def warn(msg: => String): Unit < IO =
                    IO(recorder.append(LogRecord(Log.Level.Warn, msg)))
                def error(msg: => String, cause: => Throwable): Unit < IO =
                    IO(recorder.append(LogRecord(Log.Level.Error, msg, Some(cause))))
                def error(msg: => String): Unit < IO =
                    IO(recorder.append(LogRecord(Log.Level.Error, msg)))
        }
```

- [ ] **Step 3.4: Implement `ScribeLogHandler` and `ScribeLogLayer`**

Create `krueger/core/src/io/eleven19/krueger/log/ScribeLogHandler.scala`:

```scala
package io.eleven19.krueger.log

import kyo.*
import scribe.{Logger as ScribeLogger, Level as ScribeLevel}

/** Kyo `Log` handler that delegates to scribe.
  *
  * Each Kyo `Log` level maps to its scribe equivalent. Sinks (stdout,
  * file, JSON) are configured at the scribe layer outside this handler —
  * callers who want a non-default scribe configuration replace the root
  * scribe logger before constructing this handler.
  */
final class ScribeLogHandler(logger: ScribeLogger) extends Log:

    def trace(msg: => String): Unit < IO =
        IO(logger.trace(msg))

    def debug(msg: => String): Unit < IO =
        IO(logger.debug(msg))

    def info(msg: => String): Unit < IO =
        IO(logger.info(msg))

    def warn(msg: => String): Unit < IO =
        IO(logger.warn(msg))

    def error(msg: => String, cause: => Throwable): Unit < IO =
        IO(logger.error(msg, cause))

    def error(msg: => String): Unit < IO =
        IO(logger.error(msg))

object ScribeLogHandler:

    private[log] def levelToScribe(level: Log.Level): ScribeLevel =
        level match
            case Log.Level.Trace => ScribeLevel.Trace
            case Log.Level.Debug => ScribeLevel.Debug
            case Log.Level.Info  => ScribeLevel.Info
            case Log.Level.Warn  => ScribeLevel.Warn
            case Log.Level.Error => ScribeLevel.Error
```

Create `krueger/core/src/io/eleven19/krueger/log/ScribeLogLayer.scala`:

```scala
package io.eleven19.krueger.log

import kyo.*
import scribe.{Logger as ScribeLogger}

/** Kyo `Layer` factories for the scribe-backed `Log` handler.
  *
  * - [[default]] — wraps scribe's root logger; suitable for production use.
  * - [[forLogger]] — supply a pre-configured scribe logger (e.g., named or
  *   custom-sink) to override the default sink set.
  */
object ScribeLogLayer:

    val default: Layer[Log, Any] =
        Layer { new ScribeLogHandler(ScribeLogger.root) }

    def forLogger(logger: ScribeLogger): Layer[Log, Any] =
        Layer { new ScribeLogHandler(logger) }
```

- [ ] **Step 3.5: Run the tests to verify they pass**

Run:

```bash
./mill --no-server krueger.core.jvm.test.testForked io.eleven19.krueger.log.ScribeLogHandlerSpec
```

Expected: PASS on all 3 cases. If Kyo's `Log` API differs (e.g., `Log.Level` is in a different package or method shapes have changed), adapt the implementation to match what the resolved snapshot exposes — the test cases stay shape-stable.

- [ ] **Step 3.6: Compile JS + Native variants**

Run:

```bash
./mill --no-server krueger.core.js.compile krueger.core.native.compile
```

Expected: BUILD SUCCESS on both. Scribe publishes for JS + Native; Kyo `Log` is in `kyo-prelude` (cross-platform).

- [ ] **Step 3.7: Commit**

```bash
git add krueger/core/src/io/eleven19/krueger/log/ krueger/core/test/src/io/eleven19/krueger/log/
git commit -m "feat(core): add ScribeLogHandler + InMemoryLogRecorder for Kyo Log

Bridges Kyo's Log effect to scribe in production (ScribeLogLayer) and
to an in-memory recorder for tests (InMemoryLogRecorder.layer). Library
code consumes Env[Log] only — scribe coupling lives behind the layer
boundary. Sinks (stdout / file / JSON) are configured at the scribe
layer outside this handler.

Tracks GitHub #24 (EPIC-1, K-1.2 logging portion)."
```

---

### Task 4: Add `KyoQueryableTree` extension

**Files:**
- Create: `krueger/trees/src/io/eleven19/krueger/trees/KyoQueryableTree.scala`
- Test: `krueger/trees/test/src/io/eleven19/krueger/trees/KyoQueryableTreeSpec.scala`

Read `krueger/trees/src/io/eleven19/krueger/trees/QueryableTree.scala` first to confirm the existing pure API surface (`children`, `nodeType`, `text`, `fields`). The Kyo extension mirrors those operations as effect-tracked helpers.

- [ ] **Step 4.1: Write the failing test**

Create `krueger/trees/test/src/io/eleven19/krueger/trees/KyoQueryableTreeSpec.scala`:

```scala
package io.eleven19.krueger.trees

import kyo.*
import zio.test.*
import zio.test.Assertion.*

object KyoQueryableTreeSpec extends ZIOSpecDefault:

    /** Trivial tree fixture: a node tagged by an Int identity with a children list. */
    final case class TestNode(id: Int, kids: List[TestNode]) derives CanEqual

    given QueryableTree[TestNode] with
        def nodeType(t: TestNode): NodeTypeName = NodeTypeName(s"node-${t.id}")
        def children(t: TestNode): IndexedSeq[TestNode] = t.kids.toIndexedSeq
        def text(t: TestNode): Option[String] = None
        def fields(t: TestNode): Map[FieldName, Seq[TestNode]] = Map.empty

    private val tree: TestNode =
        TestNode(1, List(TestNode(2, Nil), TestNode(3, List(TestNode(4, Nil)))))

    def spec = suite("KyoQueryableTree")(

        test("traverseKyo visits every node in pre-order"):
            val program: Vector[Int] < IO =
                IO {
                    val collected = scala.collection.mutable.ArrayBuffer.empty[Int]
                    KyoQueryableTree.traverseKyo(tree) { n =>
                        IO { collected += n.id; () }
                    }.map(_ => collected.toVector)
                }.flatten
            val out = IO.Unsafe.run(program)(using AllowUnsafe.embrace.danger).eval
            assertTrue(out == Vector(1, 2, 3, 4))
        ,

        test("foldKyo accumulates left-to-right pre-order"):
            val program: Int < IO =
                KyoQueryableTree.foldKyo(tree, 0) { (acc, n) =>
                    IO(acc + n.id)
                }
            val out = IO.Unsafe.run(program)(using AllowUnsafe.embrace.danger).eval
            assertTrue(out == 1 + 2 + 3 + 4)
        ,

        test("traverseKyo on a single-node tree visits exactly once"):
            val leaf = TestNode(99, Nil)
            val program: Int < IO =
                IO {
                    var count = 0
                    KyoQueryableTree.traverseKyo(leaf) { _ =>
                        IO { count += 1; () }
                    }.map(_ => count)
                }.flatten
            val out = IO.Unsafe.run(program)(using AllowUnsafe.embrace.danger).eval
            assertTrue(out == 1)
        ,

        test("Abort.fail in callback short-circuits traversal"):
            val program: Int < (Abort[String] & IO) =
                KyoQueryableTree.foldKyo(tree, 0) { (acc, n) =>
                    if n.id == 3 then Abort.fail[String]("stop at 3")
                    else IO(acc + n.id)
                }
            val out =
                Abort.run[String](program).map(_.fold(_ => -1, identity))
            val v = IO.Unsafe.run(out)(using AllowUnsafe.embrace.danger).eval
            assertTrue(v == -1)
    )
```

- [ ] **Step 4.2: Run the test to verify it fails**

Run:

```bash
./mill --no-server krueger.trees.jvm.test.testForked io.eleven19.krueger.trees.KyoQueryableTreeSpec
```

Expected: FAIL with `not found: object KyoQueryableTree`.

- [ ] **Step 4.3: Implement `KyoQueryableTree`**

Create `krueger/trees/src/io/eleven19/krueger/trees/KyoQueryableTree.scala`:

```scala
package io.eleven19.krueger.trees

import kyo.*

/** Kyo-aware traversal helpers over `QueryableTree[T]`.
  *
  * Mirrors the pure `QueryableTree` traversal model with effect-tracked
  * callbacks. Pure callers can keep using `QueryableTree[T]` directly;
  * effect-tracked callers reach for these helpers and get effect-row
  * tracking through traversal.
  *
  * Traversal order is pre-order, matching `UnistProjection.project` for
  * deterministic output.
  */
object KyoQueryableTree:

    /** Visit every node in pre-order, applying the effectful `f` to each.
      *
      * The effect row `S` flows through every visit; `Abort.fail` in `f`
      * short-circuits the remaining traversal.
      */
    def traverseKyo[T, S](tree: T)(f: T => Unit < S)(using Q: QueryableTree[T]): Unit < S =
        f(tree).map { _ =>
            visitChildrenKyo(Q.children(tree).toList)(child => traverseKyo(child)(f))
        }

    /** Pre-order fold with an effectful step.
      *
      * Identity element `zero` is threaded through the traversal; each
      * visit returns the new accumulator. `Abort.fail` short-circuits.
      */
    def foldKyo[T, A, S](tree: T, zero: A)(f: (A, T) => A < S)(using Q: QueryableTree[T]): A < S =
        f(zero, tree).map { acc1 =>
            foldChildrenKyo(Q.children(tree).toList, acc1)(f)
        }

    private def visitChildrenKyo[T, S](children: List[T])(f: T => Unit < S): Unit < S =
        children match
            case Nil       => ()
            case h :: rest => f(h).map(_ => visitChildrenKyo(rest)(f))

    private def foldChildrenKyo[T, A, S](
        children: List[T],
        acc: A
    )(f: (A, T) => A < S)(using Q: QueryableTree[T]): A < S =
        children match
            case Nil       => acc
            case h :: rest => foldKyo(h, acc)(f).map(a => foldChildrenKyo(rest, a)(f))
```

- [ ] **Step 4.4: Run the test to verify it passes**

Run:

```bash
./mill --no-server krueger.trees.jvm.test.testForked io.eleven19.krueger.trees.KyoQueryableTreeSpec
```

Expected: PASS on all 4 cases.

- [ ] **Step 4.5: Compile JS + Native variants**

Run:

```bash
./mill --no-server krueger.trees.js.compile krueger.trees.native.compile
```

Expected: BUILD SUCCESS on both.

- [ ] **Step 4.6: Commit**

```bash
git add krueger/trees/src/io/eleven19/krueger/trees/KyoQueryableTree.scala krueger/trees/test/src/io/eleven19/krueger/trees/KyoQueryableTreeSpec.scala
git commit -m "feat(trees): add KyoQueryableTree effect-tracked traversal helpers

traverseKyo and foldKyo provide Kyo equivalents of pure QueryableTree
walks. Traversal order is pre-order, matching UnistProjection. Abort.fail
inside a callback short-circuits remaining traversal cleanly.

Tracks GitHub #24 (EPIC-1, K-1.3)."
```

---

### Task 5: Add `KyoCstVisitor`

**Files:**
- Create: `krueger/core/src/io/eleven19/krueger/cst/KyoCstVisitor.scala`
- Test: `krueger/core/test/src/io/eleven19/krueger/cst/KyoCstVisitorSpec.scala`

Read `krueger/core/src/io/eleven19/krueger/cst/CstVisitor.scala` first to mirror its surface (visit / cursor methods).

- [ ] **Step 5.1: Write the failing test**

Create `krueger/core/test/src/io/eleven19/krueger/cst/KyoCstVisitorSpec.scala`:

```scala
package io.eleven19.krueger.cst

import io.eleven19.krueger.parser.ModuleParser
import kyo.*
import zio.test.*
import zio.test.Assertion.*

object KyoCstVisitorSpec extends ZIOSpecDefault:

    private val sampleSource: String =
        """module Main exposing (..)
          |
          |x = 1
          |""".stripMargin

    private def parsedCst: CstNode =
        ModuleParser.parseCst(sampleSource).getOrElse(
            sys.error("baseline parse failure — fix before running KyoCstVisitor tests")
        )

    def spec = suite("KyoCstVisitor")(

        test("visit invokes callback for every CST node in pre-order"):
            val program: Int < IO =
                IO {
                    var count = 0
                    KyoCstVisitor.visit(parsedCst) { _ =>
                        IO { count += 1; () }
                    }.map(_ => count)
                }.flatten
            val out = IO.Unsafe.run(program)(using AllowUnsafe.embrace.danger).eval
            assertTrue(out > 0)
        ,

        test("visit order matches the pure CstVisitor traversal order"):
            val pureOrder: Vector[String] =
                val buf = scala.collection.mutable.ArrayBuffer.empty[String]
                CstVisitor.visit(parsedCst)(n => { buf += n.nodeType.toString; () })
                buf.toVector
            val kyoOrder: Vector[String] = {
                val buf = scala.collection.mutable.ArrayBuffer.empty[String]
                val program = KyoCstVisitor.visit(parsedCst) { n =>
                    IO { buf += n.nodeType.toString; () }
                }.map(_ => buf.toVector)
                IO.Unsafe.run(program)(using AllowUnsafe.embrace.danger).eval
            }
            assertTrue(kyoOrder == pureOrder)
        ,

        test("Abort.fail in callback short-circuits visitation"):
            val program =
                Abort.run[String] {
                    KyoCstVisitor.visit(parsedCst) { n =>
                        Abort.fail[String](s"stopped at ${n.nodeType}")
                    }
                }
            val out = IO.Unsafe.run(program)(using AllowUnsafe.embrace.danger).eval
            assertTrue(out.isLeft)
    )
```

- [ ] **Step 5.2: Run the test to verify it fails**

Run:

```bash
./mill --no-server krueger.core.jvm.test.testForked io.eleven19.krueger.cst.KyoCstVisitorSpec
```

Expected: FAIL with `not found: object KyoCstVisitor`.

- [ ] **Step 5.3: Implement `KyoCstVisitor`**

Create `krueger/core/src/io/eleven19/krueger/cst/KyoCstVisitor.scala`:

```scala
package io.eleven19.krueger.cst

import io.eleven19.krueger.cst.CstQueryableTree.given
import io.eleven19.krueger.trees.KyoQueryableTree
import kyo.*

/** Kyo-aware sibling of [[CstVisitor]]. Accepts callbacks of shape
  * `CstNode => Unit < S`, propagating effect row `S` through traversal.
  *
  * Pure callers should keep using [[CstVisitor]] — its cursor API is
  * unchanged. This variant exists for code that needs typed effects
  * (`IO`, `Abort[E]`, `Env[T]`, ...) inside a visitor callback.
  *
  * Traversal order matches [[CstVisitor]] for identical inputs (pre-order).
  */
object KyoCstVisitor:

    /** Visit every node in pre-order, invoking `f` per node. */
    def visit[S](root: CstNode)(f: CstNode => Unit < S): Unit < S =
        KyoQueryableTree.traverseKyo(root)(f)

    /** Pre-order fold with an effect-tracked step. */
    def fold[A, S](root: CstNode, zero: A)(f: (A, CstNode) => A < S): A < S =
        KyoQueryableTree.foldKyo(root, zero)(f)
```

- [ ] **Step 5.4: Run the test to verify it passes**

Run:

```bash
./mill --no-server krueger.core.jvm.test.testForked io.eleven19.krueger.cst.KyoCstVisitorSpec
```

Expected: PASS on all 3 cases.

- [ ] **Step 5.5: Compile JS + Native variants**

Run:

```bash
./mill --no-server krueger.core.js.compile krueger.core.native.compile
```

Expected: BUILD SUCCESS on both.

- [ ] **Step 5.6: Commit**

```bash
git add krueger/core/src/io/eleven19/krueger/cst/KyoCstVisitor.scala krueger/core/test/src/io/eleven19/krueger/cst/KyoCstVisitorSpec.scala
git commit -m "feat(core): add KyoCstVisitor effect-tracked CST traversal

KyoCstVisitor.visit / fold delegate to KyoQueryableTree, preserving
the pre-order traversal of the pure CstVisitor. Pure CstVisitor with
its cursor API is unchanged.

Tracks GitHub #24 (EPIC-1, K-1.3)."
```

---

### Task 6: Add `KyoAstVisitor`

**Files:**
- Create: `krueger/core/src/io/eleven19/krueger/ast/KyoAstVisitor.scala`
- Test: `krueger/core/test/src/io/eleven19/krueger/ast/KyoAstVisitorSpec.scala`

Read `krueger/core/src/io/eleven19/krueger/ast/AstVisitor.scala` first to mirror its surface.

- [ ] **Step 6.1: Write the failing test**

Create `krueger/core/test/src/io/eleven19/krueger/ast/KyoAstVisitorSpec.scala`:

```scala
package io.eleven19.krueger.ast

import io.eleven19.krueger.parser.ModuleParser
import kyo.*
import zio.test.*
import zio.test.Assertion.*

object KyoAstVisitorSpec extends ZIOSpecDefault:

    private val sampleSource: String =
        """module Main exposing (..)
          |
          |x = 1
          |""".stripMargin

    private def parsedAst: AstNode =
        ModuleParser.parseAst(sampleSource).getOrElse(
            sys.error("baseline parse failure — fix before running KyoAstVisitor tests")
        )

    def spec = suite("KyoAstVisitor")(

        test("visit invokes callback for every AST node in pre-order"):
            val program: Int < IO =
                IO {
                    var count = 0
                    KyoAstVisitor.visit(parsedAst) { _ =>
                        IO { count += 1; () }
                    }.map(_ => count)
                }.flatten
            val out = IO.Unsafe.run(program)(using AllowUnsafe.embrace.danger).eval
            assertTrue(out > 0)
        ,

        test("visit order matches the pure AstVisitor traversal order"):
            val pureOrder: Vector[String] =
                val buf = scala.collection.mutable.ArrayBuffer.empty[String]
                AstVisitor.visit(parsedAst)(n => { buf += n.nodeType.toString; () })
                buf.toVector
            val kyoOrder: Vector[String] = {
                val buf = scala.collection.mutable.ArrayBuffer.empty[String]
                val program = KyoAstVisitor.visit(parsedAst) { n =>
                    IO { buf += n.nodeType.toString; () }
                }.map(_ => buf.toVector)
                IO.Unsafe.run(program)(using AllowUnsafe.embrace.danger).eval
            }
            assertTrue(kyoOrder == pureOrder)
        ,

        test("Abort.fail in callback short-circuits visitation"):
            val program =
                Abort.run[String] {
                    KyoAstVisitor.visit(parsedAst) { _ =>
                        Abort.fail[String]("stop")
                    }
                }
            val out = IO.Unsafe.run(program)(using AllowUnsafe.embrace.danger).eval
            assertTrue(out.isLeft)
    )
```

- [ ] **Step 6.2: Run the test to verify it fails**

Run:

```bash
./mill --no-server krueger.core.jvm.test.testForked io.eleven19.krueger.ast.KyoAstVisitorSpec
```

Expected: FAIL with `not found: object KyoAstVisitor`.

- [ ] **Step 6.3: Implement `KyoAstVisitor`**

Create `krueger/core/src/io/eleven19/krueger/ast/KyoAstVisitor.scala`:

```scala
package io.eleven19.krueger.ast

import io.eleven19.krueger.ast.AstQueryableTree.given
import io.eleven19.krueger.trees.KyoQueryableTree
import kyo.*

/** Kyo-aware sibling of [[AstVisitor]]. Accepts callbacks of shape
  * `AstNode => Unit < S`, propagating effect row `S` through traversal.
  *
  * Pure callers should keep using [[AstVisitor]]. Traversal order
  * matches the pure visitor for identical inputs (pre-order).
  */
object KyoAstVisitor:

    def visit[S](root: AstNode)(f: AstNode => Unit < S): Unit < S =
        KyoQueryableTree.traverseKyo(root)(f)

    def fold[A, S](root: AstNode, zero: A)(f: (A, AstNode) => A < S): A < S =
        KyoQueryableTree.foldKyo(root, zero)(f)
```

- [ ] **Step 6.4: Run the test to verify it passes**

Run:

```bash
./mill --no-server krueger.core.jvm.test.testForked io.eleven19.krueger.ast.KyoAstVisitorSpec
```

Expected: PASS on all 3 cases.

- [ ] **Step 6.5: Compile JS + Native variants**

Run:

```bash
./mill --no-server krueger.core.js.compile krueger.core.native.compile
```

Expected: BUILD SUCCESS on both.

- [ ] **Step 6.6: Commit**

```bash
git add krueger/core/src/io/eleven19/krueger/ast/KyoAstVisitor.scala krueger/core/test/src/io/eleven19/krueger/ast/KyoAstVisitorSpec.scala
git commit -m "feat(core): add KyoAstVisitor effect-tracked AST traversal

KyoAstVisitor.visit / fold delegate to KyoQueryableTree, preserving
the pre-order traversal of the pure AstVisitor.

Tracks GitHub #24 (EPIC-1, K-1.3)."
```

---

### Task 7: Add `KyoQueryVisitor`

**Files:**
- Create: `krueger/trees/src/io/eleven19/krueger/trees/query/KyoQueryVisitor.scala`
- Test: `krueger/trees/test/src/io/eleven19/krueger/trees/query/KyoQueryVisitorSpec.scala`

Read `krueger/trees/src/io/eleven19/krueger/trees/query/QueryVisitor.scala` first to mirror its surface (visit walks Query AST nodes — patterns + predicates).

- [ ] **Step 7.1: Write the failing test**

Create `krueger/trees/test/src/io/eleven19/krueger/trees/query/KyoQueryVisitorSpec.scala`:

```scala
package io.eleven19.krueger.trees.query

import kyo.*
import zio.test.*
import zio.test.Assertion.*

object KyoQueryVisitorSpec extends ZIOSpecDefault:

    private def parsedQuery: Query =
        QueryParser.parse("(NodeA name: (NodeB) @child) @parent")
            .getOrElse(sys.error("baseline query parse failure"))

    def spec = suite("KyoQueryVisitor")(

        test("visitPatterns invokes callback for every pattern node in pre-order"):
            val program: Int < IO =
                IO {
                    var count = 0
                    KyoQueryVisitor.visitPatterns(parsedQuery.root) { _ =>
                        IO { count += 1; () }
                    }.map(_ => count)
                }.flatten
            val out = IO.Unsafe.run(program)(using AllowUnsafe.embrace.danger).eval
            assertTrue(out > 0)
        ,

        test("Abort.fail in callback short-circuits visitation"):
            val program =
                Abort.run[String] {
                    KyoQueryVisitor.visitPatterns(parsedQuery.root) { _ =>
                        Abort.fail[String]("stop")
                    }
                }
            val out = IO.Unsafe.run(program)(using AllowUnsafe.embrace.danger).eval
            assertTrue(out.isLeft)
    )
```

- [ ] **Step 7.2: Run the test to verify it fails**

Run:

```bash
./mill --no-server krueger.trees.jvm.test.testForked io.eleven19.krueger.trees.query.KyoQueryVisitorSpec
```

Expected: FAIL with `not found: object KyoQueryVisitor`.

- [ ] **Step 7.3: Implement `KyoQueryVisitor`**

Create `krueger/trees/src/io/eleven19/krueger/trees/query/KyoQueryVisitor.scala`:

```scala
package io.eleven19.krueger.trees.query

import kyo.*

/** Kyo-aware sibling of [[QueryVisitor]] over query-AST patterns.
  *
  * Pure callers should keep using [[QueryVisitor]]. Traversal order
  * matches the pure visitor (pre-order over the pattern tree).
  */
object KyoQueryVisitor:

    /** Visit every pattern node (Node / Field / Wildcard / Capture) in pre-order. */
    def visitPatterns[S](root: Pattern)(f: Pattern => Unit < S): Unit < S =
        f(root).map { _ =>
            visitChildren(QueryVisitor.children(root))(f)
        }

    private def visitChildren[S](children: List[Pattern])(f: Pattern => Unit < S): Unit < S =
        children match
            case Nil       => ()
            case h :: rest => visitPatterns(h)(f).map(_ => visitChildren(rest)(f))
```

If the pure `QueryVisitor` exposes a different `children` accessor or pattern-tree shape, mirror exactly what it does. The signature stays Kyo-aware.

- [ ] **Step 7.4: Run the test to verify it passes**

Run:

```bash
./mill --no-server krueger.trees.jvm.test.testForked io.eleven19.krueger.trees.query.KyoQueryVisitorSpec
```

Expected: PASS on both cases.

- [ ] **Step 7.5: Compile JS + Native variants**

Run:

```bash
./mill --no-server krueger.trees.js.compile krueger.trees.native.compile
```

Expected: BUILD SUCCESS on both.

- [ ] **Step 7.6: Commit**

```bash
git add krueger/trees/src/io/eleven19/krueger/trees/query/KyoQueryVisitor.scala krueger/trees/test/src/io/eleven19/krueger/trees/query/KyoQueryVisitorSpec.scala
git commit -m "feat(trees): add KyoQueryVisitor effect-tracked query-AST traversal

KyoQueryVisitor.visitPatterns mirrors the pre-order pattern walk of the
pure QueryVisitor, with Kyo effect-row tracking.

Tracks GitHub #24 (EPIC-1, K-1.3)."
```

---

### Task 8: Land `docs/conventions/kyo-services.md`

**Files:**
- Create: `docs/conventions/kyo-services.md`

- [ ] **Step 8.1: Write the convention doc**

Create `docs/conventions/kyo-services.md`:

````markdown
# Kyo Service Pattern (Convention)

This document defines how Krueger code declares, consumes, and provides
services using Kyo's effect system. Every new service in the project
follows this pattern.

## Three rules

1. **Contract first.** A service is a `trait` whose methods return Kyo
   effect-tracked types (`A < S`). Methods declare exactly what effects
   they need.
2. **Consume via `Env`.** Callers depend on `Env[Service]`, never on a
   concrete impl. The compiler tracks the dependency in the effect row
   so missing layers are caught at composition time.
3. **Provide via `Layer`.** Production code wires layers using `Layer.init`
   (or direct `Layer { ... }`). Tests swap in an in-memory layer.

## Logging is a service

All in-process logging flows through Kyo's `Log` effect. The production
layer (`ScribeLogLayer.default`) wires the scribe-backed handler; tests
wire `InMemoryLogRecorder.layer(recorder)` to capture emissions for
assertion. Service code never imports `scribe` directly.

## Worked example

```scala
import kyo.*
import io.eleven19.krueger.log.*

// Step 1: Contract — a trait with Kyo-typed methods.
trait Counter:
    def increment: Unit < (IO & Env[Log])
    def value: Int < IO

// Step 2: Consume via Env — callers do not see the concrete impl.
def runWork: Int < (IO & Env[Counter] & Env[Log]) =
    for
        c <- Env.get[Counter]
        _ <- c.increment
        _ <- c.increment
        n <- c.value
        l <- Env.get[Log]
        _ <- l.info(s"counter=$n")
    yield n

// Step 3: Provide via Layer — production and test bindings.
val productionCounter: Layer[Counter, Any] =
    Layer {
        new Counter:
            private val ref = java.util.concurrent.atomic.AtomicInteger(0)
            def increment: Unit < (IO & Env[Log]) =
                for
                    log <- Env.get[Log]
                    _   <- IO(ref.incrementAndGet())
                    _   <- log.debug(s"counter incremented to ${ref.get}")
                yield ()
            def value: Int < IO = IO(ref.get)
    }

// Compose layers
val productionAppLayer: Layer[Counter & Log, Any] =
    Layer.init[Counter & Log](productionCounter, ScribeLogLayer.default)

// Run the program
val program: Int < IO =
    Memo.run(Env.runLayer(productionAppLayer)(runWork))
```

### Test wiring

```scala
val recorder = InMemoryLogRecorder.unsafeMake()
val testAppLayer: Layer[Counter & Log, Any] =
    Layer.init[Counter & Log](
        productionCounter,                       // reuse production impl
        InMemoryLogRecorder.layer(recorder)      // swap in-memory log
    )

val testProgram: Int < IO =
    Memo.run(Env.runLayer(testAppLayer)(runWork))

// After running, assert on recorder.snapshot()
```

## Why these rules

- **Contracts in the type system.** `Env[Service]` in the effect row means
  forgetting to provide a layer is a compile error.
- **Layers are values.** They compose with `Layer.init` and can be
  swapped at any boundary — production, test, or future host (CLI, wasm,
  playground).
- **No global state.** Every service is provided locally to the
  computation that needs it.
- **Logging is observable but decoupled.** Library code declares `Env[Log]`;
  hosts (CLI, JVM tests, browser) decide what backend receives the
  emissions.

## Adding a new service

1. Define the `trait` with effect-tracked methods.
2. Add at least two layers: production and test.
3. If the service is used cross-platform, ensure the trait's methods
   compile on JVM, JS, and Native (most Kyo effects support all three).
4. Document the new service under `docs/cookbook/` per the
   value-unlock convention (see `CLAUDE.md` / `AGENTS.md`).
````

- [ ] **Step 8.2: Verify the doc renders**

Open the file in any markdown viewer or run:

```bash
git diff --stat HEAD docs/conventions/kyo-services.md
```

Confirm the file size is reasonable (~3 KB) and no syntax errors.

- [ ] **Step 8.3: Commit**

```bash
git add docs/conventions/kyo-services.md
git commit -m "docs(conventions): add Kyo service-pattern guide with worked example

Three rules (contract first / consume via Env / provide via Layer),
logging-is-a-service section, and a runnable Counter example showing
production + test wiring with ScribeLogLayer + InMemoryLogRecorder.

Tracks GitHub #24 (EPIC-1, K-1.2)."
```

---

### Task 9: Final verification + push

- [ ] **Step 9.1: Full test sweep**

Run:

```bash
./mill --no-server krueger.trees.jvm.test.testForked
./mill --no-server krueger.core.jvm.test.testForked
./mill --no-server krueger.compiler-api.jvm.test.testForked
```

Expected: all tests pass on JVM. New specs pass; pre-existing specs unchanged.

- [ ] **Step 9.2: Cross-platform compile sweep**

Run:

```bash
./mill --no-server krueger.trees.js.compile krueger.core.js.compile krueger.compiler-api.js.compile
./mill --no-server krueger.trees.native.compile krueger.core.native.compile krueger.compiler-api.native.compile
./mill --no-server krueger.compiler-api.wasm.compile
./mill --no-server krueger.webapp-wasm.compile
```

Expected: BUILD SUCCESS on all. If a Native compile fails on Kyo dep availability, see Task 1 Step 1.7 for the platform-gating decision path.

- [ ] **Step 9.3: Re-enable bd auto-export**

Run:

```bash
bd config set export.auto true
```

(We disabled it during the rebase to stop the post-commit drift cycle.)

- [ ] **Step 9.4: bd state push**

Run:

```bash
bd dolt push
```

Expected: "Push complete."

- [ ] **Step 9.5: Close completed bd issues**

Run:

```bash
bd close musing-chaum-7e7242-070    # K-1.1: deps + resolver
bd close musing-chaum-7e7242-bvc    # K-1.2: service-pattern doc + scribe + ScribeLogHandler
bd close musing-chaum-7e7242-57f    # K-1.3: Kyo-aware visitors + KyoQueryableTree
bd close musing-chaum-7e7242-dmz    # K-1.4: Stage[I, O, S]
bd close musing-chaum-7e7242-bie    # EPIC-1 itself, after dependents close
bd dolt push
```

Expected: every close succeeds. EPIC-1 close succeeds because its blocking sub-tasks (K-1.1..K-1.4) are now closed.

- [ ] **Step 9.6: Push branch to origin**

Run:

```bash
git pull --rebase origin main
git push -u origin claude/wonderful-chandrasekhar-6d5277
git status
```

Expected: `git status` shows "up to date with origin".

- [ ] **Step 9.7: Open PR-A as a draft for review**

Run:

```bash
gh pr create --repo Eleven19/krueger \
  --base main \
  --head claude/wonderful-chandrasekhar-6d5277 \
  --draft \
  --title "PR-A: Kyo foundation — deps, service pattern, Kyo-aware visitors, Stage abstraction, scribe Log bridge" \
  --body "$(cat <<'EOF'
Implements EPIC-1 prerequisites (K-1.1, K-1.2, K-1.3, K-1.4) per the spec
at \`docs/superpowers/specs/2026-04-25-pr-a-kyo-foundation-design.md\`.

### What ships

- Kyo + scribe deps (>= ${'1.0-RC1+214-534321a9-SNAPSHOT'} for Kyo, 3.16.1 for scribe)
  with Sonatype snapshots resolver wired into mill-build.
- \`Stage[I, O, S]\` abstraction in \`compiler-api\` with effect-row-preserving
  composition.
- \`ScribeLogHandler\` + \`InMemoryLogRecorder\` bridging Kyo \`Log\` to scribe
  in production and to an in-memory recorder in tests.
- \`KyoQueryableTree\` extension over \`QueryableTree[T]\` with \`traverseKyo\` /
  \`foldKyo\`.
- \`KyoCstVisitor\` / \`KyoAstVisitor\` / \`KyoQueryVisitor\` siblings of the pure
  visitors. Pure visitors with their cursor API are unchanged.
- \`docs/conventions/kyo-services.md\` worked-example doc.

### Behavior change

None. PR-A is additive — no existing test changed, no pure API touched.

### Tracks

- GitHub: #24 (EPIC-1)
- bd: musing-chaum-7e7242-bie

### Test plan

- [x] \`./mill krueger.trees.jvm.test.testForked\` green
- [x] \`./mill krueger.core.jvm.test.testForked\` green
- [x] \`./mill krueger.compiler-api.jvm.test.testForked\` green
- [x] JVM + JS + Native compile sweep green
- [x] webapp-wasm + compiler-api.wasm linkers still produce artifacts
EOF
)"
```

Expected: PR opened as a draft. Mark ready for review only after CI is green.

- [ ] **Step 9.8: Confirm CI**

Wait for CI on the draft PR. Confirm:

- Lint job green
- Build & Test job green (JVM + Scala.js + Scala Native matrix)
- No new flaky tests

If CI fails, fix the underlying issue (do not skip hooks). Re-push. Iterate until green.

---

## Self-Review

After implementing the plan, run this checklist:

- [ ] **Spec coverage:** every PR-A acceptance criterion in the spec maps to a task above (deps, scribe, Kyo visitors, KyoQueryableTree, Stage, convention doc, CI compatibility).
- [ ] **Placeholder scan:** no `TBD`, `TODO`, `implement later`, or vague hand-waves in any task.
- [ ] **Type consistency:** `Stage[I, O, S]` signatures in Task 2 match the references in any later task (none of the later tasks reference Stage; standalone). `Log` API surface used in Task 3 matches what the actual Kyo snapshot publishes (verify at execution time).
- [ ] **Snapshot API caveat:** the Kyo SNAPSHOT version may evolve API shapes (e.g., `Log.Level` location). Tests are shape-stable and will catch a mismatch; impls adapt at execution time.

If issues are found, fix inline; no need to re-review.

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-04-25-pr-a-kyo-foundation.md`. Two execution options:**

**1. Subagent-Driven (recommended)** — dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — execute tasks in this session using executing-plans, batch execution with checkpoints.

**Which approach?**
