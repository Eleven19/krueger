# Playground Design

## Context

The `/try/` playground already exposes Krueger’s CST, AST, unist tree, and query machinery, but the current layout does not make the tool’s purpose obvious. The activity rail uses symbols that rely on browser tooltips for meaning, the query view competes with tree exploration instead of feeling like a guided extension of it, and panel sizing is rigid enough that the experience can feel cramped or unbalanced. The result is that users have to infer the playground’s mental model instead of being led through it.

The playground’s primary job is to help users see how Krueger produces syntax trees for the languages and DSLs it supports, including the query language itself. The first redesign should make tree exploration the unmistakable default workflow, then roll richer query capabilities in on top of that foundation. The workspace should also move toward an IDE-style layout with a bottom utility panel and a command surface that can grow from example loading into broader actions over time.

## EARS Requirements

- REQ-playground-001 (When): When a user opens `/try/`, the system shall present a tree-first workspace where the source editor and the active syntax-tree explorer are visible together without requiring a panel switch.
- REQ-playground-002 (If): If a user changes the active explorer mode, then the system shall keep the workspace structure stable and only swap the relevant explorer content or inspector detail instead of reshuffling the whole layout.
- REQ-playground-003 (When): When a user invokes the command surface, the system shall offer curated examples and GitHub-backed import flows as first-class actions.
- REQ-playground-004 (Where): Where example-loading actions are offered in the command surface, the system shall also expose equivalent explorer-local tool buttons for discoverability.
- REQ-playground-005 (If): If a GitHub repo, file import, or compiler action fails, then the system shall emit actionable diagnostics in `Problems` and supporting detail in `Logs`.
- REQ-playground-006 (When): When a user selects a node in the tree explorer, the system shall surface structured selection context in the right-side inspector.
- REQ-playground-007 (Where): Where the layout is constrained by narrow viewport width or resized panes, the system shall preserve usable panel proportions without hiding the primary source-plus-tree workflow.
- REQ-playground-008 (When): When query tooling is introduced or expanded, the system shall present it as an incremental extension of tree exploration rather than as an equally competing default mode.
- REQ-playground-009 (If): If the command surface cannot complete an example or import action, then the system shall leave the current source content intact and report the failure clearly.

## Approaches Considered

Three layout directions were considered:

- Explorer-centered IDE: source editor and active tree share the center stage, with a right inspector and bottom utility panel.
- Inspector-centered studio: source and tree stack in the center while a larger right workbench carries examples, selection, and query tools.
- Guided workbench: the layout gives more persistent space to onboarding and action suggestions, with the tree sharing attention with helper surfaces.

The selected direction is the explorer-centered IDE. It best matches the product goal because it makes the source-to-tree relationship obvious at a glance, introduces a VS Code-like bottom panel without overcrowding the first release, and gives future query assistance a natural home in the inspector without turning the initial experience into a split-brain workbench.

## Architecture

The redesigned playground uses four primary regions:

- Header command surface: replaces the centered “Try Krueger” text with a command-style input. In the first increment it focuses on curated examples and GitHub-backed imports, with room to grow into broader actions later.
- Activity rail: keeps high-level mode switching, but uses clearer iconography, explicit tooltips, and labels that reflect user intent such as `CST`, `AST`, `Matches`, and `Settings`.
- Center workspace: shows the editable source and the active syntax-tree explorer side by side. This is the primary path through the tool.
- Right inspector: shows selection details, node metadata, and future query-assist affordances tied to the currently focused tree node.
- Bottom utility panel: starts as a tabbed surface with `Logs` and `Problems`, with the structure intentionally sized for future tabs such as richer output or terminal-like capabilities.

This layout should feel closer to a familiar editor workbench than to a stack of independent result panels. The important shift is that the tree explorer becomes the main visual output, while everything else supports understanding or acting on that tree.

## Interaction Model

The first rollout is tree-first. A user edits or loads source, the center explorer shows the corresponding CST or AST, and the inspector provides structured context for the current selection. Query functionality still exists, but it becomes a secondary tool that hangs off the tree exploration workflow instead of trying to define the whole workspace.

Examples and imports are intentionally available in two places:

- The header command surface for keyboard-first, command-palette-style access.
- Explorer-local tool buttons for direct discoverability near the output that users are trying to influence.

Both entry points should lead into the same underlying flows so they reinforce one mental model. “Load example” and “Import from GitHub” are content-loading actions, not separate modes. After content is loaded, the user remains in the same source-plus-tree workspace.

GitHub-backed import should be repo-aware but guided. A user can paste a repo URL or file URL, resolve a supported file list, choose a file, and load its contents into the source editor. Import failures should not clear or replace the current source buffer; they should route into `Problems` and `Logs` with stable, actionable messages.

## Incremental Capability Rollout

The redesign should explicitly stage capability growth:

### Phase 1: Tree-first workspace

- Introduce the new center workspace, right inspector, and bottom `Logs` / `Problems` panel.
- Replace the centered header title with the command surface.
- Support curated examples from both the command surface and explorer tool buttons.
- Keep query tools available, but position them as secondary to CST/AST exploration.

### Phase 2: Guided import and richer selection context

- Add GitHub repo and file import flows to the command surface and explorer tool buttons.
- Enrich the inspector with node spans, field names, child metadata, and selection summaries.
- Improve cross-panel coordination so diagnostics and import status appear consistently in the utility panel.

### Phase 3: Linked query exploration

- Add stronger coordination between tree selection and query tooling.
- Support suggested or scaffolded query shapes from the selected node.
- Let query results focus or highlight corresponding nodes in the tree explorer.

This sequencing keeps the product understandable. Users first learn that Krueger turns source into structure, then learn how to load more interesting inputs, and only then get advanced query-assist behaviors.

## Effect Framework Usage

The redesign should feel free to use the `effect` framework for TypeScript-side orchestration. The playground now has multiple effectful capabilities that benefit from a shared model:

- compiler calls and backend switching,
- curated example lookup and loading,
- GitHub repo and file import flows,
- command-surface action dispatch,
- diagnostic and log collection,
- future browser or node-backed utility capabilities.

Using `effect` services and typed error channels should reduce the amount of ad hoc promise handling and component-local error plumbing in Svelte files. It also gives the UI a cleaner way to funnel failures into `Logs` and `Problems` consistently. This is especially important if the playground grows toward more advanced import, output, and browser-facing capabilities over time.

## Components

The first increment is likely to touch these areas:

- `sites/try-wasm/src/routes/+page.svelte` for overall workspace composition and layout state.
- `sites/try-wasm/src/lib/components/SiteHeader.svelte` for the command-surface replacement.
- `sites/try-wasm/src/lib/components/ActivityBar.svelte` for icon clarity, tooltips, and revised semantics.
- `sites/try-wasm/src/lib/components/EditorGroup.svelte` or successor layout components for the center workspace split.
- `sites/try-wasm/src/lib/components/ResultsPanel.svelte` and related view components to shift from a monolithic right panel to explorer, inspector, and bottom-panel responsibilities.
- New components for the right inspector and bottom utility tabs.
- TypeScript-side service modules for examples, imports, diagnostics, and action dispatch, potentially backed by `effect`.

The component boundary goal is straightforward: layout orchestration belongs in top-level workspace components, while effectful content-loading and diagnostics flows live in explicit service layers rather than in scattered UI callbacks.

## Data Flow

1. A user edits source or loads content through the command surface or explorer tool buttons.
2. The playground updates the source editor while preserving the active workspace mode.
3. The selected explorer mode requests the relevant CST, AST, or query-backed view data.
4. The center explorer renders the active tree output.
5. Tree selection updates the right inspector with node details and selection context.
6. Compiler logs, import status, and failures stream into the bottom utility panel.
7. Optional query actions read from the same shared source and selection state rather than creating a separate navigation path.

## Error Handling

The redesign should make diagnostics first-class rather than incidental:

- `Problems` receives user-actionable failures such as parse errors, unsupported imports, network failures, and invalid GitHub targets.
- `Logs` receives supporting detail such as import progress, backend transitions, capability checks, and lower-level runtime notes.
- Content-loading failures must not silently blank the editor or reset the workspace.
- Import and command-surface errors should preserve the current source content unless the user explicitly confirms replacement after a successful load.

Diagnostics should be stable and named in a way that helps users understand whether the failure came from Krueger parsing, GitHub import, network access, or browser/runtime capability checks.

## Testing

The implementation should follow strict red-green-refactor. Coverage for the first rollout should include:

- Happy path: the default workspace renders source and active tree together, with a visible `Logs` / `Problems` utility panel.
- Happy path: curated examples can be loaded from both the command surface and explorer tool buttons.
- Negative path: invalid example actions or failed GitHub imports report stable diagnostics without clobbering existing source content.
- Negative path: compiler parse failures appear in `Problems` and supporting detail appears in `Logs`.
- Boundary path: constrained panel sizes and resizing behavior preserve usable source-plus-tree visibility.
- Regression path: existing CST, AST, and query rendering remain available after the layout migration.
- Integration path: command-surface actions, explorer tool buttons, and diagnostic plumbing operate consistently across supported backends.
- End-to-end path: a user can load an example, view tree output, observe logs/problems, and switch explorer modes without losing context.

## Open Decisions Carried Forward

The design intentionally leaves a few details for the implementation plan:

- The exact visual language for the activity-rail icons and whether labels should appear on hover only or in a compact adjacent strip.
- The exact resizing model for the center explorer, inspector, and utility panel.
- The initial scope of GitHub repo browsing and any guardrails around supported file types, size limits, or fetch strategy.
- The exact shape of the command-surface UI once it grows beyond examples and imports.

These decisions should be resolved in the implementation plan without changing the approved product direction of a tree-first, explorer-centered IDE workspace.
