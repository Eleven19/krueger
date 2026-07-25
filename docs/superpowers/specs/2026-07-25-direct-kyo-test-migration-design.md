# Direct kyo-test Migration Design

## Goal

Replace Krueger's ZIO Test suites with idiomatic kyo-test RC5 suites, without a source-compatibility layer, while retaining the valid Kyo RC5 production adaptations already present on PR #50.

## Scope

The migration covers the shared tests in `core`, `trees`, `compiler-api`, and `webapp-wasm`; their Mill test wiring; the Scala.js 1.22 Wasm linker requirement; and RC5 behavior exposed when the tests actually execute. It does not consolidate test files, redesign production APIs, or preserve legacy suite display names.

## Branch Strategy

Use `origin/main` and the parent of the PR commit as references, but do not reset the whole branch. PR #50 contains independently useful RC5 changes in visitor, query, logging, documentation, and build code. Reconstructing all of those from scratch would increase risk.

Instead:

1. Normalize the pre-existing CRLF-only worktree changes after confirming they contain no semantic edits.
2. Remove all four compatibility shims.
3. Replace the shim-shaped test changes with a direct migration.
4. Review every remaining PR diff against `origin/main`; retain only changes justified by Kyo RC5, kyo-test, or Scala.js 1.22.

This keeps the PR reviewable without force-resetting or discarding valid work.

## Test Suite Architecture

Each test source file remains a separate suite class with a no-argument constructor:

```scala
import kyo.test.*

class SpanTest extends Test[Any]:
    "Span" - {
        "computes the end offset" in {
            assert(Span(3, 7).end == 10)
        }
    }
```

The migration may rename suite classes from `*Spec` to `*Test` and may improve group or leaf descriptions. Names do not need to match the ZIO Test output. File boundaries remain stable unless a filename must follow a renamed public suite class.

Native kyo-test constructs replace every compatibility construct:

- `class ... extends Test[Any]` replaces `object ... extends ZIOSpecDefault`.
- `"group" - { ... }` replaces `suite("group")(... )`.
- `"case" in { ... }` replaces `test("case") { ... }`.
- `assert(condition)` replaces `assertTrue(condition)`.
- Multiple `assertTrue` arguments become separate `assert` calls so failure reports identify the exact condition.
- `succeed` replaces `assertCompletes` for intentional smoke checks.

No code remains in the `zio.test` package, and test dependencies contain no ZIO Test artifacts.

## Effectful Tests

kyo-test leaf bodies natively accept the baseline `Async & Abort[Any] & Scope` effects. Existing tests that manually call `Sync.Unsafe.evalOrThrow` solely to bridge a test runtime should instead return or compose the Kyo computation.

The RC5 logger dispatches events asynchronously on JVM and Native. Recorder tests therefore compose their log program with `Log.flush` and assert only after the flush completes:

```scala
program.andThen(Log.flush).map { _ =>
    assert(recorder.snapshot().map(_.message) == expected)
}
```

Unsafe evaluation remains only where the behavior under test is explicitly an unsafe boundary.

## Build Wiring

Mill test modules continue to select the platform-specific kyo-test frameworks:

- JVM: `kyo.test.runner.SbtFramework`
- Scala.js and Wasm: `kyo.test.runner.JsFramework`
- Scala Native: `kyo.test.runner.NativeFramework`

The dependency set includes the JVM, Scala.js, or Scala Native variants of `kyo-test-api` and `kyo-test-runner` at `1.0.0-RC5`.

Scala.js 1.22 is required by the RC5-linked IR. Its Wasm backend requires ECMAScript 2022, while Mill 1.2.0-RC1's basic `ESVersion` facade stops at ES2021. The meta-build therefore adds Mill's `scalajslib-config` integration plus the Scala.js 1.22 linker, and `CommonScalaJSWasmModule` overrides `StandardConfig` to enable Wasm with `ESVersion.ES2022`.

## Failure Handling and Diagnostics

The migration must not accept a successful test command with zero discovered suites. Validation checks `discoveredTestClasses` before running each platform's tests and records the expected non-zero suite counts.

Native kyo-test assertions remain enabled, including its default failure for leaves that evaluate no assertions. Smoke tests must call `succeed`; the migration will not disable `failOnNoAssertion` globally or per suite.

## Verification

Red-green-refactor proceeds in layers:

1. Record current failures: zero discovered tests, CI formatting failures, and Wasm ES2022 failure.
2. Convert one representative suite and prove discovery plus native assertion reporting.
3. Convert the remaining suites mechanically, then improve naming without changing behavior.
4. Run JVM suites and resolve only failures revealed by actual execution, including `Log.flush`.
5. Compile and run Scala.js suites where Node is available; compile and run Scala Native suites where its toolchain is available.
6. Link and stage both JavaScript and Wasm playground artifacts.
7. Run formatting and the CI-equivalent workflow commands.
8. Review the complete diff against `origin/main`, commit, push, and recheck PR #50.

Existing happy-path, negative, boundary, deterministic-order, and regression assertions are preserved. The migration changes the test framework and organization, not the behavioral acceptance criteria those tests express.

## Documentation

Existing README and contributing documentation will describe kyo-test and the supported platform commands. No compatibility-layer documentation will be added because the layer will not exist. The PR description will be updated to remove its claim that temporary `zio.test.ZIOSpecDefault` shims are used.

## Out of Scope

- Merging or splitting test files for stylistic reasons.
- Adding a reusable ZIO Test compatibility library.
- Changing production behavior unrelated to Kyo RC5.
- Initializing or repairing the unavailable Beads Dolt database without separate authorization.
