# PR-A: Kyo foundation — deps, service pattern, Kyo-aware visitors, Stage abstraction, scribe Log bridge

Implements EPIC-1 prerequisites (K-1.1, K-1.2, K-1.3, K-1.4) per the spec at
`docs/superpowers/specs/2026-04-25-pr-a-kyo-foundation-design.md`.

## What ships

- Kyo + scribe deps (Kyo `>= 1.0-RC1+214-534321a9-SNAPSHOT`, scribe `3.16.1`)
  with Sonatype Central snapshots resolver wired into `mill-build`.
- `Stage[I, O, S]` in `compiler-api` with effect-row-preserving composition.
- `ScribeLogHandler` + `InMemoryLogRecorder` + `ScribeLogLayer` bridging
  Kyo `Log` to scribe in production and to an in-memory recorder in tests.
- `KyoQueryableTree` extension over `QueryableTree[T]` with `traverseKyo` /
  `foldKyo`.
- `KyoCstVisitor` / `KyoAstVisitor` / `KyoQueryVisitor` Kyo-aware siblings of
  the pure visitors. Pure visitors with their cursor API are unchanged.
- `docs/conventions/kyo-services.md` worked-example doc (Counter service +
  scribe vs in-memory log layers).

## Behavior change

None. PR-A is additive — no existing test changed, no pure API touched.

## Adaptations from the spec

- Sonatype URL: spec said `oss.sonatype.org`; legacy host is sunset, so the
  resolver uses `https://central.sonatype.com/repository/maven-snapshots/` first
  with the legacy URL as a fallback.
- Kyo `IO` → `Sync`: snapshot renamed; tests use `Sync` (or `program.eval` for
  pure `< Any` programs).
- `Log` is installed via `Log.let(handler)(...)` rather than `Env.runLayer` —
  matches the actual Kyo API surface.

## Tracks

- GitHub: #24 (EPIC-1)
- bd: `musing-chaum-7e7242-bie`

## Test plan

- [x] `./mill krueger.trees.jvm.test.testForked` green
- [x] `./mill krueger.core.jvm.test.testForked` green
- [x] `./mill krueger.compiler-api.jvm.test.testForked` green
- [x] JVM + JS + Native compile sweep green
- [x] webapp-wasm + compiler-api.wasm linkers still produce artifacts

## Follow-ups (filed as bd tasks for K-1.6 / EPIC-1 close-out)

1. Extract `KruegerDeps.kyoCore` / `KruegerDeps.scribe` helpers so future Kyo
   modules don't shotgun-surgery three `package.mill` files. Should land
   before K-1.6 (kyo-schema).
2. Confirm Coursier snapshot resolution policy under Mill 1.1.5; document the
   chosen resolver ordering with a comment.
3. (Optional) split `Modules.scala` into `Versions.scala` + `Modules.scala`
   on next significant addition.
