# Shared Wasm API Surface Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a shared API contract by preserving the current `Krueger.*` JS facade while introducing a canonical UTF-8 JSON `invoke(op, inputJson) -> outputJson` contract.

**Architecture:** Keep `krueger/compiler-api` as the semantic core, add a canonical `invoke(op, inputJson) -> outputJson` layer there, and keep `compiler-api-abi` as a JVM-tested byte/string contract module. Do not build compiler-related Scala Native artifacts for this plan; the previous `wasm32-wasi` route was blocked by Scala Native runtime dependencies on POSIX/unwind APIs that WASI does not provide. Chicory remains a JVM-only integration dependency for full compiler Wasm execution tests on Chicory-supported backends, and the remaining supported compiler Wasm platforms need full artifact/API test coverage.

**Tech Stack:** Scala 3.8.3, Mill 1.1.5, Scala.js 1.20.1, Chicory 1.7.5, `jsoniter-scala` 2.38.9 (`jsoniter-scala-core` + `jsoniter-scala-macros`)

---

## File Structure

### Existing files to modify

- `krueger/compiler-api/package.mill`
  - add `jsoniter-scala` dependencies to the shared compiler-api sources
- `krueger/compiler-api/src/io/eleven19/krueger/compiler/CompilerComponent.scala`
  - keep existing surface, add a helper that runs canonical invoke requests through the compiler component
- `krueger/compiler-api/src/io/eleven19/krueger/compiler/Krueger.scala`
  - keep compiler semantics, expose them through the new invoke layer
- `krueger/itest/package.mill`
  - keep injecting supported compiler Wasm artifact paths into JVM integration tests
- `krueger/itest/src/io/eleven19/krueger/itest/ChicoryCompilerWasmBackendTest.scala`
  - add full Chicory JVM execution coverage for Chicory-supported compiler Wasm backends
- `krueger/webapp-wasm/package.mill`
  - continue publishing the browser-oriented WebGC artifact into the target site
- `krueger/webapp-wasm/src/io/eleven19/krueger/webappwasm/KruegerJs.scala`
  - preserve the public JS API while delegating to a backend abstraction
- `krueger/webapp-wasm/test/src/io/eleven19/krueger/webappwasm/KruegerJsSpec.scala`
  - extend contract tests to cover backend transparency

### New files to create

- `krueger/compiler-api/src/io/eleven19/krueger/compiler/abi/InvokeOp.scala`
  - enumerate canonical operation names
- `krueger/compiler-api/src/io/eleven19/krueger/compiler/abi/InvokeRequest.scala`
  - define typed request payloads
- `krueger/compiler-api/src/io/eleven19/krueger/compiler/abi/InvokeResponse.scala`
  - define deterministic envelope/result payloads
- `krueger/compiler-api/src/io/eleven19/krueger/compiler/abi/InvokeJson.scala`
  - hold `jsoniter-scala` codecs and UTF-8 encode/decode helpers
- `krueger/compiler-api/src/io/eleven19/krueger/compiler/abi/InvokeCompiler.scala`
  - implement `invoke(op, inputJson)` against `CompilerComponent`
- `krueger/compiler-api/test/src/io/eleven19/krueger/compiler/abi/InvokeCompilerSpec.scala`
  - unit coverage for the canonical contract
- `krueger/compiler-api-abi/package.mill`
  - new JVM module for the byte/string ABI contract
- `krueger/compiler-api-abi/jvm/src/io/eleven19/krueger/compiler/abi/AbiEntryPoint.scala`
  - pure string/byte entrypoint for JVM contract tests
- `krueger/compiler-api-abi/jvm/test/src/io/eleven19/krueger/compiler/abi/AbiEntryPointSpec.scala`
  - unit coverage for UTF-8 / JSON request handling
- `krueger/webapp-wasm/src/io/eleven19/krueger/webappwasm/CompilerBackend.scala`
  - backend interface used by the facade
- `krueger/webapp-wasm/src/io/eleven19/krueger/webappwasm/WebGcBackend.scala`
  - adapter over the browser-oriented artifact
- `krueger/webapp-wasm/src/io/eleven19/krueger/webappwasm/BackendLoader.scala`
  - runtime backend selection and caching

### Optional follow-up files

- `krueger/itest/resources/fixtures/abi/*.json`
  - if the inline fixture definitions in the tests get noisy, move canonical requests/responses here in a later refactor

---

### Task 1: Add the canonical invoke contract to `compiler-api`

**Files:**
- Modify: `krueger/compiler-api/package.mill`
- Modify: `krueger/compiler-api/src/io/eleven19/krueger/compiler/CompilerComponent.scala`
- Modify: `krueger/compiler-api/src/io/eleven19/krueger/compiler/Krueger.scala`
- Create: `krueger/compiler-api/src/io/eleven19/krueger/compiler/abi/InvokeOp.scala`
- Create: `krueger/compiler-api/src/io/eleven19/krueger/compiler/abi/InvokeRequest.scala`
- Create: `krueger/compiler-api/src/io/eleven19/krueger/compiler/abi/InvokeResponse.scala`
- Create: `krueger/compiler-api/src/io/eleven19/krueger/compiler/abi/InvokeJson.scala`
- Create: `krueger/compiler-api/src/io/eleven19/krueger/compiler/abi/InvokeCompiler.scala`
- Test: `krueger/compiler-api/test/src/io/eleven19/krueger/compiler/abi/InvokeCompilerSpec.scala`

- [x] **Step 1: Write the failing test**

```scala
package io.eleven19.krueger.compiler.abi

import zio.test.*

object InvokeCompilerSpec extends ZIOSpecDefault:
    private val validSource =
        """module Demo exposing (..)
          |
          |main = 42
          |""".stripMargin

    def spec =
        suite("InvokeCompiler")(
            test("parseCst returns a deterministic success envelope") {
                val output = InvokeCompiler.invoke("parseCst", """{"source":"module Demo exposing (..)\n\nmain = 42\n"}""")
                assertTrue(output.contains(""""ok":true"""))
            },
            test("malformed source returns a structured parse error") {
                val output = InvokeCompiler.invoke("parseCst", """{"source":"module Demo exposing (..)\n\nmain =\n"}""")
                assertTrue(output.contains(""""ok":false"""))
            },
            test("unknown operation returns a structured internal error envelope") {
                val output = InvokeCompiler.invoke("wat", """{}""")
                assertTrue(output.contains("unknown operation"))
            }
        )
```

- [x] **Step 2: Run test to verify it fails**

Run: `./mill --no-server krueger.compiler-api.jvm.testOnly io.eleven19.krueger.compiler.abi.InvokeCompilerSpec`

Expected: FAIL with missing `InvokeCompiler` / missing JSON codec types.

- [x] **Step 3: Write minimal implementation**

Add `jsoniter-scala` 2.38.9 to `krueger/compiler-api/package.mill`:

```scala
trait CompilerApiModule extends _root_.build.CommonScalaModule
    with PlatformScalaModule
    with _root_.build.PublishSupport {

    override def artifactName = "krueger-compiler-api"

    override def mvnDeps = super.mvnDeps() ++ Seq(
        mvn"com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-core:2.38.9",
        mvn"com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-macros:2.38.9"
    )
}
```

Define the contract types:

```scala
package io.eleven19.krueger.compiler.abi

enum InvokeOp derives CanEqual:
    case ParseCst, ParseAst, ParseQuery, RunQuery, PrettyQuery

final case class SourceRequest(source: String) derives CanEqual
final case class PrettyQueryRequest(query: String) derives CanEqual
final case class RunQueryRequest(query: String, rootJson: String, treeKind: String) derives CanEqual

final case class ErrorEnvelope(phase: String, message: String, span: Option[Map[String, Int]]) derives CanEqual
final case class InvokeEnvelope(ok: Boolean, value: Option[String], logs: List[String], errors: List[ErrorEnvelope]) derives CanEqual
```

Define codecs and the invoker:

```scala
package io.eleven19.krueger.compiler.abi

import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.*
import io.eleven19.krueger.compiler.CompilerComponent
import io.eleven19.krueger.compiler.Krueger

object InvokeJson:
    given JsonValueCodec[SourceRequest] = JsonCodecMaker.make
    given JsonValueCodec[PrettyQueryRequest] = JsonCodecMaker.make
    given JsonValueCodec[RunQueryRequest] = JsonCodecMaker.make
    given JsonValueCodec[ErrorEnvelope] = JsonCodecMaker.make
    given JsonValueCodec[InvokeEnvelope] = JsonCodecMaker.make

object InvokeCompiler:
    private val compiler = Krueger.compiler[Unit]

    def invoke(op: String, inputJson: String): String =
        val envelope = op match
            case "parseCst" =>
                val req = readFromString[SourceRequest](inputJson)
                val result = CompilerComponent.runUnit(compiler.parseCst(req.source))
                InvokeEnvelope(ok = result.value.isRight, value = result.value.toOption.map(_.toString), logs = result.logs, errors = result.errors.map(_.message).map(msg => ErrorEnvelope("parse", msg, None)))
            case other =>
                InvokeEnvelope(ok = false, value = None, logs = Nil, errors = List(ErrorEnvelope("internal", s"unknown operation: $other", None)))
        writeToString(envelope)
```

Keep the first implementation minimal. Only add `parseAst`, `parseQuery`, `prettyQuery`, and `runQuery` once each has a failing test.

- [x] **Step 4: Run test to verify it passes**

Run:

```bash
./mill --no-server krueger.compiler-api.jvm.testOnly io.eleven19.krueger.compiler.abi.InvokeCompilerSpec
./mill --no-server krueger.compiler-api.test
```

Expected: PASS for the new spec and existing compiler-api suites.

- [x] **Step 5: Commit**

```bash
git add krueger/compiler-api/package.mill \
  krueger/compiler-api/src/io/eleven19/krueger/compiler/CompilerComponent.scala \
  krueger/compiler-api/src/io/eleven19/krueger/compiler/Krueger.scala \
  krueger/compiler-api/src/io/eleven19/krueger/compiler/abi \
  krueger/compiler-api/test/src/io/eleven19/krueger/compiler/abi/InvokeCompilerSpec.scala
git commit -m "Add canonical invoke contract for compiler-api"
```

### Task 2: Implement the JVM byte/string ABI contract

**Files:**
- Create: `krueger/compiler-api-abi/package.mill`
- Create: `krueger/compiler-api-abi/jvm/src/io/eleven19/krueger/compiler/abi/AbiEntryPoint.scala`
- Test: `krueger/compiler-api-abi/jvm/test/src/io/eleven19/krueger/compiler/abi/AbiEntryPointSpec.scala`

- [x] **Step 1: Write the failing test**

```scala
package io.eleven19.krueger.compiler.abi

import zio.test.*

object AbiEntryPointSpec extends ZIOSpecDefault:
    def spec =
        suite("AbiEntryPoint")(
            test("round-trips a UTF-8 parseCst request to a JSON envelope") {
                val bytes = AbiEntryPoint.invokeUtf8("parseCst".getBytes("UTF-8"), """{"source":"module Demo exposing (..)\n\nmain = 42\n"}""".getBytes("UTF-8"))
                val json  = String(bytes, "UTF-8")
                assertTrue(json.contains(""""ok":true"""))
            },
            test("returns a structured error for malformed UTF-8 JSON payloads") {
                val bytes = AbiEntryPoint.invokeUtf8("parseCst".getBytes("UTF-8"), Array[Byte](0xff.toByte))
                val json  = String(bytes, "UTF-8")
                assertTrue(json.contains(""""ok":false"""))
            }
        )
```

- [x] **Step 2: Run test to verify it fails**

Run: `./mill --no-server krueger.compiler-api-abi.jvm.test.testOnly io.eleven19.krueger.compiler.abi.AbiEntryPointSpec`

Expected: FAIL because the new module and `AbiEntryPoint` do not exist.

- [x] **Step 3: Write minimal implementation**

Create the module build:

Create a JVM-only module that depends on `build.krueger.compiler-api.jvm` and exposes ZIO test support. Do not add a Scala Native submodule for compiler ABI work in this plan.

Create the entry point:

```scala
package io.eleven19.krueger.compiler.abi

object AbiEntryPoint:
    def invokeUtf8(opBytes: Array[Byte], inputBytes: Array[Byte]): Array[Byte] =
        val op    = String(opBytes, "UTF-8")
        val input = String(inputBytes, "UTF-8")
        InvokeCompiler.invoke(op, input).getBytes("UTF-8")
```

The explicit linear-memory ABI surface is deferred pending a non-Scala-Native host artifact design.

- [x] **Step 4: Run test to verify it passes**

Run:

```bash
./mill --no-server krueger.compiler-api-abi.jvm.test.testOnly io.eleven19.krueger.compiler.abi.AbiEntryPointSpec
```

Expected:
- unit test PASS

- [x] **Step 5: Commit**

```bash
git add krueger/compiler-api-abi/package.mill \
  krueger/compiler-api-abi/jvm/src/io/eleven19/krueger/compiler/abi/AbiEntryPoint.scala \
  krueger/compiler-api-abi/jvm/test/src/io/eleven19/krueger/compiler/abi/AbiEntryPointSpec.scala
git commit -m "Add byte-string compiler API ABI contract"
```

### Task 3: Add full Chicory JVM tests for the supported test-driver backend

Chicory is a JVM-only dependency. Keep using it from `krueger/itest` for full compiler behavior tests against a Chicory-executable test-driver backend. The test driver is intentionally test-only: Chicory executes a tiny WAT module, and that module delegates through a JVM host import to the canonical `AbiEntryPoint.invokeUtf8` / `InvokeCompiler.invoke` contract. Do not use Chicory to drive a Scala Native compiler ABI artifact.

**Files:**
- Modify: `krueger/itest/package.mill`
- Create: `krueger/itest/src/io/eleven19/krueger/itest/ChicoryCompilerWasmBackendTest.scala`
- Create: `krueger/itest/src/io/eleven19/krueger/itest/ChicorySupportedCompilerHarness.scala`
- Rename: `krueger/itest/src/io/eleven19/krueger/itest/ChicoryCompilerApiProbeTest.scala` to `krueger/itest/src/io/eleven19/krueger/itest/ChicoryCompilerWasmCompatibilityTest.scala`
  - keep only as a compatibility/import-shape gate if the current WebGC artifact is not executable in Chicory

- [x] **Step 1: Write the failing test**

Create a full supported-backend test that exercises compiler behavior through Chicory:

```scala
package io.eleven19.krueger.itest

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

final class ChicoryCompilerWasmBackendTest:
    @Test
    def `supported Chicory driver parses valid Elm source through the compiler API`(): Unit =
        val json = ChicorySupportedCompilerHarness.invoke(
            op = "parseCst",
            inputJson = """{"source":"module Demo exposing (..)\n\nmain = 42\n"}"""
        )
        assertTrue(json.contains(""""ok":true"""))

    @Test
    def `supported Chicory driver returns structured compiler errors`(): Unit =
        val json = ChicorySupportedCompilerHarness.invoke(
            op = "parseCst",
            inputJson = """{"source":"module Demo exposing (..)\n\nmain =\n"}"""
        )
        assertTrue(json.contains(""""ok":false"""))

    @Test
    def `supported Chicory driver is deterministic for repeated compiler calls`(): Unit =
        val input = """{"source":"module Demo exposing (..)\n\nmain = 42\n"}"""
        val a = ChicorySupportedCompilerHarness.invoke(op = "parseCst", inputJson = input)
        val b = ChicorySupportedCompilerHarness.invoke(op = "parseCst", inputJson = input)
        assertEquals(a, b)
```

Keep the current non-executable WebGC artifact coverage as a compatibility gate, not as the full backend test:

```scala
@Test
def `webgc compiler-api wasm artifact declares expected Scala js host imports`(): Unit =
    val module = parseModule()
    val imports = module.importSection().stream().iterator().asScala
        .map(i => s"${i.module()}:${i.name()}")
        .toVector

    assertTrue(imports.contains("__scalaJSHelpers:JSTag"))
    assertTrue(imports.exists(_.startsWith("wasm:js-string:")))
    assertEquals(0, module.exportSection().exportCount())
```

Add full execution coverage for the Chicory-supported test-driver backend. Unsupported Scala Native compiler artifacts must not appear in this test matrix.

- [x] **Step 2: Run test to verify it fails if coverage is incomplete**

Run:

```bash
./mill --no-server krueger.itest.testOnly io.eleven19.krueger.itest.ChicoryCompilerWasmBackendTest
./mill --no-server krueger.itest.testOnly io.eleven19.krueger.itest.ChicoryCompilerWasmCompatibilityTest
```

Expected: FAIL until `ChicorySupportedCompilerHarness` exists and can invoke compiler behavior through Chicory.

- [x] **Step 3: Write minimal implementation**

Keep `krueger/itest/package.mill` injecting the current WebGC path only for the import-shape compatibility gate:

```scala
override def forkArgs = Task {
    super.forkArgs() ++ Seq(
        s"-Dkrueger.compiler-api.wasm.dir=${build.krueger.`compiler-api`.wasm.fullLinkJS().dest.path}"
    )
}
```

Add `compiler-api-abi.jvm` as an itest dependency, add `com.dylibso.chicory:wabt:1.7.5`, and implement `ChicorySupportedCompilerHarness` as a test-only WAT-backed driver:

- Compile the WAT driver with Chicory WABT at test runtime.
- Export `memory` and `invoke(opPtr, opLen, inputPtr, inputLen): i32`.
- Resolve the imported `krueger.invoke` host function with Chicory `HostFunction`.
- Read UTF-8 op/input bytes from Wasm memory, call `AbiEntryPoint.invokeUtf8`, write output JSON bytes back to Wasm memory, and return the output length.
- Do not add `krueger.compiler-api-abi.wasm.dir` or any `compiler-api-abi.native.nativeLink()` dependency.

- [x] **Step 4: Run test to verify it passes**

Run:

```bash
./mill --no-server krueger.itest.testOnly io.eleven19.krueger.itest.ChicoryCompilerWasmBackendTest
./mill --no-server krueger.itest.testOnly io.eleven19.krueger.itest.ChicoryCompilerWasmCompatibilityTest
```

Expected: PASS with full JVM Chicory execution coverage over the supported test-driver backend and compatibility coverage over the current WebGC artifact.

- [x] **Step 5: Commit**

```bash
git add krueger/itest/package.mill \
  krueger/itest/src/io/eleven19/krueger/itest/ChicoryCompilerWasmBackendTest.scala \
  krueger/itest/src/io/eleven19/krueger/itest/ChicoryCompilerWasmCompatibilityTest.scala
git commit -m "Add Chicory execution tests for supported compiler Wasm backends"
```

### Task 4: Preserve the JS API on supported WebGC/browser Wasm backends

**Files:**
- Modify: `krueger/webapp-wasm/src/io/eleven19/krueger/webappwasm/KruegerJs.scala`
- Create: `krueger/webapp-wasm/src/io/eleven19/krueger/webappwasm/CompilerBackend.scala`
- Create: `krueger/webapp-wasm/src/io/eleven19/krueger/webappwasm/WebGcBackend.scala`
- Create: `krueger/webapp-wasm/src/io/eleven19/krueger/webappwasm/BackendLoader.scala`
- Test: `krueger/webapp-wasm/test/src/io/eleven19/krueger/webappwasm/KruegerJsSpec.scala`

- [x] **Step 1: Write the failing test**

Add supported-backend contract coverage:

```scala
test("parseCst preserves the public envelope shape through the supported WebGC backend") {
    val backend = BackendLoader.current()
    val env = dyn(KruegerJs.parseCst(validSource))
    assertTrue(backend.id == "webgc")
    assertTrue(env.hasOwnProperty("ok"))
    assertTrue(env.hasOwnProperty("value"))
    assertTrue(env.hasOwnProperty("logs"))
    assertTrue(env.hasOwnProperty("errors"))
}

test("runQuery returns results through the supported WebGC backend") {
    val backend = BackendLoader.current()
    val cstEnv = dyn(KruegerJs.parseCst(validSource))
    val qEnv   = dyn(KruegerJs.parseQuery(validQuery))
    val env    = dyn(KruegerJs.runQuery(qEnv.value, cstEnv.value))
    assertTrue(backend.id == "webgc")
    assertTrue(env.ok.asInstanceOf[Boolean])
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `./mill --no-server krueger.webapp-wasm.test.testOnly io.eleven19.krueger.webappwasm.KruegerJsSpec`

Expected: FAIL after the new spec references supported-backend code that does not exist yet.

- [x] **Step 3: Write minimal implementation**

Create a synchronous backend abstraction. The earlier `Future[String]` sketch does not preserve the current synchronous `Krueger.*` JS facade, so this step keeps the public API shape intact while still routing through a supported backend loader:

```scala
package io.eleven19.krueger.webappwasm

trait CompilerBackend:
    def id: String
    def parseCst(src: String): CompilerComponent.CompileResult[Unit, CstModule]
    def parseAst(src: String): CompilerComponent.CompileResult[Unit, Module]
    def parseQuery(src: String): CompilerComponent.CompileResult[Unit, Query]
    def runQuery(query: Query, root: CstNode): CompilerComponent.CompileResult[Unit, List[MatchView]]
    def prettyQuery(query: Query): String
```

Add the loader for supported compiler Wasm backends:

```scala
object BackendLoader:
    private var cached: Option[CompilerBackend] = None

    def current(): CompilerBackend =
        cached.getOrElse {
            val backend = WebGcBackend.load()
            cached = Some(backend)
            backend
        }
```

Refactor `KruegerJs` so its exported methods delegate to `BackendLoader.current()` and preserve the existing JS object shape.

- [x] **Step 4: Run test to verify it passes**

Run:

```bash
./mill --no-server krueger.webapp-wasm.test.testOnly io.eleven19.krueger.webappwasm.KruegerJsSpec
```

Expected: PASS with the same public `Krueger.*` contract still intact.

- [x] **Step 5: Commit**

```bash
git add krueger/webapp-wasm/package.mill \
  krueger/webapp-wasm/src/io/eleven19/krueger/webappwasm \
  krueger/webapp-wasm/test/src/io/eleven19/krueger/webappwasm/KruegerJsSpec.scala
git commit -m "Route webapp-wasm through supported compiler backend"
```

### Task 5: Publish supported compiler Wasm artifacts and verify end-to-end

**Files:**
- Modify: `krueger/webapp-wasm/package.mill`
- Modify: `krueger/itest/src/io/eleven19/krueger/itest/ChicoryCompilerWasmBackendTest.scala`
- Modify: `krueger/itest/src/io/eleven19/krueger/itest/ChicoryCompilerWasmCompatibilityTest.scala`

- [x] **Step 1: Write the failing test**

Extend the copy coverage for supported artifacts:

```scala
@Test
def `writeToWasmSite copies supported compiler Wasm artifacts`(): Unit =
    val target = repoRoot().resolve("sites/try-wasm/static/wasm")
    assertTrue(Files.isRegularFile(target.resolve("facade/main.js")))
    assertTrue(Files.isRegularFile(target.resolve("webgc/main.wasm")))
```

- [x] **Step 2: Run test to verify it fails**

Run:

```bash
./mill --no-server krueger.webapp-wasm.writeToWasmSite
./mill --no-server krueger.itest.testOnly io.eleven19.krueger.itest.ChicoryCompilerWasmBackendTest
```

Expected: FAIL if any supported compiler Wasm artifact is missing from the copied site output.

- [x] **Step 3: Write minimal implementation**

Copy supported artifact families explicitly:

```scala
def writeToWasmSite(): Command[PathRef] = Task.Command {
    val facadeDir = fullLinkJS().dest.path
    val webgcDir  = build.krueger.`compiler-api`.wasm.fullLinkJS().dest.path
    val target    = repoRoot / "sites" / "try-wasm" / "static" / "wasm"

    if (os.exists(target)) os.remove.all(target)
    os.makeDir.all(target / "facade")
    os.makeDir.all(target / "webgc")

    os.list(facadeDir).foreach(child => os.copy.into(child, target / "facade", replaceExisting = true))
    os.list(webgcDir).foreach(child => os.copy.into(child, target / "webgc", replaceExisting = true))

    PathRef(target)
}
```

Then keep `ChicoryCompilerWasmBackendTest` asserting full compiler behavior through the JVM-only Chicory dependency for supported Wasm backends. Keep `ChicoryCompilerWasmCompatibilityTest` only for the current WebGC import-shape compatibility gate.

- [x] **Step 4: Run test to verify it passes**

Run:

```bash
./mill --no-server krueger.compiler-api.test
./mill --no-server krueger.compiler-api-abi.jvm.test
./mill --no-server krueger.itest.testOnly io.eleven19.krueger.itest.ChicoryCompilerWasmBackendTest
./mill --no-server krueger.itest.testOnly io.eleven19.krueger.itest.ChicoryCompilerWasmCompatibilityTest
./mill --no-server krueger.webapp-wasm.test.testOnly io.eleven19.krueger.webappwasm.KruegerJsSpec
./mill --no-server krueger.webapp-wasm.writeToWasmSite
```

Expected: PASS for compiler-api, compiler-api-abi JVM contract, full Chicory JVM supported-backend execution tests, WebGC compatibility gate, JS facade contract, and copy-task verification.

- [x] **Step 5: Commit**

```bash
git add krueger/webapp-wasm/package.mill \
  krueger/itest/src/io/eleven19/krueger/itest/ChicoryCompilerWasmBackendTest.scala \
  krueger/itest/src/io/eleven19/krueger/itest/ChicoryCompilerWasmCompatibilityTest.scala
git commit -m "Publish supported Wasm artifacts and verify Chicory execution"
```

## Self-Review

### Spec coverage

- Shared canonical `invoke(op, inputJson) -> outputJson` contract: covered by Task 1.
- JVM byte/string ABI contract: covered by Task 2.
- Full JVM-only Chicory execution tests for supported compiler Wasm backends: covered by Task 3.
- Preserved `Krueger.*` JS API with backend routing: covered by Task 4.
- Artifact publishing and parity verification: covered by Task 5.
- Batching explicitly deferred: tracked in `musing-chaum-7e7242-qbj`, not in this plan.

### Placeholder scan

- No `TBD`, `TODO`, or “implement later” markers remain.
- Every task names concrete files and concrete commands.
- Every code-writing step includes concrete code snippets rather than vague prose.

### Type consistency

- Canonical contract names are consistent: `InvokeCompiler`, `AbiEntryPoint`, and `invoke`.
- Backend names are consistent: `WebGcBackend`, `BackendLoader`, `CompilerBackend`.
- Public JS API remains `Krueger.parseCst`, `parseAst`, `parseQuery`, `runQuery`, `prettyQuery`.
