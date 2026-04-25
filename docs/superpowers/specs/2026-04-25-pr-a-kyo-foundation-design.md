# PR-A: Kyo Foundation Design

Date: 2026-04-25
Status: Proposed
Tracks: GitHub [#24](https://github.com/Eleven19/krueger/issues/24) (EPIC-1) — bd `musing-chaum-7e7242-bie`

## Context

Krueger is growing toward an end-to-end compiler and testing toolchain that needs
to ship as both libraries and standalone tools across JVM, JS, and Native. Until
now, traversal has lived in two patterns — the cursor-bearing pure visitors (`CstVisitor` /
`AstVisitor` / `QueryVisitor`) and the typeclass-driven `QueryableTree[T]` plus
`UnistProjection` that arrived with PR #23. Configuration, dependency injection, and
asynchrony have been ad hoc. There is no shared way to thread context through stages,
no service-pattern convention, and no composable pipeline abstraction.

This PR introduces Kyo as a core dependency across `core`, `trees`, `compiler-api`,
and the future `testkit` and `cli` modules, and establishes the conventions that
the rest of the planned epics depend on. PR-A is intentionally **behavior-preserving** —
it adds APIs alongside existing ones, never replaces or breaks them.

PR-A also lands a project-wide convention update (see `CLAUDE.md` /
`AGENTS.md`): every user-facing feature must ship with use-case-based
value-unlock docs (Why-this-exists, Use-it-from-your-code, Compose-with-
other-Krueger-features, Real-world recipe). PR-A is library-foundation only
and does not introduce user-facing surfaces — the convention applies to
EPIC-2..EPIC-6 PRs as they land user-visible behavior. EPIC-5 owns the
docs-site cookbook surface where these recipes live.

## Goals

- Make Kyo available on every Krueger module, including the cross-platform
  variants supported by Kyo. Minimum required version is
  `1.0-RC1+214-534321a9-SNAPSHOT` — the build that introduced `kyo-schema` on
  the Kyo mainline. Later versions (snapshot or stable) are acceptable so long
  as they remain `>= 1.0-RC1+214-534321a9-SNAPSHOT`.
- Establish a service / layer convention that downstream code can copy.
- Add Kyo-aware visitor variants that preserve the existing visitor cursor model.
- Add a `KyoQueryableTree` extension over `QueryableTree[T]`.
- Introduce a typed `Stage[I, O, S]` abstraction in `compiler-api` so future
  pipeline work has a single composition operator to lean on.
- Wire **scribe** (`com.outr::scribe :: 3.16.1`, cross JVM/JS/Native) as the
  default logging backend, bridged to Kyo's `Log` effect via a small
  `ScribeLogHandler`. Services log through `Env[Log]`; production layers
  provide the scribe-backed handler.

## Non-Goals

- Migrating any existing call site away from pure visitors. Pure variants stay
  untouched and remain the default for callers that do not need effects.
- Replacing `purelogic` (`com.github.ghostdogpr::purelogic`). A separate decision
  doc (K-1.5, GH issue forthcoming via EPIC-1) recommends an outcome later.
- Replacing `jsoniter-scala` for the `compiler-api` envelope types. The
  `kyo-schema` codec migration (K-1.6) ships as PR-F after this PR lands.
- Changing the visitor / queryable / unist behavior observed by any existing test.

## Decision Summary

PR-A delivers seven artifacts, all behavior-preserving:

1. **Kyo deps + Sonatype snapshots resolver** in `mill-build/src/build/Modules.scala`
   so every `CommonScalaModule` (and its JS / Native peers) can depend on
   `kyo-prelude`, `kyo-core`, `kyo-direct`, `kyo-combinators`, and the future
   `kyo-schema`. The snapshot resolver is added via a `repositoriesTask` override.
2. **Convention doc** at `docs/conventions/kyo-services.md` describing how to write
   a service trait, expose it via `Env[Service]`, and provide implementations via
   `Layer[Service, Deps]`. Includes one worked example with an in-memory test impl
   and a production impl.
3. **`KyoCstVisitor` / `KyoAstVisitor` / `KyoQueryVisitor`** alongside the existing
   pure visitors. The Kyo variants accept callbacks shaped `Node => A < S` and
   propagate the effect set through traversal. They share the cursor types of the
   pure variants where possible.
4. **`KyoQueryableTree[T]` extension** giving effect-tracked equivalents of the
   pure traversal helpers on `QueryableTree[T]` (e.g. `traverseKyo`, `foldKyo`).
5. **`Stage[I, O, S]` abstraction** in `compiler-api` for composable pipeline
   stages, with `>>>` preserving type-level effect tracking.
6. **PureLogic / kyo-schema decision docs** (K-1.5, K-1.6) filed but not
   implemented in PR-A — they ship as PR-F and PR-G follow-ons.
7. **scribe logging backend + Kyo `Log` bridge.** Adds `com.outr::scribe :: 3.16.1`
   (cross JVM/JS/Native) and a `ScribeLogHandler` in `krueger.core` that consumes
   Kyo `Log` events and routes them to scribe. Convention: services declare
   `Env[Log]` dependence; production layers wire the scribe-backed handler;
   tests wire an in-memory handler.

## Module Impact

| Module                | Change                                                                  |
|-----------------------|-------------------------------------------------------------------------|
| `mill-build`          | Adds Kyo deps, scribe dep, Sonatype snapshots resolver via `repositoriesTask`. |
| `krueger.core`        | Adds Kyo-aware visitor variants in `cst` / `ast` packages; adds `ScribeLogHandler` bridging Kyo `Log` to scribe. |
| `krueger.trees`       | Adds `KyoQueryableTree` extension and `KyoQueryVisitor`.                |
| `krueger.compiler-api`| Adds `Stage[I, O, S]` abstraction.                                      |
| Other modules         | No source changes; gain Kyo + scribe on the classpath.                  |

Cross-platform coverage matches each module's existing `jvm` / `js` / `native`
matrix. Kyo modules that do not publish a Native artifact are gated behind a
platform check; affected APIs degrade gracefully (compile-time absence rather
than runtime stubs).

## Service Pattern Convention

The convention doc anchors three rules:

- **Contract first.** A service is a `trait` with effect-tracked methods,
  e.g. `trait Filesystem { def read(p: Path): Array[Byte] < (IO & Abort[IOErr]) }`.
- **Consume via `Env`.** Callers depend on `Env[Filesystem]`, never on a concrete
  impl. The compiler tracks the dependency in the effect row.
- **Provide via `Layer`.** Production code wires layers using `Layer.init`. Tests
  swap an in-memory layer in.
- **Log via `Env[Log]`.** Services that emit diagnostics declare a Kyo `Log`
  dependency. Production layers wire the scribe-backed handler; tests wire an
  in-memory recorder.

The doc includes one runnable example combining two services with logging and
showing `Env.runLayer` + `Memo.run`.

## Logging Strategy

Krueger standardizes on `com.outr::scribe :: 3.16.1` (cross JVM/JS/Native) as
its logging substrate. All in-process logging flows through Kyo's `Log` effect
to keep service signatures honest about side effects:

```
service code:                          Env[Log]      (effect-tracked)
production layer:        Log -- ScribeLogHandler -- scribe sinks
test layer:              Log -- InMemoryLogRecorder
```

`ScribeLogHandler` (in `krueger.core`) is a thin Kyo `Log` consumer that
maps level/message/context onto scribe's API. Tests can swap in a recorder
that captures emissions for assertion. CLI consumers configure scribe sinks
(stdout, file, JSON) at the outer edge; library consumers can override the
default scribe configuration via standard scribe APIs.

This separation keeps libraries free of any scribe coupling at the API level —
they speak Kyo `Log` only — while giving the CLI and other production hosts a
single, conventional logging backend.

## Visitor Variant Strategy

Pure visitors keep their existing shape and cursor API. Kyo-aware variants live
alongside them in the same package, suffixed `Kyo`:

```
io.eleven19.krueger.cst.CstVisitor          (pure, unchanged)
io.eleven19.krueger.cst.KyoCstVisitor       (new, A < S callbacks)
io.eleven19.krueger.ast.AstVisitor          (pure, unchanged)
io.eleven19.krueger.ast.KyoAstVisitor       (new)
io.eleven19.krueger.trees.query.QueryVisitor    (pure, unchanged)
io.eleven19.krueger.trees.query.KyoQueryVisitor (new)
```

Cursor types are shared. The Kyo variants reuse the pure cursor where the
operation is pure (parent / child / sibling navigation) and offer effect-tracked
overloads only where useful (e.g. callback dispatch). Traversal order matches
the pure variant for identical inputs.

## `KyoQueryableTree[T]` Extension

Lives next to `QueryableTree[T]` in `krueger.trees`. Provides:

- `traverseKyo[A, S](tree: T)(f: T => A < S): A < S`
- `foldKyo[A, S](tree: T, z: A)(f: (A, T) => A < S): A < S`
- additional helpers as need arises during PR-B / PR-C.

Pure `QueryableTree[T]` helpers stay as-is.

## `Stage[I, O, S]` Abstraction

```scala
trait Stage[-I, +O, S]:
    def run(input: I): O < S
    final def >>>[O2, S2](next: Stage[O, O2, S2]): Stage[I, O2, S & S2] = ...
```

Composition preserves the union of effect rows. PR-A ships the abstraction with
two trivial Stages (`identity`, `pure`) and tests for composition. Real stages
arrive in EPIC-6 (parser pipeline) and PR-D (CLI subcommand pipelines).

## Build Wiring

- New repository in `CommonScalaModule.repositoriesTask`:
  `https://oss.sonatype.org/content/repositories/snapshots/`.
- Add Kyo deps to the shared `mvnDeps` of every module touched. Native-incompatible
  modules are added only on `jvm` / `js` variants.
- Mill build remains 1.1.5; Scala 3.8.3; no compiler-options changes.

## Test Matrix

Per the project's mandatory testing-depth rule:

- **Happy path.** Modules build and test on JVM, JS, and Native (where available).
  Existing `core` / `trees` / `compiler-api` / `unist` tests stay green.
- **Kyo-aware visitors.** New zio-test specs run the Kyo variants over
  representative CST + AST trees with both `IO` and `Abort` callbacks.
- **`KyoQueryableTree`.** Specs round-trip `traverseKyo` / `foldKyo` with effects.
- **`Stage` composition.** Specs verify `>>>` preserves effect rows in the type
  position via compile-time checks (use `summon[Stage[A, C, S1 & S2]]`).
- **Scribe bridge.** Spec verifies `ScribeLogHandler` routes Kyo `Log.info` /
  `Log.warn` / `Log.error` emissions into scribe; in-memory recorder asserts
  level + message + context fields are preserved.
- **Negative.** A failing `Abort.fail` in a Kyo visitor short-circuits cleanly
  with a stable diagnostic; misuse of `Stage` (incompatible types) fails at
  compile time.
- **Edge / boundary.** Empty trees, single-node trees, deeply nested trees,
  cursor at root / leaf boundaries. Service layer with multiple deps composes
  via `Layer.init`.
- **Regression.** Pre-existing parser, query, queryable, and unist projection
  suites pass unchanged.

## Diagnostics and Determinism

- Service layer initialization order is deterministic (`Memo`-backed where
  applicable).
- Kyo `Abort` failures carry stable, actionable text including the failing node
  type when emitted from a visitor.
- Kyo-aware visitor traversal order matches the pure variant byte-for-byte for
  identical inputs.

## Risks and Mitigations

- **Snapshot churn.** The named SNAPSHOT can be re-published. Mitigate by
  treating `1.0-RC1+214-534321a9-SNAPSHOT` as a **minimum** rather than a pin —
  callers may upgrade to a later snapshot or stable release any time. The lower
  bound exists because that build introduced `kyo-schema` on mainline; below it
  the codec layer (K-1.6, PR-F) cannot ship.
- **Kyo cross-platform coverage gaps.** Some Kyo modules do not target Native.
  Mitigate by gating Native-only modules to the subset Kyo supports and
  documenting the matrix in the convention doc.
- **Service-pattern adoption drift.** Two patterns coexisting (visitor vs
  service) could sprawl. Mitigate by limiting PR-A to scaffolding + the
  convention doc; downstream PRs apply the conventions in concrete features.
- **No upstream `kyo-scribe` bridge.** Maven Central shows no published
  `kyo-scribe` artifact, so PR-A ships a small in-house `ScribeLogHandler`
  in `krueger.core`. Kept narrow (one file, ~50 LOC of glue) so an upstream
  bridge can replace it later with minimal churn.

## Out of Scope (Tracked Elsewhere)

- **K-1.5** — PureLogic → Kyo migration decision (GH issue under EPIC-1, ships as
  PR-G if approved).
- **K-1.6** — `kyo-schema` codec layer for `compiler-api` envelope types (ships
  as PR-F).
- **EPIC-2..EPIC-6** — Sexp, testkit, CLI, integration + docs, parser cleanup.
  Each gets its own spec → plan → PR cycle.

## Acceptance

PR-A is mergeable when:

1. Mill build succeeds on JVM, JS, and Native targets supported by Kyo.
2. All pre-existing test suites stay green.
3. New specs cover Kyo-aware visitors, `KyoQueryableTree`, `Stage`
   composition, and the `ScribeLogHandler` bridge.
4. `docs/conventions/kyo-services.md` includes a runnable worked example,
   showing both layered services and `Env[Log]` use with a scribe-backed
   production layer and an in-memory test layer.
5. CI pipeline updated for the Sonatype snapshots resolver (no new secret
   needed; snapshots are public).
