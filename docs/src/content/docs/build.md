---
title: Build and qualify Krueger
description: Build every platform, test the exact candidate and preview your compiler change in the playground.
---

## Why this exists

A parser change can work on the JVM and still break the browser playground or
Native library. The sbt build shares the existing sources across platforms and
provides full-test commands that check discovery before executing suites. You
can follow one change from its unit test through the query engine to the
published documentation and both browser backends.

## Set up

Install JDK 25, Node 24 and Git. On Linux, install `clang libunwind-dev libgc-dev`
for Scala Native. On macOS, install Xcode command-line tools and `boehm-gc`.
From the repository root, run:

```sh
./sbt --batch 'show sbtVersion' compile
```

The launcher downloads sbt 2.0.11 from Maven Central and checks its pinned
SHA-256. It runs a standalone JVM and accepts `JAVA_OPTS`. An installed sbt
runner also reads `project/build.properties`; use `sbt --server` for a standalone
session or its default client mode for repeated interactive commands. On
Windows, use an installed sbt 2 runner with JDK 25. The build definition uses
Scala 3.8.4, independently of the library compiler pinned to Scala 3.9.0.
Scala.js remains 1.22.0, Native 0.5.12 and Kyo 1.0.0-RC7.

Import `build.sbt` in IntelliJ or Metals. Generated BSP/IDE data stays ignored.
No source directories need moving.

## Common commands

Run these from the repository root:

```sh
./sbt --batch compile
./sbt --batch scalafmtAll scalafmtSbt
./sbt --batch scalafmtCheckAll scalafmtSbtCheck
./sbt --batch testJvmFull
./sbt --batch testJsFull
./sbt --batch testNativeFull
./sbt --batch testWasmFull
./sbt --batch testIntegrationFull
./sbt --batch qualifyAll
./sbt --batch stageWasm apiDocs site
./sbt --batch publishLocal
```

For iteration, `./sbt 'coreJvm/test *ModuleParserSpec'` runs selected suites.
In sbt 2, `test` can reuse test results. The `test*Full` aliases explicitly
check suite discovery, clear stale reports and invoke `Test/testFull`; `qualifyAll` includes all
13 platform/integration projects and reruns tests on a warm build. An empty
required suite fails with its project name. Fresh XML reports must contain
executed tests for every discovered suite. The BDD project contains the
Cucumber suite, Gherkin resources and Chicory runtime checks.

`stageWasm` links JS and WebGC and replaces
`sites/try-wasm/static/wasm/{facade,webgc}`. It validates the producer files
before replacing output and removes source maps and references. Linked files
are in `target/out/linked/{webapp-js,webapp-wasm,compiler-api-wasm}`.
`apiDocs` creates combined core/trees Scaladoc for JVM, JS and Native under
`docs/public/api/`, excluding those generated pages from Pagefind indexing.
These staging operations run every time, including after output deletion.

| Previous Mill command | sbt replacement |
| --- | --- |
| `krueger.core.jvm.compile` | `coreJvm/compile` |
| `krueger.core.js.test` | `testJsFull` |
| `krueger.core.native.test` | `testNativeFull` |
| `krueger.compiler-api.test` | `testJvmFull testJsFull testNativeFull testWasmFull` |
| `krueger.itest` | `testIntegrationFull` |
| `krueger.webapp-wasm.writeToWasmSite` | `stageWasm` |
| `docs.writeToDocsPublic` | `apiDocs` |
| `docs.prepareLocalDevSite` | `prepareLocalDevSite` |
| `docs.site` | `site` |
| `__.publishLocal` | `publishLocal` |

## Use it from your code

The Maven organization and artifact names are unchanged. In a consumer's
`build.sbt`, replace `VERSION` with a published version:

```scala
scalaVersion := "3.9.0"
libraryDependencies += "io.eleven19.krueger" %% "krueger-core" % "VERSION"
```

With sbt 2, `%%` also selects the JS or Native platform suffix in a project
with the corresponding platform plugin. JVM uses `_3`, JS uses `_sjs1_3`, and
Native uses `_native0.5_3`. The WebGC compiler links the same Scala.js library
IR and does not need a second Maven coordinate.

## Compose parsing with queries

The core dependency brings in the tree/query library. This complete program
parses an Elm module and finds its value declaration:

```scala
import io.eleven19.krueger.Krueger
import io.eleven19.krueger.cst.CstNode
import io.eleven19.krueger.cst.CstQueryableTree.given
import io.eleven19.krueger.trees.query.*
import parsley.Success

@main def inspectModule(): Unit =
    val source = "module Main exposing (..)\n\nmain = 42\n"
    val Success(tree) = Krueger.parseCst(source): @unchecked
    val Success(query) = QueryParser.parse("(CstValueDeclaration) @decl"): @unchecked
    println(Matcher.matches[CstNode](query, tree).toList.size)
```

## Real developer recipe

To change a parser diagnostic and inspect the effect in both browser backends:

1. Add a failing test in `krueger/core/test/src/` and a negative or boundary
   scenario in `krueger/itest/resources/features/`.
2. Run `./sbt --batch testJvmFull testIntegrationFull` and confirm the expected
   failure. Implement the change and rerun until both pass.
3. Run `./sbt --batch qualifyAll` to execute JVM, JS, Native, WebGC and BDD tests.
4. Run `./sbt --batch prepareLocalDevSite`, then `npm --prefix docs run dev`.
   Open `http://localhost:4321/krueger/try/`, enter the failing Elm input and
   toggle the JS and WASM backends. Both show the same structured diagnostic.
5. Run `./sbt --batch site` to produce `docs/dist/`, including all API roots and
   `try/`. Run `npm --prefix docs run preview` to inspect the static output.
6. Run `./sbt --batch publishLocal` to consume your change from another Scala
   project with `0.1.0-SNAPSHOT` and the normal dependency declaration.

## CI and publishing

`scripts/ci/qualify.sh` checks formatting, full suites, site output, workflow
contracts and browser/runtime consumers on the current checkout. CI retains
separate JVM/BDD, JS/browser, Native, docs and lint categories; `CI required`
fails if any selected category fails, is cancelled, is missing or is skipped.
Dependency caches speed resolution; cached test success is never qualification.

The default development version is `VERSION` plus `-SNAPSHOT`.
`PUBLISH_VERSION` overrides it for release and snapshot workflows. Existing
release/tag/manual triggers and `ELEVEN19_*` secret names remain unchanged.
Publication workflows qualify their checkout before calling
`scripts/ci/publish-central.sh VERSION`. That script validates credentials,
imports the signing key into a temporary keyring, signs artifacts, uploads
snapshots directly and promotes staged stable releases with `sonaRelease`.

For packaging checks, use `publishLocal` or `publishM2`. For signed staging,
use a disposable local signing key, a non-snapshot `PUBLISH_VERSION`, and
`./sbt --batch publishSigned` with `publishTo` set to a local file repository.
Do not invoke `sonaUpload` or `sonaRelease` during a packaging test.
