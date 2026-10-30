# OpenSpec changes and HTML designs

OpenSpec keeps a change's motivation, behavior requirements and design together
so a contributor can review the approach before implementation. Krueger uses
the `html-design` workflow from
[finos/morphir-moonbit](https://github.com/finos/morphir-moonbit/tree/db639506585e38326810722d4951e71f43c6c4f8/openspec/schemas/html-design).
Its design is an HTML review page with a table of contents, local styles and
syntax highlighting. Proposals, spec deltas and task checklists stay Markdown
because OpenSpec parses those files.

## Install and check

Install the version that generated the committed agent skills:

```sh
npm install --global @fission-ai/openspec@1.14.3
openspec --version
openspec schema which html-design
openspec schema validate html-design
node --test scripts/check-openspec.mjs
```

The schema must resolve from `openspec/schemas/html-design/`, with source
`project`. `openspec/config.yaml` selects it by default. No global OpenSpec
configuration is needed to use this schema.

The committed skills support Codex, Claude Code and GitHub Copilot. Ask the agent
to use `openspec-propose`, `openspec-explore`, `openspec-apply-change`,
`openspec-update-change`, `openspec-sync-specs` or `openspec-archive-change`.
In Codex CLI, for example, invoke `$openspec-propose`; in Claude Code use
`/openspec-propose`. Restart the agent session after installation to refresh its
skill inventory. Generated skills may refer to `design.md`; this project's
schema resolves that artifact to `design.html`.

## Start a change

From the repository root:

```sh
openspec list --specs
openspec new change improve-query-diagnostics
openspec status --change improve-query-diagnostics
openspec instructions proposal --change improve-query-diagnostics
```

Write each artifact using its resolved instructions. The change directory is
`openspec/changes/improve-query-diagnostics/`:

| Artifact | File | Needs |
| --- | --- | --- |
| Proposal | `proposal.md` | Nothing |
| Behavior contracts | `specs/<capability>/spec.md` | Proposal |
| Design for review | `design.html` | Proposal |
| Implementation checklist | `tasks.md` | Specs and design |

Specs and design can be written independently after the proposal. A small
change still gets a short design page. Use the exact capability paths from
existing specs when changing their requirements. A tooling-only or docs-only
change with no behavior contract may set `skip_specs: true` in its
`.openspec.yaml`; do not invent requirements to satisfy validation.

Get the next artifact's instructions as needed:

```sh
openspec instructions specs --change improve-query-diagnostics
openspec instructions design --change improve-query-diagnostics
openspec instructions tasks --change improve-query-diagnostics
openspec validate improve-query-diagnostics --strict
```

For HTML, keep the template head and its local resources. Put content inside
`main`, give section headings IDs and include them in `nav.toc`. Use
`language-scala`, `language-elm`, `language-json`, `language-yaml` or other
installed highlight.js grammars on code blocks. Escape `<`, `>` and `&` in
text and code. Do not embed CSS, scripts or external resources. Open the design
file directly in a browser for review. The shared starter document is
`docs/templates/agent-document.html`.

## Combine OpenSpec with Beads and TDD

Beads remains the canonical tracker. Follow
[the multi-worktree convention](beads-multi-worktree.md) before tracker writes:
pull once at session start, confirm the `krueger` prefix and push at session end.
Reuse existing issues when they describe the work. New issues follow the
Context, EARS requirements and Acceptance template in `AGENTS.md`.

Name the owning epic in the proposal's `## Beads` section and the HTML design's
meta line. Link it to the change with
`bd update <id> --spec-id openspec/changes/improve-query-diagnostics`.
Each numbered group in `tasks.md` maps to a child task bead. Its heading ends
with that bead's ID. The checkboxes mirror progress; they do not replace Beads.

For example, a query diagnostics change starts with an EARS requirement for a
valid query, another for an invalid query, and one for an input boundary.
Use those requirements to write unit and BDD scenarios before changing the
parser. Run them and confirm the expected failures. Implement the smallest
change, rerun the tests, then refactor. Include diagnostic ordering assertions
where ordering is observable. Each task group ships its own tests and the
required developer use-case docs.

After implementation, run the issue's acceptance checks and OpenSpec validation.
Close each group bead with the commit range and actual test results. A reviewer
can then trace a requirement through the HTML design to a task, test and commit.

## Archive and maintain

When implementation and review are complete:

```sh
openspec archive improve-query-diagnostics --yes
```

Archiving merges spec deltas into `openspec/specs/` and moves the change under
`openspec/changes/archive/<date>-improve-query-diagnostics/`. OpenSpec does not
rewrite HTML asset links. In the archived `design.html`, replace every
`../../../docs/templates/assets/` with `../../../../docs/templates/assets/`,
then reopen the page and verify that styles and code highlighting load.
`openspec/config.yaml` includes this archive guidance.

Update the owning bead's spec ID to the archived path and close it after its
group beads are closed. Commit OpenSpec changes, specs and archive fixes.
`docs/superpowers/` artifacts stay local under the existing repository policy.

When changing shared styles or scripts, update both HTML template heads if
needed. Run `node --test scripts/check-openspec.mjs`; it exercises the actual
CLI in a temporary workspace, including invalid schemas and archive paths.
After upgrading the CLI, regenerate integrations for the same workflows:

```sh
openspec update
```

Review generated changes and rerun validation. Keep project guidance in the
schema, config and agent instruction files, rather than editing generated skills.
See [upstream provenance](../../openspec/UPSTREAM.md) for copied files and licenses.
