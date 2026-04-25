# Beads Across Multiple Worktrees

This convention exists because Krueger uses `bd` (beads) as the canonical
issue tracker, and the project routinely runs multiple agents in parallel
across multiple git worktrees. Without explicit guidance, the default
beads + Dolt setup creates per-worktree databases with worktree-derived
names (e.g., `musing-chaum-7e7242`), which leads to:

- Each worktree sees a different bd state.
- `bd dolt push` and `bd dolt pull` can collide or sync the wrong database.
- Issue prefix tied to the worktree directory name (e.g.,
  `musing-chaum-7e7242-070`) instead of a stable project prefix
  (`krueger-070`).
- `bd create` intermittently fails with `database not initialized:
  issue_prefix config is missing` after switching worktrees or restarting
  the Dolt server.
- Subagents launched from different worktrees write to different DBs and
  the user has to reconcile by hand.

## Target State (proposed)

- **Canonical issue prefix is `krueger`**, project-wide. New issues are
  `krueger-<id>` regardless of which worktree creates them.
- **All worktrees share one Dolt database** (`krueger`) via a single Dolt
  server running at the project root, OR via the canonical Dolt remote
  (`git+ssh://git@github.com/Eleven19/krueger.git`) with explicit
  `bd dolt pull` / `push` discipline at session boundaries.
- **Per-worktree `.beads/config.yaml`** sets `issue-prefix: krueger` and
  points at the same `database: krueger` name.
- **The current `musing-chaum-7e7242-*` issues are migrated** to
  `krueger-*` IDs (see Migration below).

## Rules for Agents Working in Any Worktree

> These rules are mandatory. Agents that do not follow them risk creating
> divergent bd state across worktrees.

1. **At session start.** Run `bd dolt pull` once before reading or
   writing issues. If the pull fails, run `bd doctor` and stop; do not
   proceed with stale state.
2. **Before any `bd create`, `bd update`, `bd close`, or `bd dep add`.**
   Confirm `bd config get issue-prefix` returns `krueger`. If it returns
   something else (e.g., `musing-chaum-7e7242`), stop and surface to the
   user — the worktree was bootstrapped before the migration and needs
   re-init.
3. **At session end.** Run `bd dolt push` once. Do not push from a
   subagent if the parent agent will push too — duplicate pushes can
   produce confusing audit logs.
4. **Subagents.** When dispatching a subagent that will touch bd, name
   the worktree explicitly in the prompt and instruct the subagent to
   `bd dolt pull` first and `bd dolt push` last. Subagents must NOT run
   `bd dolt restart` or `bd init` without explicit user authorization.
5. **Multi-worktree concurrency.** Treat bd writes from a different
   worktree as a remote source of truth. If two agents in different
   worktrees both push, the second push wins for conflicting fields and
   the loser must `bd dolt pull` to reconcile. Avoid concurrent edits
   to the same issue across worktrees.

## Migration Path (musing-chaum-7e7242-* → krueger-*)

This is a one-time rename. Tracked as a follow-up issue and PR.

- **Step 1.** Cut a backup: `bd export > .beads/backup-pre-migration.jsonl`.
  Commit the backup to the repo so the rename is reversible.
- **Step 2.** Stop all per-worktree Dolt servers: `bd dolt stop` from
  every active worktree.
- **Step 3.** Re-init the canonical project DB at the repo root with the
  stable prefix:
  ```bash
  bd init --prefix krueger --database krueger --non-interactive --force
  ```
  Use `--force` only after the backup is committed.
- **Step 4.** Re-import issues with prefix rewrite (a small script that
  reads `backup-pre-migration.jsonl` and rewrites every `id` and
  `depends_on_id` from `musing-chaum-7e7242-*` to `krueger-*`, preserving
  the trailing slug).
- **Step 5.** Re-import via `bd import < migrated.jsonl`.
- **Step 6.** Update every worktree's `.beads/config.yaml` to set
  `issue-prefix: krueger` and `database: krueger`.
- **Step 7.** Rebroadcast the new IDs: update GH issues that reference
  the old IDs (sub-task lists in EPIC-1..EPIC-6 bodies).
- **Step 8.** Document the new state in the project README + this file's
  status section.

## Status

- **Current**: prefix is `musing-chaum-7e7242` (auto-derived from a
  worktree directory). 111 issues exist under that prefix.
- **Target**: prefix `krueger`. Migration tracked as a separate task.
- **Open known issue**: `bd create` fails with
  `database not initialized: issue_prefix config is missing` when the
  shared server is restarted or when a new worktree is added without
  re-running `bd init`. Workaround: use `bd update` against existing
  issues, or dispatch a subagent that has the prefix in its environment.
- **Recovery path**: `bd init --external --prefix krueger --database
  <existing-db>` re-registers the prefix at the DB layer without
  destroying issue data. Tested on a non-production DB before applying
  to the project DB.

## Why a Single Project Prefix Matters

- **Cross-worktree references stay stable.** A bd ID written in a PR
  description, design doc, or commit message must resolve no matter
  which worktree the reader is in.
- **Subagent prompts are reusable.** "Update `krueger-070`" works
  everywhere; "Update `musing-chaum-7e7242-070`" only works in one
  worktree.
- **Less DB confusion.** One prefix = one DB to back up, one remote to
  sync against, one place to look when something is missing.
