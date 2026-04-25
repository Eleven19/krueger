# Agent Instructions

This project uses **bd** (beads) for issue tracking. Run `bd prime` for full workflow context.

## TDD Red-Green-Refactor (Mandatory)

- For any behavior change or bug fix, follow strict **Red-Green-Refactor**.
- **Red first:** write/modify tests to express the desired behavior *before* changing production code, then run tests and confirm they fail for the expected reason.
- **Green second:** make the minimal production code change needed to pass the new failing tests.
- **Refactor third:** clean up code/tests while keeping all tests green.
- Do not implement production code ahead of tests unless the user explicitly asks to skip TDD.
- If code was written before tests by mistake, perform a strict reset of those changes and restart with Red first.

## Testing Depth and Edge Cases (Mandatory)

- Do not treat a feature as done based on a single happy-path test.
- For behavior changes, include tests that cover:
  - happy path(s),
  - failure/validation path(s),
  - boundary/edge cases,
  - regression cases for previously reported bugs.
- Prefer both unit-level and integration/BDD coverage when user-facing behavior changes.
- Add deterministic-order assertions where ordering is part of behavior.
- Before closing an issue, verify that tests exercise the full issue acceptance criteria, including edge cases explicitly mentioned in the issue description.

## Acceptance Criteria Clarity (Mandatory)

- Write issue acceptance criteria using EARS-style requirements whenever practical:
  - `When <trigger>, the system shall <response>.`
  - `If <precondition>, then the system shall <response>.`
  - `While <state>, the system shall <response>.`
  - `Where <variant>, the system shall <response>.`
- For every non-trivial change, include at least:
  - one success requirement,
  - one negative/failure requirement,
  - one boundary/edge requirement.
- Feature scenarios and tests should map clearly back to acceptance criteria requirements.

## Issue Authoring Template (Mandatory)

- Use the following reusable template for all new issues (gap work and retrofit work):

```text
Context:
- <what gap/behavior/problem is being addressed and why>

EARS requirements:
- REQ-<slug>-001 (When): When <trigger>, the system shall <response>.
- REQ-<slug>-002 (If): If <precondition>, then the system shall <response>.
- REQ-<slug>-003 (Where/While): Where/While <variant/state>, the system shall <response>.

Acceptance:
1) Implementation expectations:
   - <parser/matcher/runtime/docs changes expected>
2) Test matrix (mandatory):
   - happy-path coverage
   - negative/failure coverage
   - edge/boundary coverage
   - regression coverage
3) User-visible behavior:
   - if user-visible, require both unit + BDD/integration assertions
4) Determinism and diagnostics:
   - assert ordering determinism where observable
   - assert stable, actionable diagnostics for failures
```

- Apply this template to:
  - new feature/gap issues,
  - retrofit/backfill issues,
  - process/docs issues that change expected engineering workflow.

## BDD Step Language Expansion (Mandatory)

- When existing Gherkin steps cannot express a new behavior clearly, expand the step language.
- New or updated step phrases must be verified by:
  - at least one positive scenario,
  - at least one negative scenario,
  - at least one edge/boundary scenario when applicable.
- Prefer composable reusable steps; add highly specialized steps only when readability or precision requires them.

## Fixture Rigor and Dogfooding (Mandatory)

- Do not rely only on minimal toy fixtures for end-state confidence.
- Use a layered fixture strategy as appropriate:
  - toy fixtures for local invariants,
  - richer synthetic syntax-tree fixtures for interaction coverage,
  - real CST/AST fixtures for integration behavior,
  - dogfooded query-AST fixtures when validating query-language internals.
- For parser/lifting/query behavior, include CST and AST verification where behavior is meant to be cross-tree.

## Value-Unlock Documentation (Mandatory)

- Every user-facing feature ships with use-case-based docs that demonstrate the value to a developer integrating Krueger. A feature without these docs is not complete and shall not merge.
- Required surfaces for each user-facing feature:
  - **Why this exists** — one paragraph in plain language, no jargon, answers "what problem does this solve for me."
  - **Use it from your code** — runnable, copy-pasteable code snippet that compiles against the published artifact.
  - **Compose with other Krueger features** — at least one cross-feature example (e.g., parse + query, run testkit + custom reporter, CLI + library co-use).
  - **Real-world recipe** — a full mini use case showing intent → integration → outcome (parse → transform → emit, query → analyze, run a corpus → report failures, etc.).
- Convention applies to libraries (`core`, `trees`, `testkit`), the CLI, and the playground.
- The PR description shall link to the new or updated docs page; reviewers shall block on missing or vague value-unlock content.

## Retroactive Coverage Upgrades (Mandatory)

- Apply this rigor to existing features, not only newly added ones.
- File and track dedicated retrofit issues for existing behavior that lacks sufficient negative/edge/regression coverage.
- Keep existing happy-path coverage green while adding retrofit tests.

## Quick Reference

```bash
bd ready              # Find available work
bd show <id>          # View issue details
bd update <id> --claim  # Claim work atomically
bd close <id>         # Complete work
bd dolt push          # Push beads data to remote
```

## Non-Interactive Shell Commands

**ALWAYS use non-interactive flags** with file operations to avoid hanging on confirmation prompts.

Shell commands like `cp`, `mv`, and `rm` may be aliased to include `-i` (interactive) mode on some systems, causing the agent to hang indefinitely waiting for y/n input.

**Use these forms instead:**
```bash
# Force overwrite without prompting
cp -f source dest           # NOT: cp source dest
mv -f source dest           # NOT: mv source dest
rm -f file                  # NOT: rm file

# For recursive operations
rm -rf directory            # NOT: rm -r directory
cp -rf source dest          # NOT: cp -r source dest
```

**Other commands that may prompt:**
- `scp` - use `-o BatchMode=yes` for non-interactive
- `ssh` - use `-o BatchMode=yes` to fail instead of prompting
- `apt-get` - use `-y` flag
- `brew` - use `HOMEBREW_NO_AUTO_UPDATE=1` env var

<!-- BEGIN BEADS INTEGRATION v:1 profile:minimal hash:ca08a54f -->
## Beads Issue Tracker

This project uses **bd (beads)** for issue tracking. Run `bd prime` to see full workflow context and commands.

**Multi-worktree note (mandatory).** Krueger routinely runs multiple agents in parallel across multiple git worktrees. Read [`docs/conventions/beads-multi-worktree.md`](docs/conventions/beads-multi-worktree.md) before running any `bd create` / `bd update` / `bd dolt push` / `bd dolt pull` from an agent. Key rules: pull at session start, push at session end, never `bd init` without authorization, confirm `bd config get issue-prefix` returns `krueger` (target state) before writing.

### Quick Reference

```bash
bd ready              # Find available work
bd show <id>          # View issue details
bd update <id> --claim  # Claim work
bd close <id>         # Complete work
```

### Rules

- Use `bd` for ALL task tracking — do NOT use TodoWrite, TaskCreate, or markdown TODO lists
- Run `bd prime` for detailed command reference and session close protocol
- Use `bd remember` for persistent knowledge — do NOT use MEMORY.md files

## Session Completion

**When ending a work session**, you MUST complete ALL steps below. Work is NOT complete until `git push` succeeds.

**MANDATORY WORKFLOW:**

1. **File issues for remaining work** - Create issues for anything that needs follow-up
2. **Run quality gates** (if code changed) - Tests, linters, builds
3. **Update issue status** - Close finished work, update in-progress items
4. **PUSH TO REMOTE** - This is MANDATORY:
   ```bash
   git pull --rebase
   bd dolt push
   git push
   git status  # MUST show "up to date with origin"
   ```
5. **Clean up** - Clear stashes, prune remote branches
6. **Verify** - All changes committed AND pushed
7. **Hand off** - Provide context for next session

**CRITICAL RULES:**
- Work is NOT complete until `git push` succeeds
- NEVER stop before pushing - that leaves work stranded locally
- NEVER say "ready to push when you are" - YOU must push
- If push fails, resolve and retry until it succeeds
<!-- END BEADS INTEGRATION -->
