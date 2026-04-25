# Project Instructions for AI Agents

This file provides instructions and context for AI coding agents working on this project.

## TDD Red-Green-Refactor (Mandatory)

- Use strict **Red-Green-Refactor** for all functional changes.
- **Red:** author or update tests first; run them and verify failure for the intended reason.
- **Green:** implement the smallest production change that makes the new tests pass.
- **Refactor:** improve structure/readability only after green, with full test suite still passing.
- Do not write production code before tests unless the user explicitly requests a non-TDD approach.
- If this order is violated, reset the premature code changes and restart from Red.

## Testing Coverage and Edge Cases (Mandatory)

- A single happy-path test is insufficient for non-trivial features.
- For each behavior change, author tests for:
  - nominal success behavior,
  - validation/error behavior,
  - edge/boundary behavior,
  - regression scenarios tied to known issues.
- When behavior is externally visible, include both unit tests and higher-level integration/BDD scenarios.
- If ordering or determinism matters, assert order explicitly in tests.
- Do not mark work complete until acceptance criteria and key edge cases are covered by tests.

## Acceptance Criteria Clarity (Mandatory)

- Express acceptance criteria in EARS-style requirements whenever practical:
  - `When <trigger>, the system shall <response>.`
  - `If <precondition>, then the system shall <response>.`
  - `While <state>, the system shall <response>.`
  - `Where <variant>, the system shall <response>.`
- Require at least one success, one negative/failure, and one edge/boundary requirement for non-trivial work.
- Ensure tests and feature scenarios can be traced back to those requirements.

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

- Expand Gherkin step language when existing steps are insufficient to clearly express new behavior.
- Any new or modified step phrase must be validated by:
  - a positive scenario,
  - a negative scenario,
  - an edge/boundary scenario where relevant.
- Prefer composable generic steps and avoid one-off specialized steps unless clarity requires them.

## Fixture Rigor and Dogfooding (Mandatory)

- Use more than minimal toy fixtures when validating behavior with meaningful interactions.
- Prefer a layered fixture approach:
  - toy fixtures for narrow unit invariants,
  - richer synthetic syntax-tree fixtures for semantics interactions,
  - real Elm CST/AST fixtures for integration behavior,
  - dogfooded query-AST fixtures when validating query language internals.
- For cross-tree behavior, verify both CST and AST paths.

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

- Apply this testing rigor to existing implemented features, not only new gaps.
- Track retrofit coverage as explicit issues and complete them through standard RGR cycles.
- Preserve existing green happy-path coverage while adding negative, edge, and regression tests.

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


## Build & Test

_Add your build and test commands here_

```bash
# Example:
# npm install
# npm test
```

## Architecture Overview

_Add a brief overview of your project architecture_

## Conventions & Patterns

_Add your project-specific conventions here_
