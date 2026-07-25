# Direct kyo-test Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the temporary ZIO Test compatibility shims with idiomatic, discoverable kyo-test RC5 suites and restore every PR #50 CI path.

**Architecture:** Each existing test file remains an independent no-argument `Test[Any]` class using native kyo-test groups, leaves, assertions, and effects. Mill retains platform-specific kyo-test runners, while its advanced Scala.js integration supplies the ES2022 configuration required by Scala.js 1.22 Wasm.

**Tech Stack:** Scala 3.8.3, Kyo/kyo-test 1.0.0-RC5, Mill 1.2.0-RC1, Scala.js 1.22.0, Scala Native 0.5.11, Scalafmt 3.9.4.

## Global Constraints

- Follow strict red-green-refactor; do not change production behavior before a failing reproduction exists.
- Preserve every existing behavioral assertion, including negative, boundary, deterministic-order, and regression coverage.
- Suite, group, and leaf display names may improve; test files remain separate and are not consolidated.
- Remove all `zio.test` source shims and imports; do not disable kyo-test's `failOnNoAssertion` check.
- Retain only PR changes justified by Kyo RC5, kyo-test, or Scala.js 1.22 after comparison with `origin/main`.
- Do not initialize or mutate Beads while the configured Dolt database is unavailable.

---

### Task 1: Prove native kyo-test discovery with one representative suite

**Files:**
- Modify: `krueger/core/test/src/io/eleven19/krueger/SpanSpec.scala`
- Test: `krueger/core/test/src/io/eleven19/krueger/SpanSpec.scala`

**Interfaces:**
- Consumes: `kyo.test.Test[Any]`, `String.-`, `String.in`, and `kyo.test.assert`.
- Produces: one non-module suite class discoverable by `kyo.test.runner.SbtFramework`.

- [ ] **Step 1: Record the failing discovery baseline**

Run:

```bash
./mill show krueger.core.jvm.test.discoveredTestClasses
./mill krueger.core.jvm.test
```

Expected: `discoveredTestClasses` is `[]` and the test task reports `0 tests`.

- [ ] **Step 2: Convert `SpanSpec` directly**

Replace the ZIO-shaped suite with this native structure while retaining its three assertions:

```scala
package io.eleven19.krueger

import kyo.test.*

class SpanTest extends Test[Any]:
    "Span" - {
        "represents the empty span at offset zero" in {
            assert(Span.zero == Span(0, 0))
        }
        "computes the end offset" in {
            assert(Span(3, 7).end == 10)
        }
        "spans from the first offset through the second end" in {
            val a = Span(2, 3)
            val b = Span(10, 2)
            assert(Span.between(a, b) == Span(2, 10))
        }
    }
```

- [ ] **Step 3: Verify native discovery and assertions**

Run the two commands from Step 1. Expected: discovery contains `io.eleven19.krueger.SpanTest`; three leaves run and pass, while still-unconverted singleton suites remain undiscovered.

- [ ] **Step 4: Format the representative suite**

Run `./mill krueger.core.jvm.reformat` and confirm `./mill krueger.core.jvm.checkFormat` passes for the converted file.

### Task 2: Convert the trees suites

**Files:**
- Modify: `krueger/trees/test/src/io/eleven19/krueger/trees/KyoQueryableTreeSpec.scala`
- Modify: `krueger/trees/test/src/io/eleven19/krueger/trees/MatcherSpec.scala`
- Modify: `krueger/trees/test/src/io/eleven19/krueger/trees/NodeTypeNameSpec.scala`
- Modify: `krueger/trees/test/src/io/eleven19/krueger/trees/QueryCursorSpec.scala`
- Modify: `krueger/trees/test/src/io/eleven19/krueger/trees/QueryExecutionPipelineSpec.scala`
- Modify: `krueger/trees/test/src/io/eleven19/krueger/trees/QueryLogicSpec.scala`
- Modify: `krueger/trees/test/src/io/eleven19/krueger/trees/QueryParserSpec.scala`
- Modify: `krueger/trees/test/src/io/eleven19/krueger/trees/QueryPrinterSpec.scala`
- Modify: `krueger/trees/test/src/io/eleven19/krueger/trees/QuerySpec.scala`
- Modify: `krueger/trees/test/src/io/eleven19/krueger/trees/QueryVisitorSpec.scala`
- Modify: `krueger/trees/test/src/io/eleven19/krueger/trees/QueryableTreeSpec.scala`
- Modify: `krueger/trees/test/src/io/eleven19/krueger/trees/TreesSpec.scala`
- Modify: `krueger/trees/test/src/io/eleven19/krueger/trees/UnistProjectionSpec.scala`
- Modify: `krueger/trees/test/src/io/eleven19/krueger/trees/query/KyoQueryVisitorSpec.scala`
- Delete: `krueger/trees/test/src/zio/test/ZIOSpecDefault.scala`

**Interfaces:**
- Consumes: native kyo-test registration proved in Task 1.
- Produces: fourteen discoverable trees suite classes with native assertion accounting.

- [ ] **Step 1: Convert suite declarations and imports**

For each file, replace `import zio.test.*` with `import kyo.test.*`, replace the singleton `object ... extends ZIOSpecDefault` with a no-argument `class ... extends Test[Any]`, and remove the `def spec =` wrapper.

- [ ] **Step 2: Convert the DSL structure**

Translate every `suite("name")` into a native `"name" - { ... }` group and every `test("name")` into a `"name" in { ... }` leaf. Remove call-list commas and closing parentheses that belonged only to the old `suite`/`test` functions.

- [ ] **Step 3: Convert assertions without weakening them**

Replace each single-condition `assertTrue(x)` with `assert(x)`. Replace `assertTrue(a, b, c)` with three ordered calls—`assert(a)`, `assert(b)`, `assert(c)`—so each condition is recorded separately. Replace any `assertCompletes` with `succeed`.

- [ ] **Step 4: Remove the trees shim and verify no legacy references remain**

Delete the shim, then run:

```bash
grep -RInE 'zio\.test|ZIOSpecDefault|assertTrue|assertCompletes' krueger/trees/test/src --include='*.scala'
```

Expected: no matches.

- [ ] **Step 5: Verify trees discovery and JVM behavior**

Run:

```bash
./mill show krueger.trees.jvm.test.discoveredTestClasses
./mill krueger.trees.jvm.test
```

Expected: fourteen classes are discovered and all trees leaves pass with non-zero assertion counts.

### Task 3: Convert the compiler-api and webapp-wasm suites

**Files:**
- Modify: `krueger/compiler-api/test/src/io/eleven19/krueger/compiler/CompilerComponentSpec.scala`
- Modify: `krueger/compiler-api/test/src/io/eleven19/krueger/compiler/StageSpec.scala`
- Modify: `krueger/compiler-api/test/src/io/eleven19/krueger/compiler/abi/AbiEntryPointSpec.scala`
- Modify: `krueger/compiler-api/test/src/io/eleven19/krueger/compiler/abi/InvokeCompilerSpec.scala`
- Delete: `krueger/compiler-api/test/src/zio/test/ZIOSpecDefault.scala`
- Modify: `krueger/webapp-wasm/test/src/io/eleven19/krueger/webappwasm/KruegerJsSpec.scala`
- Delete: `krueger/webapp-wasm/test/src/zio/test/ZIOSpecDefault.scala`

**Interfaces:**
- Consumes: native cross-platform kyo-test DSL and the existing Scala.js alias `import scala.scalajs.{js => sjs}`.
- Produces: four compiler-api suites and one webapp-wasm suite discoverable on their configured platforms.

- [ ] **Step 1: Convert all five suites to native kyo-test**

Apply the declaration, group, leaf, assertion, and `succeed` mappings from Task 2. Preserve the `sjs` alias in `KruegerJsSpec.scala` so it cannot collide with kyo-test names.

- [ ] **Step 2: Delete both shims and scan for legacy references**

Run:

```bash
grep -RInE 'zio\.test|ZIOSpecDefault|assertTrue|assertCompletes' \
  krueger/compiler-api/test/src krueger/webapp-wasm/test/src --include='*.scala'
```

Expected: no matches.

- [ ] **Step 3: Verify JVM compiler-api tests**

Run discovery and `./mill krueger.compiler-api.jvm.test`. Expected: four suites are discovered and pass.

- [ ] **Step 4: Verify webapp-wasm compilation and discovery**

Run `./mill show krueger.webapp-wasm.test.discoveredTestClasses` and its configured test task. Expected: the suite is discovered; if Node is unavailable, compilation/discovery still pass and the environment failure is recorded separately.

### Task 4: Convert the remaining core suites and expose RC5 regressions

**Files:**
- Modify: every `*Spec.scala` under `krueger/core/test/src/io/eleven19/krueger/` except the representative `SpanSpec.scala` already converted in Task 1.
- Delete: `krueger/core/test/src/zio/test/ZIOSpecDefault.scala`

**Interfaces:**
- Consumes: native kyo-test DSL and assertions.
- Produces: twenty-one total discoverable core suites and actual execution of the full core regression set.

- [ ] **Step 1: Convert the remaining twenty core suite files**

Use the same mappings as Task 2. Keep file boundaries and all test bodies. Improve descriptions only when the existing name is unclear; do not change expected values or remove assertions.

- [ ] **Step 2: Delete the core shim and scan the entire repository test tree**

Run:

```bash
grep -RInE 'zio\.test|ZIOSpecDefault|assertTrue|assertCompletes' krueger/*/test/src --include='*.scala'
```

Expected: no matches and no `krueger/*/test/src/zio` files remain.

- [ ] **Step 3: Run the now-discovered core suite as the red regression gate**

Run `./mill show krueger.core.jvm.test.discoveredTestClasses` and `./mill krueger.core.jvm.test`. Expected before Task 5: twenty-one suites are discovered; the two in-memory logging assertions fail because RC5 dispatch is asynchronous. Any additional failure is investigated independently before production changes.

### Task 5: Adapt logging tests to RC5 asynchronous dispatch

**Files:**
- Modify: `krueger/core/test/src/io/eleven19/krueger/log/ScribeLogHandlerSpec.scala`

**Interfaces:**
- Consumes: `Log.flush: Unit < Async` and kyo-test's effectful leaf type.
- Produces: recorder snapshots taken after all queued events reach their sink.

- [ ] **Step 1: Replace unsafe evaluation in the two recorder leaves**

Return the Kyo computation from each leaf:

```scala
program.andThen(Log.flush).map { _ =>
    val events = recorder.snapshot()
    assert(events.map(_.message) == expected)
}
```

Use the three-message expected list in the order-preservation leaf.

- [ ] **Step 2: Verify all core tests pass**

Run `./mill krueger.core.jvm.test`. Expected: all 137 existing core leaves pass with zero no-assertion failures.

### Task 6: Configure Scala.js 1.22 Wasm for ES2022

**Files:**
- Modify: `build.mill.yaml`
- Modify: `mill-build/src/build/Modules.scala`

**Interfaces:**
- Consumes: `mill.scalajslib.config.ScalaJSConfigModule` and `org.scalajs.linker.interface.StandardConfig`.
- Produces: a Wasm linker configuration targeting ES2022 with WebAssembly enabled.

- [ ] **Step 1: Record the existing Wasm red failure**

Run `./mill krueger.webapp-wasm.writeToWasmSite`. Expected: `The WebAssembly backend requires ECMAScript 2022 or later`.

- [ ] **Step 2: Add the advanced linker meta-build dependencies**

Add under `mill-build.mvnDeps` in `build.mill.yaml`:

```yaml
- com.lihaoyi::mill-libs-scalajslib-config-1:1.2.0-RC1
- org.scala-js:scalajs-linker_2.13:1.22.0
- org.scala-js:scalajs-js-envs_2.13:1.4.0
```

- [ ] **Step 3: Configure the Wasm module with the Scala.js API**

Mix `mill.scalajslib.config.ScalaJSConfigModule` into `CommonScalaJSWasmModule`, then add:

```scala
override def scalaJSConfig: Task[org.scalajs.linker.interface.StandardConfig] = Task.Anon {
  super.scalaJSConfig().withESFeatures(
    _.withESVersion(org.scalajs.linker.interface.ESVersion.ES2022)
      .withUseWebAssembly(true)
  )
}
```

- [ ] **Step 4: Verify both playground backends link and stage**

Run `./mill krueger.webapp-wasm.writeToWasmSite`. Expected: one facade artifact and the Wasm loader/binary artifacts are copied successfully.

### Task 7: Format, cross-platform verify, and publish

**Files:**
- Format: all modified Scala and Mill files.
- Verify: `README.md`
- Verify: `docs/src/content/docs/contributing.md`
- Verify: `docs/src/content/docs/index.mdx`
- Update: PR #50 description.

**Interfaces:**
- Consumes: the completed native suites and linker configuration.
- Produces: a clean, documented, pushed PR with CI-equivalent evidence.

- [ ] **Step 1: Format and eliminate line-ending-only noise**

Run the relevant `reformat` tasks, then `git diff --check`. Review `git diff --ignore-space-at-eol` to confirm no accidental CRLF-only content remains.

- [ ] **Step 2: Verify all platform discovery lists are non-empty**

Run `discoveredTestClasses` for core, trees, compiler-api, and webapp-wasm on JVM/JS/Native variants that exist. Expected suite counts are 21, 14, 4, and 1 per corresponding source set.

- [ ] **Step 3: Run CI-equivalent gates**

Run JVM test tasks, Scala.js tests with Node available, Scala Native tests with its toolchain available, `krueger.itest`, `krueger.webapp-wasm.writeToWasmSite`, and the exact formatting command from `.github/workflows/ci.yml`.

- [ ] **Step 4: Review the complete branch diff**

Run `git diff origin/main...HEAD` plus the uncommitted diff. Remove compatibility-layer claims and retain only RC5/kyo-test/Scala.js changes. Confirm the documentation names kyo-test and valid commands.

- [ ] **Step 5: Update the PR description**

Describe the direct native migration, discovered/executed suite counts, async logging adaptation, ES2022 linker configuration, and link the design document. Remove the statement that temporary `zio.test.ZIOSpecDefault` shims remain.

- [ ] **Step 6: Commit, synchronize, and push**

Commit the implementation, run `git pull --rebase`, attempt `bd dolt push` only if the configured database has been restored, push `kyo-rc5-finish-pass`, and verify `git status` reports the branch up to date with origin.

- [ ] **Step 7: Recheck PR checks**

Run `gh pr checks 50`; inspect any remaining GitHub Actions failure by run and job log before making another change.
