# Cursor Handoff: PR-A Kyo Foundation, Resume at Task 3

You are resuming an in-flight implementation plan. Tasks 1-2 are committed. Tasks 3-9 remain.

## Working environment

- **Repo:** Krueger (Scala 3.8.3 / Mill 1.1.5, cross JVM/JS/Native, Elm tooling)
- **Worktree:** `/Users/damian/code/github/Eleven19/krueger/.claude/worktrees/wonderful-chandrasekhar-6d5277`
- **Branch:** `claude/wonderful-chandrasekhar-6d5277`
- **Latest commit:** `2caa7cc` — "feat(compiler-api): add Stage[I, O, S] composable pipeline abstraction"

## What's already landed in this branch

- 6 GitHub epics filed: [#24](https://github.com/Eleven19/krueger/issues/24)..[#30](https://github.com/Eleven19/krueger/issues/30) + ops issue [#31](https://github.com/Eleven19/krueger/issues/31)
- 111 bd issues (prefix `musing-chaum-7e7242` — migration to `krueger` prefix tracked under #31; bd `create` is currently broken, use `bd update` only)
- Spec at `docs/superpowers/specs/2026-04-25-pr-a-kyo-foundation-design.md` (approved by user)
- Plan at `docs/superpowers/plans/2026-04-25-pr-a-kyo-foundation.md` — your task list
- Conventions in `CLAUDE.md` / `AGENTS.md` (TDD RGR mandatory, EARS, value-unlock docs, beads multi-worktree)
- Convention doc `docs/conventions/beads-multi-worktree.md`
- **Task 1** (commit `4341fed`): Kyo + scribe deps + Sonatype Central snapshots resolver wired into `mill-build/src/build/Modules.scala` and the three `package.mill` files
- **Task 2** (commit `2caa7cc`): `Stage[I, O, S]` in `compiler-api`

## What to do

**Continue executing the plan from Task 3.** The plan has 9 numbered tasks. Tasks 1-2 are done. Run Tasks 3-9 in order. After Task 9, follow the plan's verification + push steps.

For each remaining task:
1. Read the full task text in `docs/superpowers/plans/2026-04-25-pr-a-kyo-foundation.md`
2. TDD: write failing test → verify failure → minimal impl → verify pass → cross-platform compile → commit
3. Move on. No need for human gating unless something deviates from the plan.

## Critical Kyo API note from Task 2

The plan uses `IO`, `IO.Unsafe.run(_)(using AllowUnsafe.embrace.danger).eval`. The resolved Kyo SNAPSHOT renamed `IO` → `Sync` (with `IO` still aliased but `@deprecated`). Adapt every test:

- For `A < Any` pure programs, use `program.eval` directly — no unsafe runner needed.
- For effectful programs (`A < (IO & ...)`, `A < (Sync & ...)`), use `Sync.Unsafe.run(program)(using AllowUnsafe.embrace.danger).eval`.
- Replace `IO(...)` with `Sync(...)` in test bodies.
- The `kyo.<` infix type and `.map` extension are unchanged.

## Mandatory rules (from CLAUDE.md / AGENTS.md)

- **TDD Red-Green-Refactor.** No production code before a failing test.
- **Test depth.** Happy path + negative + edge + regression for each behavior change.
- **EARS-style requirements** in any new issue authoring.
- **Value-Unlock Documentation.** Every user-facing feature needs cookbook recipe — PR-A is foundation, no user-facing features here, but keep the convention in mind for downstream PRs.
- **Beads multi-worktree.** `bd dolt pull` at session start, `bd dolt push` at session end. `bd create` is currently broken — use `bd update` only. Prefix is `musing-chaum-7e7242` for now (migration to `krueger` tracked separately).
- **No `Co-Authored-By: Claude` trailer in commit messages.** Override of the default.
- **Use `bd` for task tracking, NOT TodoWrite or markdown TODO lists.** The plan's checkboxes are exempt because they were approved by the user.

## Tasks 3-9 quick map

3. **`ScribeLogHandler` + `InMemoryLogRecorder` + `ScribeLogLayer`** in `krueger/core/src/io/eleven19/krueger/log/`. Bridges Kyo `Log` to scribe (production) or in-memory recorder (tests). `Log` API may have shifted on the SNAPSHOT — adapt the handler to whatever level/method shape the resolved Kyo exposes, but keep three deliverables (handler, layer, recorder).
4. **`KyoQueryableTree`** in `krueger/trees/src/io/eleven19/krueger/trees/`. `traverseKyo` + `foldKyo` over `QueryableTree[T]`. Pre-order, matching `UnistProjection`.
5. **`KyoCstVisitor`** in `krueger/core/src/io/eleven19/krueger/cst/`. Delegates to `KyoQueryableTree`. Pure `CstVisitor` stays unchanged.
6. **`KyoAstVisitor`** in `krueger/core/src/io/eleven19/krueger/ast/`. Same shape as KyoCstVisitor.
7. **`KyoQueryVisitor`** in `krueger/trees/src/io/eleven19/krueger/trees/query/`. Walks the query-AST `Pattern` tree. Read pure `QueryVisitor` first to mirror `children` accessor and signature.
8. **`docs/conventions/kyo-services.md`** — convention doc with worked Counter example showing `Env[Service]` + `Layer.init` + `Env[Log]` (production scribe layer + in-memory test layer).
9. **Final verification + draft PR.** Full test sweep on JVM, cross-platform compile, re-enable `bd config set export.auto true`, `bd dolt push`, `bd close` the K-1.* issues, push branch, open draft PR titled "PR-A: Kyo foundation". Do NOT push without explicit user confirmation if not in an autonomous mode.

## Code-quality follow-ups for K-1.6 (do not block PR-A)

The Task 1 code-quality reviewer flagged three medium follow-ups to file as bd tasks once `bd create` is restored:

1. Extract `KruegerDeps.kyoCore: Seq[Dep]` + `KruegerDeps.scribe: Dep` helpers to `mill-build/src/build/Modules.scala`; refactor `package.mill` files to consume them. Should land before K-1.6 (kyo-schema) to prevent shotgun-surgery.
2. Confirm Coursier snapshot resolution policy under Mill 1.1.5; document the chosen ordering with a comment.
3. (Optional, on next significant addition) split `Modules.scala` into `Versions.scala` + `Modules.scala`.

Note these in your final PR description as "follow-up issues to file" rather than fixing them in PR-A.

## Files / locations cheat sheet

- Spec: `docs/superpowers/specs/2026-04-25-pr-a-kyo-foundation-design.md`
- Plan: `docs/superpowers/plans/2026-04-25-pr-a-kyo-foundation.md`
- This handoff: `docs/superpowers/handoffs/2026-04-25-pr-a-kyo-foundation-cursor-handoff.md`
- Mill versions object: `mill-build/src/build/Modules.scala` (see `KruegerVersions.Kyo` and `KruegerVersions.Scribe`)
- Resolver override: same file, `repositoriesTask` on `CommonScalaModule`
- Stage trait: `krueger/compiler-api/src/io/eleven19/krueger/compiler/Stage.scala`
- StageSpec (template): `krueger/compiler-api/test/src/io/eleven19/krueger/compiler/StageSpec.scala`

## Commands you'll re-run a lot

```bash
# JVM compile + test sweep
./mill --no-server krueger.trees.jvm.test.testForked krueger.core.jvm.test.testForked krueger.compiler-api.jvm.test.testForked

# Cross-platform compile sweep
./mill --no-server krueger.trees.js.compile krueger.core.js.compile krueger.compiler-api.js.compile
./mill --no-server krueger.trees.native.compile krueger.core.native.compile krueger.compiler-api.native.compile

# Single-spec run
./mill --no-server krueger.core.jvm.test.testForked io.eleven19.krueger.log.ScribeLogHandlerSpec

# Beads
bd dolt pull             # at session start
bd ready
bd update <id> --description="..."
bd dolt push             # at session end
```

## When you finish all 9 tasks

1. Run the full test + cross-platform sweep one more time.
2. Re-enable `bd config set export.auto true`.
3. `bd dolt push`.
4. `bd close musing-chaum-7e7242-070 musing-chaum-7e7242-bvc musing-chaum-7e7242-57f musing-chaum-7e7242-dmz musing-chaum-7e7242-bie` (K-1.1, K-1.2, K-1.3, K-1.4, EPIC-1).
5. `bd dolt push` again.
6. `git push -u origin claude/wonderful-chandrasekhar-6d5277` (only if user authorizes — autonomous mode = OK).
7. `gh pr create --draft --base main --title "PR-A: Kyo foundation — deps, service pattern, Kyo-aware visitors, Stage abstraction, scribe Log bridge"` with the body template from Task 9 of the plan.

That completes PR-A. Subsequent PRs (PR-B1..PR-D2, EPIC-2..EPIC-6, plus EPIC-7 release pipeline) are tracked in GitHub issues and will be planned separately when their epics open.

Good luck.
