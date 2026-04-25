# Playground Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild `/try/` into a tree-first IDE-style workspace with a command surface, explorer-local example/import actions, a right inspector, and a bottom `Logs` / `Problems` panel.

**Architecture:** Keep `+page.svelte` responsible for top-level compiler state, but move workspace layout, command actions, diagnostics, and import/example orchestration into focused components and `src/lib/playground/*` services. Use `effect` on the TypeScript side to model content-loading and GitHub-import capabilities with typed failures that map directly into `Logs` and `Problems`.

**Tech Stack:** Svelte 5, TypeScript, Vitest, Testing Library, Playwright-backed docs smoke scripts, `effect`, Scala.js/WASM Krueger facade.

---

Start execution in a dedicated worktree before Task 1. Use the `using-git-worktrees` skill to create a worktree named `playground-redesign` (or the repo’s preferred equivalent), then run the tasks there.

## File Structure

- Create `sites/try-wasm/src/lib/playground/types.ts`
  - Shared TypeScript types for examples, command actions, diagnostics, GitHub targets, selection state, and utility tabs.
- Create `sites/try-wasm/src/lib/playground/catalog.ts`
  - Curated examples plus helper functions that expose them to both the command surface and the explorer toolbar.
- Create `sites/try-wasm/src/lib/playground/catalog.test.ts`
  - Tests deterministic example ordering, language coverage, and command/action metadata.
- Create `sites/try-wasm/src/lib/playground/actions.ts`
  - `effect`-backed example-loading and command dispatch helpers.
- Create `sites/try-wasm/src/lib/playground/actions.test.ts`
  - Tests example-loading effects and failure mapping into diagnostics.
- Create `sites/try-wasm/src/lib/playground/github.ts`
  - GitHub URL parsing, repo browsing, and file import effects.
- Create `sites/try-wasm/src/lib/playground/github.test.ts`
  - Tests supported URL shapes, API request construction, and failure behavior.
- Create `sites/try-wasm/src/lib/components/CommandSurface.svelte`
  - Header command box for examples, imports, and future commands.
- Create `sites/try-wasm/src/lib/components/PaneResizeHandle.svelte`
  - Accessible keyboard/mouse splitter used by the center workspace and bottom panel.
- Create `sites/try-wasm/src/lib/components/ExplorerPane.svelte`
  - Replaces `ResultsPanel.svelte` as the main center explorer surface for CST/AST/matches/settings content.
- Create `sites/try-wasm/src/lib/components/ExplorerToolbar.svelte`
  - Local buttons for curated examples and GitHub import actions near the explorer.
- Create `sites/try-wasm/src/lib/components/InspectorPanel.svelte`
  - Right-side focused-node metadata and selection summary.
- Create `sites/try-wasm/src/lib/components/UtilityPanel.svelte`
  - Bottom tabbed `Logs` / `Problems` panel.
- Create `sites/try-wasm/src/lib/components/workspace-shell.test.ts`
  - Layout, command-surface, splitter, inspector, and utility-panel tests.
- Modify `sites/try-wasm/src/routes/+page.svelte`
  - Own overall playground state and wire compiler results into the new workspace components.
- Modify `sites/try-wasm/src/lib/components/SiteHeader.svelte`
  - Replace the centered title with the new command surface slot/prop.
- Modify `sites/try-wasm/src/lib/components/ActivityBar.svelte`
  - Clarify icon semantics and keep explicit labels/tooltips.
- Modify `sites/try-wasm/src/lib/components/TreeView.svelte`
  - Emit selection callbacks in addition to rendering tree output.
- Modify `sites/try-wasm/src/lib/components/MatchesView.svelte`
  - Support focus/highlight hooks needed by later linked query work without changing default behavior now.
- Modify `sites/try-wasm/src/lib/components/activity-results.test.ts`
  - Cover explorer content, selection, and utility-panel regressions.
- Modify `sites/try-wasm/src/lib/panels.ts`
  - Rename/refine panel semantics so the activity rail maps to explorer intent.
- Modify `sites/try-wasm/package.json`
  - Add `effect` dependency.
- Modify `sites/try-wasm/package-lock.json`
  - Lock `effect`.
- Modify `docs/scripts/check-playground-e2e.mjs`
  - Add browser smoke assertions for the command surface, example loading, and bottom utility tabs.

## Task 1: Service Foundation And Curated Examples

**Files:**
- Create: `sites/try-wasm/src/lib/playground/types.ts`
- Create: `sites/try-wasm/src/lib/playground/catalog.ts`
- Create: `sites/try-wasm/src/lib/playground/catalog.test.ts`
- Create: `sites/try-wasm/src/lib/playground/actions.ts`
- Create: `sites/try-wasm/src/lib/playground/actions.test.ts`
- Modify: `sites/try-wasm/package.json`
- Modify: `sites/try-wasm/package-lock.json`

- [ ] **Step 1: Write the failing tests for curated examples and action errors**

Create `sites/try-wasm/src/lib/playground/catalog.test.ts`:

```ts
import { describe, expect, it } from 'vitest';

import {
  commandSurfaceActions,
  explorerToolbarActions,
  playgroundExamples
} from './catalog';

describe('playground catalog', () => {
  it('exposes deterministic examples for source and query walkthroughs', () => {
    expect(playgroundExamples.map((example) => example.id)).toEqual([
      'elm/basic-module',
      'elm/type-alias',
      'query/value-declaration',
      'query/capture-walkthrough'
    ]);
    expect(playgroundExamples.find((example) => example.id === 'query/capture-walkthrough'))
      .toMatchObject({
        language: 'krueger-query',
        query: '(CstValueDeclaration) @decl'
      });
  });

  it('keeps command and explorer actions aligned for discoverability', () => {
    expect(commandSurfaceActions.map((action) => action.id)).toEqual([
      'example.open',
      'github.import'
    ]);
    expect(explorerToolbarActions.map((action) => action.id)).toEqual([
      'example.open',
      'github.import'
    ]);
  });
});
```

Create `sites/try-wasm/src/lib/playground/actions.test.ts`:

```ts
import { describe, expect, it } from 'vitest';
import { Effect } from 'effect';

import { loadExample } from './actions';

describe('playground actions', () => {
  it('loads a curated example by id', async () => {
    const example = await Effect.runPromise(loadExample('elm/basic-module'));

    expect(example).toMatchObject({
      id: 'elm/basic-module',
      source: expect.stringContaining('module Demo exposing')
    });
  });

  it('maps unknown example ids to a stable diagnostic', async () => {
    const exit = await Effect.runPromiseExit(loadExample('missing/example'));

    expect(exit._tag).toBe('Failure');
    expect(JSON.stringify(exit)).toContain('example/not-found');
    expect(JSON.stringify(exit)).toContain('Unknown example: missing/example');
  });
});
```

- [ ] **Step 2: Run the focused tests to confirm red**

Run:

```bash
cd sites/try-wasm
npx vitest run src/lib/playground/catalog.test.ts src/lib/playground/actions.test.ts
```

Expected: FAIL with missing-module errors for `./catalog` and `./actions`.

- [ ] **Step 3: Install `effect` and add the shared playground types**

Run:

```bash
cd sites/try-wasm
npm install effect --save
```

Update `sites/try-wasm/package.json` dependency section so it includes:

```json
{
  "dependencies": {
    "bits-ui": "^2.18.0",
    "effect": "^3.0.0",
    "monaco-editor": "^0.55.1"
  }
}
```

Create `sites/try-wasm/src/lib/playground/types.ts`:

```ts
export type PlaygroundExample = {
  id: string;
  label: string;
  language: 'elm' | 'krueger-query';
  source: string;
  query: string;
  notes?: string;
};

export type CommandAction = {
  id: 'example.open' | 'github.import';
  label: string;
  hint: string;
};

export type PlaygroundDiagnostic = {
  code: string;
  message: string;
  detail?: string;
  severity: 'info' | 'warning' | 'error';
  source: 'example' | 'github' | 'compiler' | 'runtime';
};

export type PlaygroundLog = {
  message: string;
  kind: 'info' | 'success' | 'warning' | 'error';
};

export type UtilityTab = 'logs' | 'problems';

export type TreeSelection = {
  path: number[];
  nodeType: string;
  text?: string;
  childCount: number;
  span?: { start: number; end: number };
};
```

- [ ] **Step 4: Add the curated examples catalog and `effect` actions**

Create `sites/try-wasm/src/lib/playground/catalog.ts`:

```ts
import type { CommandAction, PlaygroundExample } from './types';

export const playgroundExamples: PlaygroundExample[] = [
  {
    id: 'elm/basic-module',
    label: 'Elm: Basic Module',
    language: 'elm',
    source: `module Demo exposing (..)

main = 42
`,
    query: '(CstValueDeclaration) @decl',
    notes: 'Minimal module for first-run CST/AST exploration.'
  },
  {
    id: 'elm/type-alias',
    label: 'Elm: Type Alias',
    language: 'elm',
    source: `module Demo exposing (User)

type alias User =
    { name : String
    , age : Int
    }
`,
    query: '(TypeAliasDeclaration) @alias',
    notes: 'Shows declaration fields and record structure.'
  },
  {
    id: 'query/value-declaration',
    label: 'Query: Value Declaration',
    language: 'krueger-query',
    source: `module Demo exposing (..)

main = 42
`,
    query: '(CstValueDeclaration) @decl',
    notes: 'Introduces matching against a simple value declaration.'
  },
  {
    id: 'query/capture-walkthrough',
    label: 'Query: Capture Walkthrough',
    language: 'krueger-query',
    source: `module Demo exposing (..)

double value =
    value + value
`,
    query: '(CstValueDeclaration pattern: (_) @pattern expression: (_) @expr) @decl',
    notes: 'Demonstrates multiple captures and field-oriented reading.'
  }
];

export const commandSurfaceActions: CommandAction[] = [
  { id: 'example.open', label: 'Open Example', hint: 'Load a curated example' },
  { id: 'github.import', label: 'Import From GitHub', hint: 'Load a repo file into the editor' }
];

export const explorerToolbarActions: CommandAction[] = [...commandSurfaceActions];
```

Create `sites/try-wasm/src/lib/playground/actions.ts`:

```ts
import { Effect } from 'effect';

import { playgroundExamples } from './catalog';
import type { PlaygroundDiagnostic, PlaygroundExample } from './types';

function exampleNotFound(id: string): PlaygroundDiagnostic {
  return {
    code: 'example/not-found',
    message: `Unknown example: ${id}`,
    severity: 'error',
    source: 'example'
  };
}

export const loadExample = (id: string): Effect.Effect<PlaygroundExample, PlaygroundDiagnostic> =>
  Effect.fromNullable(playgroundExamples.find((example) => example.id === id)).pipe(
    Effect.mapError(() => exampleNotFound(id))
  );
```

- [ ] **Step 5: Run the focused tests to confirm green**

Run:

```bash
cd sites/try-wasm
npx vitest run src/lib/playground/catalog.test.ts src/lib/playground/actions.test.ts
```

Expected: PASS with 4 tests passing.

- [ ] **Step 6: Commit the service foundation**

Run:

```bash
git add sites/try-wasm/package.json sites/try-wasm/package-lock.json sites/try-wasm/src/lib/playground/types.ts sites/try-wasm/src/lib/playground/catalog.ts sites/try-wasm/src/lib/playground/catalog.test.ts sites/try-wasm/src/lib/playground/actions.ts sites/try-wasm/src/lib/playground/actions.test.ts
git commit -m "feat: add playground service foundation"
```

## Task 2: Workspace Shell, Command Surface, And Resizable Layout

**Files:**
- Create: `sites/try-wasm/src/lib/components/CommandSurface.svelte`
- Create: `sites/try-wasm/src/lib/components/PaneResizeHandle.svelte`
- Create: `sites/try-wasm/src/lib/components/workspace-shell.test.ts`
- Modify: `sites/try-wasm/src/lib/components/SiteHeader.svelte`
- Modify: `sites/try-wasm/src/routes/+page.svelte`

- [ ] **Step 1: Write the failing layout and command-surface tests**

Create `sites/try-wasm/src/lib/components/workspace-shell.test.ts`:

```ts
// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/svelte';
import { afterEach, describe, expect, it, vi } from 'vitest';

import Page from '$routes/+page.svelte';

vi.mock('$lib/krueger', async () => {
  const ok = (value: unknown) => ({ ok: true, value, logs: [], errors: [] });
  return {
    createKruegerClient: vi.fn(async () => ({
      backend: 'js',
      parseCst: () => ok({}),
      parseAst: () => ok({}),
      parseCstUnist: () => ok({ type: 'CstModule', data: { fields: {}, childCount: 1 }, children: [] }),
      parseAstUnist: () => ok({ type: 'Module', data: { fields: {}, childCount: 1 }, children: [] }),
      parseQuery: () => ok({}),
      runQuery: () => ok([]),
      prettyQuery: () => '(CstValueDeclaration) @decl',
      tokenize: () => ok([])
    }))
  };
});

describe('playground workspace shell', () => {
  afterEach(() => cleanup());

  it('renders a command surface, explorer, inspector, and utility panel together', () => {
    render(Page);

    expect(screen.getByRole('combobox', { name: 'Playground command' })).not.toBeNull();
    expect(screen.getByRole('region', { name: 'Source workspace' })).not.toBeNull();
    expect(screen.getByRole('region', { name: 'Selection inspector' })).not.toBeNull();
    expect(screen.getByRole('tablist', { name: 'Output panels' })).not.toBeNull();
  });

  it('exposes keyboard-accessible splitters for the center and bottom panes', async () => {
    render(Page);

    const splitter = screen.getAllByRole('separator')[0] as HTMLElement;
    expect(splitter.getAttribute('aria-valuenow')).toBe('62');

    await fireEvent.keyDown(splitter, { key: 'ArrowLeft' });

    expect(splitter.getAttribute('aria-valuenow')).toBe('60');
  });
});
```

- [ ] **Step 2: Run the focused test and confirm red**

Run:

```bash
cd sites/try-wasm
npx vitest run src/lib/components/workspace-shell.test.ts
```

Expected: FAIL because the new command surface, inspector, utility panel, and splitters do not exist yet.

- [ ] **Step 3: Add the command surface and reusable splitter component**

Create `sites/try-wasm/src/lib/components/CommandSurface.svelte`:

```svelte
<script lang="ts">
  import type { CommandAction } from '$lib/playground/types';

  let {
    actions,
    value = '',
    placeholder = 'Examples, imports, commands...',
    onSubmit,
    onInput
  }: {
    actions: CommandAction[];
    value?: string;
    placeholder?: string;
    onSubmit: (value: string) => void;
    onInput: (value: string) => void;
  } = $props();
</script>

<form
  class="command-surface"
  role="search"
  aria-label="Playground command surface"
  on:submit|preventDefault={() => onSubmit(value)}
>
  <input
    class="command-input"
    role="combobox"
    aria-label="Playground command"
    autocomplete="off"
    spellcheck="false"
    bind:value
    placeholder={placeholder}
    on:input={(event) => onInput((event.currentTarget as HTMLInputElement).value)}
  />
  <span class="command-hint">{actions[0]?.label} / {actions[1]?.label}</span>
</form>
```

Create `sites/try-wasm/src/lib/components/PaneResizeHandle.svelte`:

```svelte
<script lang="ts">
  let {
    value,
    min = 20,
    max = 80,
    label,
    onAdjust
  }: {
    value: number;
    min?: number;
    max?: number;
    label: string;
    onAdjust: (next: number) => void;
  } = $props();

  function bump(delta: number): void {
    onAdjust(Math.min(max, Math.max(min, value + delta)));
  }
</script>

<div
  class="resize-handle"
  role="separator"
  tabindex="0"
  aria-label={label}
  aria-valuemin={min}
  aria-valuemax={max}
  aria-valuenow={value}
  on:keydown={(event) => {
    if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') bump(-2);
    if (event.key === 'ArrowRight' || event.key === 'ArrowDown') bump(2);
  }}
/>
```

- [ ] **Step 4: Wire the new shell into the header and page layout**

Update `sites/try-wasm/src/lib/components/SiteHeader.svelte` so the center region can render a command surface:

```svelte
<script lang="ts">
  import CommandSurface from './CommandSurface.svelte';
  import type { CommandAction } from '$lib/playground/types';

  let {
    centerTitle = '',
    commandActions = [],
    commandValue = '',
    onCommandSubmit = () => {},
    onCommandInput = () => {}
  }: {
    centerTitle?: string;
    commandActions?: CommandAction[];
    commandValue?: string;
    onCommandSubmit?: (value: string) => void;
    onCommandInput?: (value: string) => void;
  } = $props();
</script>

{#if commandActions.length > 0}
  <div class="center-command">
    <CommandSurface
      actions={commandActions}
      value={commandValue}
      onSubmit={onCommandSubmit}
      onInput={onCommandInput}
    />
  </div>
{:else if centerTitle}
  <p class="center-title" aria-hidden="true">{centerTitle}</p>
{/if}
```

Update `sites/try-wasm/src/routes/+page.svelte` so it owns pane ratios and passes command props to the header:

```svelte
import CommandSurface from '$lib/components/CommandSurface.svelte';
import PaneResizeHandle from '$lib/components/PaneResizeHandle.svelte';
import { commandSurfaceActions } from '$lib/playground/catalog';

let commandText = $state('');
let editorPercent = $state(62);
let utilityPercent = $state(76);
```

Add the new layout shell:

```svelte
<SiteHeader
  commandActions={commandSurfaceActions}
  commandValue={commandText}
  onCommandInput={(next) => {
    commandText = next;
  }}
  onCommandSubmit={(value) => {
    commandText = value;
  }}
/>

<main class="playground-shell">
  <section class="workspace" aria-label="Source workspace">
    <div class="center-stack" style={`--editor-percent:${editorPercent}; --utility-percent:${utilityPercent};`}>
      <!-- editor/explorer grid goes here in Task 3 -->
    </div>
    <aside class="inspector-shell" aria-label="Selection inspector"></aside>
  </section>
</main>
```

- [ ] **Step 5: Run the focused test to confirm green**

Run:

```bash
cd sites/try-wasm
npx vitest run src/lib/components/workspace-shell.test.ts
```

Expected: PASS with 2 tests passing.

- [ ] **Step 6: Commit the new shell**

Run:

```bash
git add sites/try-wasm/src/lib/components/CommandSurface.svelte sites/try-wasm/src/lib/components/PaneResizeHandle.svelte sites/try-wasm/src/lib/components/workspace-shell.test.ts sites/try-wasm/src/lib/components/SiteHeader.svelte sites/try-wasm/src/routes/+page.svelte
git commit -m "feat: add playground workspace shell"
```

## Task 3: Explorer Pane, Inspector, And Utility Panel

**Files:**
- Create: `sites/try-wasm/src/lib/components/ExplorerPane.svelte`
- Create: `sites/try-wasm/src/lib/components/InspectorPanel.svelte`
- Create: `sites/try-wasm/src/lib/components/UtilityPanel.svelte`
- Modify: `sites/try-wasm/src/lib/components/TreeView.svelte`
- Modify: `sites/try-wasm/src/lib/components/activity-results.test.ts`
- Modify: `sites/try-wasm/src/lib/panels.ts`
- Modify: `sites/try-wasm/src/routes/+page.svelte`

- [ ] **Step 1: Write the failing explorer, inspector, and utility tests**

Add to `sites/try-wasm/src/lib/components/activity-results.test.ts`:

```ts
it('sends tree selection into the inspector and keeps logs/problems in a bottom tabset', async () => {
  render(Page);

  const treeToggle = await screen.findByRole('button', { name: 'Select CstModule' });
  await fireEvent.click(treeToggle);

  expect(screen.getByRole('region', { name: 'Selection inspector' }).textContent).toContain('CstModule');
  expect(screen.getByRole('tab', { name: 'Logs' })).not.toBeNull();
  expect(screen.getByRole('tab', { name: 'Problems' })).not.toBeNull();
});

it('keeps CST and AST as explorer-facing activity rail labels with explicit tooltips', () => {
  render(Page);

  expect(screen.getByRole('tab', { name: 'CST' }).getAttribute('title')).toBe('CST');
  expect(screen.getByRole('tab', { name: 'AST' }).getAttribute('title')).toBe('AST');
});
```

- [ ] **Step 2: Run the focused test and confirm red**

Run:

```bash
cd sites/try-wasm
npx vitest run src/lib/components/activity-results.test.ts
```

Expected: FAIL because the page does not yet render an inspector region or output tabs, and tree nodes do not emit selection.

- [ ] **Step 3: Add the explorer, inspector, and utility components**

Create `sites/try-wasm/src/lib/components/InspectorPanel.svelte`:

```svelte
<script lang="ts">
  import type { TreeSelection } from '$lib/playground/types';

  let { selection }: { selection: TreeSelection | null } = $props();
</script>

<section class="inspector-panel" aria-label="Selection inspector">
  {#if selection}
    <h2>{selection.nodeType}</h2>
    <p>{selection.text ?? 'No text value'}</p>
    <dl>
      <div><dt>Path</dt><dd>{selection.path.join('.')}</dd></div>
      <div><dt>Children</dt><dd>{selection.childCount}</dd></div>
    </dl>
  {:else}
    <p>Select a node to inspect its structure.</p>
  {/if}
</section>
```

Create `sites/try-wasm/src/lib/components/UtilityPanel.svelte`:

```svelte
<script lang="ts">
  import { Tabs } from 'bits-ui';
  import type { PlaygroundDiagnostic, PlaygroundLog, UtilityTab } from '$lib/playground/types';

  let {
    logs,
    problems,
    activeTab = 'logs'
  }: {
    logs: PlaygroundLog[];
    problems: PlaygroundDiagnostic[];
    activeTab?: UtilityTab;
  } = $props();
</script>

<Tabs.Root class="utility-root" value={activeTab}>
  <Tabs.List aria-label="Output panels">
    <Tabs.Trigger value="logs">Logs</Tabs.Trigger>
    <Tabs.Trigger value="problems">Problems</Tabs.Trigger>
  </Tabs.List>
  <Tabs.Content value="logs">
    {#each logs as entry}<p>{entry.message}</p>{/each}
  </Tabs.Content>
  <Tabs.Content value="problems">
    {#if problems.length === 0}
      <p>No problems.</p>
    {:else}
      <ul>{#each problems as problem}<li>{problem.message}</li>{/each}</ul>
    {/if}
  </Tabs.Content>
</Tabs.Root>
```

Create `sites/try-wasm/src/lib/components/ExplorerPane.svelte`:

```svelte
<script lang="ts">
  import MatchesView from './MatchesView.svelte';
  import PrettyQueryView from './PrettyQueryView.svelte';
  import SettingsPanel from './SettingsPanel.svelte';
  import TreeView from './TreeView.svelte';
  import type { CompilerEnvelope, MatchView } from '$lib/krueger';
  import type { TreeSelection } from '$lib/playground/types';
  import type { Panel } from '$lib/panels';

  let {
    selectedPanel,
    cstResult,
    astResult,
    matchResult,
    queryResult,
    prettyQuery,
    onSelectNode
  } = $props();
</script>
```

Update `sites/try-wasm/src/lib/components/TreeView.svelte` so nodes emit selection:

```svelte
let {
  result,
  label,
  onSelectNode = () => {}
}: {
  result: CompilerEnvelope<unknown>;
  label: string;
  onSelectNode?: (selection: TreeSelection) => void;
} = $props();
```

And on the rendered node button:

```svelte
<button
  class="tree-node"
  aria-label={`Select ${node.type}`}
  on:click={() =>
    onSelectNode({
      path,
      nodeType: node.type,
      text: node.value,
      childCount: node.data.childCount
    })}
>
  {node.type}
</button>
```

- [ ] **Step 4: Wire the new explorer layout into the page**

Update `sites/try-wasm/src/lib/panels.ts`:

```ts
export type Panel = 'cst' | 'ast' | 'matches' | 'prettyQuery' | 'settings';

export const panels = [
  { id: 'cst', label: 'CST', icon: '◇' },
  { id: 'ast', label: 'AST', icon: '◆' },
  { id: 'matches', label: 'Matches', icon: '⇢' },
  { id: 'prettyQuery', label: 'Canonical Query', icon: '≡' }
] as const;

export const defaultPanel: Panel = 'cst';
```

Update `sites/try-wasm/src/routes/+page.svelte`:

```svelte
import ExplorerPane from '$lib/components/ExplorerPane.svelte';
import InspectorPanel from '$lib/components/InspectorPanel.svelte';
import UtilityPanel from '$lib/components/UtilityPanel.svelte';
import type { PlaygroundDiagnostic, PlaygroundLog, TreeSelection } from '$lib/playground/types';

let selection = $state<TreeSelection | null>(null);
let logs = $state<PlaygroundLog[]>([{ message: 'Playground ready.', kind: 'info' }]);
let problems = $state<PlaygroundDiagnostic[]>([]);
```

Replace the old right-side `ResultsPanel` usage with:

```svelte
<div class="center-stack">
  <div class="editor-explorer-grid">
    <EditorGroup ... />
    <ExplorerPane
      {selectedPanel}
      {cstResult}
      {astResult}
      {cstUnistResult}
      {astUnistResult}
      {matchResult}
      {queryResult}
      {prettyQuery}
      onSelectNode={(next) => {
        selection = next;
      }}
    />
  </div>
  <UtilityPanel {logs} {problems} />
</div>
<InspectorPanel {selection} />
```

- [ ] **Step 5: Run the focused tests to confirm green**

Run:

```bash
cd sites/try-wasm
npx vitest run src/lib/components/activity-results.test.ts src/lib/components/workspace-shell.test.ts
```

Expected: PASS with inspector and output-panel assertions green.

- [ ] **Step 6: Commit the explorer layout**

Run:

```bash
git add sites/try-wasm/src/lib/components/ExplorerPane.svelte sites/try-wasm/src/lib/components/InspectorPanel.svelte sites/try-wasm/src/lib/components/UtilityPanel.svelte sites/try-wasm/src/lib/components/TreeView.svelte sites/try-wasm/src/lib/components/activity-results.test.ts sites/try-wasm/src/lib/panels.ts sites/try-wasm/src/routes/+page.svelte
git commit -m "feat: add tree-first playground layout"
```

## Task 4: Example Actions Through Command Surface And Explorer Toolbar

**Files:**
- Create: `sites/try-wasm/src/lib/components/ExplorerToolbar.svelte`
- Modify: `sites/try-wasm/src/lib/playground/actions.ts`
- Modify: `sites/try-wasm/src/lib/playground/actions.test.ts`
- Modify: `sites/try-wasm/src/lib/components/CommandSurface.svelte`
- Modify: `sites/try-wasm/src/lib/components/workspace-shell.test.ts`
- Modify: `sites/try-wasm/src/routes/+page.svelte`

- [ ] **Step 1: Write the failing tests for example loading from both entry points**

Add to `sites/try-wasm/src/lib/components/workspace-shell.test.ts`:

```ts
it('loads a curated example from the command surface', async () => {
  render(Page);

  const command = screen.getByRole('combobox', { name: 'Playground command' });
  await fireEvent.input(command, { target: { value: 'example elm/type-alias' } });
  await fireEvent.keyDown(command, { key: 'Enter' });

  expect(screen.getByRole('textbox', { name: 'Elm source' })).toHaveValue(
    expect.stringContaining('type alias User')
  );
  expect(screen.getByText('Loaded example Elm: Type Alias.')).not.toBeNull();
});

it('loads the same curated example from the explorer toolbar', async () => {
  render(Page);

  await fireEvent.click(screen.getByRole('button', { name: 'Load Elm: Basic Module' }));

  expect(screen.getByRole('textbox', { name: 'Elm source' })).toHaveValue(
    expect.stringContaining('main = 42')
  );
});
```

- [ ] **Step 2: Run the focused tests to confirm red**

Run:

```bash
cd sites/try-wasm
npx vitest run src/lib/components/workspace-shell.test.ts src/lib/playground/actions.test.ts
```

Expected: FAIL because the command parser and explorer toolbar do not load examples yet.

- [ ] **Step 3: Expand the action layer to dispatch example commands**

Update `sites/try-wasm/src/lib/playground/actions.ts`:

```ts
import { Effect } from 'effect';

import type { PlaygroundDiagnostic, PlaygroundExample } from './types';

export type CommandIntent =
  | { type: 'example.open'; exampleId: string }
  | { type: 'github.import'; target: string };

export function parseCommandIntent(input: string): CommandIntent | null {
  const trimmed = input.trim();
  if (trimmed.startsWith('example ')) {
    return { type: 'example.open', exampleId: trimmed.slice('example '.length) };
  }
  if (trimmed.startsWith('github ')) {
    return { type: 'github.import', target: trimmed.slice('github '.length) };
  }
  return null;
}

export const resolveExampleIntent = (input: string): Effect.Effect<PlaygroundExample, PlaygroundDiagnostic> =>
  Effect.fromNullable(parseCommandIntent(input)).pipe(
    Effect.flatMap((intent) =>
      intent.type === 'example.open'
        ? loadExample(intent.exampleId)
        : Effect.fail({
            code: 'command/unsupported',
            message: `Unsupported command: ${intent.type}`,
            severity: 'error',
            source: 'runtime'
          })
    ),
    Effect.mapError((error) => error)
  );
```

- [ ] **Step 4: Add the explorer toolbar and wire both surfaces into page state**

Create `sites/try-wasm/src/lib/components/ExplorerToolbar.svelte`:

```svelte
<script lang="ts">
  import type { PlaygroundExample } from '$lib/playground/types';

  let {
    examples,
    onExampleClick,
    onGithubClick
  }: {
    examples: PlaygroundExample[];
    onExampleClick: (exampleId: string) => void;
    onGithubClick: () => void;
  } = $props();
</script>

<div class="explorer-toolbar" aria-label="Explorer tools">
  {#each examples.slice(0, 2) as example}
    <button type="button" on:click={() => onExampleClick(example.id)}>
      Load {example.label}
    </button>
  {/each}
  <button type="button" on:click={onGithubClick}>Import From GitHub</button>
</div>
```

Update `sites/try-wasm/src/routes/+page.svelte`:

```svelte
import { Effect } from 'effect';

import ExplorerToolbar from '$lib/components/ExplorerToolbar.svelte';
import { playgroundExamples } from '$lib/playground/catalog';
import { resolveExampleIntent, loadExample } from '$lib/playground/actions';

async function applyExample(exampleId: string): Promise<void> {
  const example = await Effect.runPromise(loadExample(exampleId));
  source = example.source;
  query = example.query;
  logs = [{ message: `Loaded example ${example.label}.`, kind: 'success' }, ...logs];
}
```

Handle command submit:

```svelte
onCommandSubmit={async (value) => {
  const example = await Effect.runPromise(resolveExampleIntent(value));
  source = example.source;
  query = example.query;
}}
```

Render the toolbar above the explorer:

```svelte
<ExplorerToolbar
  examples={playgroundExamples}
  onExampleClick={applyExample}
  onGithubClick={() => {
    commandText = 'github https://github.com/owner/repo';
  }}
/>
```

- [ ] **Step 5: Run the focused tests to confirm green**

Run:

```bash
cd sites/try-wasm
npx vitest run src/lib/components/workspace-shell.test.ts src/lib/playground/actions.test.ts
```

Expected: PASS with command-surface and toolbar example flows green.

- [ ] **Step 6: Commit example-loading support**

Run:

```bash
git add sites/try-wasm/src/lib/components/ExplorerToolbar.svelte sites/try-wasm/src/lib/playground/actions.ts sites/try-wasm/src/lib/playground/actions.test.ts sites/try-wasm/src/lib/components/CommandSurface.svelte sites/try-wasm/src/lib/components/workspace-shell.test.ts sites/try-wasm/src/routes/+page.svelte
git commit -m "feat: add example loading to playground"
```

## Task 5: GitHub Import Effects And Diagnostics

**Files:**
- Create: `sites/try-wasm/src/lib/playground/github.ts`
- Create: `sites/try-wasm/src/lib/playground/github.test.ts`
- Modify: `sites/try-wasm/src/lib/playground/types.ts`
- Modify: `sites/try-wasm/src/lib/playground/actions.ts`
- Modify: `sites/try-wasm/src/lib/components/workspace-shell.test.ts`
- Modify: `sites/try-wasm/src/routes/+page.svelte`

- [ ] **Step 1: Write the failing GitHub import tests**

Create `sites/try-wasm/src/lib/playground/github.test.ts`:

```ts
import { describe, expect, it, vi } from 'vitest';
import { Effect } from 'effect';

import { importGithubFile, parseGithubTarget } from './github';

describe('github import service', () => {
  it('parses repo and file URLs into a stable target shape', () => {
    expect(parseGithubTarget('https://github.com/elm/core/blob/main/src/Basics.elm')).toEqual({
      owner: 'elm',
      repo: 'core',
      ref: 'main',
      path: 'src/Basics.elm'
    });
  });

  it('imports file content from the GitHub contents API', async () => {
    const fetchImpl = vi.fn(async () =>
      new Response(
        JSON.stringify({ content: Buffer.from('module Demo exposing (..)\\n').toString('base64') }),
        { status: 200 }
      )
    );

    const file = await Effect.runPromise(
      importGithubFile('https://github.com/elm/core/blob/main/src/Basics.elm', fetchImpl)
    );

    expect(file.path).toBe('src/Basics.elm');
    expect(file.source).toContain('module Demo exposing');
  });

  it('returns a typed diagnostic when import fails', async () => {
    const fetchImpl = vi.fn(async () => new Response('missing', { status: 404 }));
    const exit = await Effect.runPromiseExit(
      importGithubFile('https://github.com/elm/core/blob/main/src/Missing.elm', fetchImpl)
    );

    expect(exit._tag).toBe('Failure');
    expect(JSON.stringify(exit)).toContain('github/import-failed');
    expect(JSON.stringify(exit)).toContain('src/Missing.elm');
  });
});
```

Add to `sites/try-wasm/src/lib/components/workspace-shell.test.ts`:

```ts
it('preserves the current editor content when a GitHub import fails', async () => {
  render(Page);

  const command = screen.getByRole('combobox', { name: 'Playground command' });
  await fireEvent.input(command, { target: { value: 'github https://github.com/elm/core/blob/main/src/Missing.elm' } });
  await fireEvent.keyDown(command, { key: 'Enter' });

  expect(screen.getByRole('textbox', { name: 'Elm source' })).toHaveValue(
    expect.stringContaining('main = 42')
  );
  expect(screen.getByRole('tab', { name: 'Problems' })).not.toBeNull();
});
```

- [ ] **Step 2: Run the focused tests to confirm red**

Run:

```bash
cd sites/try-wasm
npx vitest run src/lib/playground/github.test.ts src/lib/components/workspace-shell.test.ts
```

Expected: FAIL because GitHub import parsing and effectful loading do not exist yet.

- [ ] **Step 3: Implement the GitHub import service**

Update `sites/try-wasm/src/lib/playground/types.ts`:

```ts
export type GithubTarget = {
  owner: string;
  repo: string;
  ref: string;
  path: string;
};

export type ImportedGithubFile = {
  path: string;
  source: string;
  repoLabel: string;
};
```

Create `sites/try-wasm/src/lib/playground/github.ts`:

```ts
import { Effect } from 'effect';

import type { GithubTarget, ImportedGithubFile, PlaygroundDiagnostic } from './types';

export function parseGithubTarget(input: string): GithubTarget | null {
  const url = new URL(input);
  const parts = url.pathname.split('/').filter(Boolean);
  if (parts.length < 5 || parts[2] !== 'blob') return null;
  const [owner, repo, , ref, ...pathParts] = parts;
  return { owner, repo, ref, path: pathParts.join('/') };
}

export const importGithubFile = (
  input: string,
  fetchImpl: typeof fetch = fetch
): Effect.Effect<ImportedGithubFile, PlaygroundDiagnostic> =>
  Effect.fromNullable(parseGithubTarget(input)).pipe(
    Effect.mapError(() => ({
      code: 'github/invalid-target',
      message: `Unsupported GitHub target: ${input}`,
      severity: 'error',
      source: 'github'
    })),
    Effect.flatMap((target) =>
      Effect.tryPromise({
        try: async () => {
          const response = await fetchImpl(
            `https://api.github.com/repos/${target.owner}/${target.repo}/contents/${target.path}?ref=${target.ref}`
          );
          if (!response.ok) throw new Error(`${response.status} ${response.statusText}`);
          const payload = (await response.json()) as { content: string };
          return {
            path: target.path,
            source: atob(payload.content.replace(/\\n/g, '')),
            repoLabel: `${target.owner}/${target.repo}`
          };
        },
        catch: (error): PlaygroundDiagnostic => ({
          code: 'github/import-failed',
          message: `Could not import ${target.path}`,
          detail: error instanceof Error ? error.message : String(error),
          severity: 'error',
          source: 'github'
        })
      })
    )
  );
```

- [ ] **Step 4: Wire GitHub import results and failures into logs/problems without clobbering source**

Update `sites/try-wasm/src/lib/playground/actions.ts` to expose a GitHub intent path:

```ts
import { importGithubFile } from './github';

export const resolveCommandIntent = (
  input: string,
  fetchImpl: typeof fetch = fetch
) =>
  Effect.fromNullable(parseCommandIntent(input)).pipe(
    Effect.flatMap((intent) => {
      switch (intent.type) {
        case 'example.open':
          return loadExample(intent.exampleId);
        case 'github.import':
          return importGithubFile(intent.target, fetchImpl);
      }
    })
  );
```

Update `sites/try-wasm/src/routes/+page.svelte`:

```svelte
import { resolveCommandIntent } from '$lib/playground/actions';

async function runCommand(value: string): Promise<void> {
  const exit = await Effect.runPromiseExit(resolveCommandIntent(value));
  if (exit._tag === 'Failure') {
    problems = [
      {
        code: 'github/import-failed',
        message: 'Could not complete the requested import.',
        severity: 'error',
        source: 'github'
      },
      ...problems
    ];
    logs = [{ message: `Command failed: ${value}`, kind: 'error' }, ...logs];
    return;
  }

  const loaded = exit.value;
  if ('repoLabel' in loaded) {
    source = loaded.source;
    logs = [{ message: `Imported ${loaded.path} from ${loaded.repoLabel}.`, kind: 'success' }, ...logs];
    return;
  }

  source = loaded.source;
  query = loaded.query;
}
```

- [ ] **Step 5: Run the focused tests to confirm green**

Run:

```bash
cd sites/try-wasm
npx vitest run src/lib/playground/github.test.ts src/lib/components/workspace-shell.test.ts
```

Expected: PASS with GitHub target parsing, import success, and failure-preserves-source coverage green.

- [ ] **Step 6: Commit GitHub import support**

Run:

```bash
git add sites/try-wasm/src/lib/playground/github.ts sites/try-wasm/src/lib/playground/github.test.ts sites/try-wasm/src/lib/playground/types.ts sites/try-wasm/src/lib/playground/actions.ts sites/try-wasm/src/lib/components/workspace-shell.test.ts sites/try-wasm/src/routes/+page.svelte
git commit -m "feat: add github import to playground"
```

## Task 6: Regression, Accessibility, And E2E Coverage

**Files:**
- Modify: `docs/scripts/check-playground-e2e.mjs`
- Modify: `sites/try-wasm/src/lib/components/ActivityBar.svelte`
- Modify: `sites/try-wasm/src/lib/components/workspace-shell.test.ts`
- Modify: `sites/try-wasm/src/lib/components/activity-results.test.ts`
- Modify: `sites/try-wasm/src/routes/+page.svelte`

- [ ] **Step 1: Write the failing regression and e2e assertions**

Update `docs/scripts/check-playground-e2e.mjs` to assert the new shell:

```js
await page.goto(`${baseUrl}/try/`, { waitUntil: 'domcontentloaded' });

await page.getByRole('combobox', { name: 'Playground command' }).waitFor();
await expectText(page, '[aria-label="Selection inspector"]', 'Select a node', '/try/ inspector placeholder');
await page.getByRole('tab', { name: 'Logs' }).click();
await expectText(page, '[role="tabpanel"]', 'Playground ready.', '/try/ logs tab');
```

Then add the example flow:

```js
const command = page.getByRole('combobox', { name: 'Playground command' });
await command.fill('example elm/type-alias');
await command.press('Enter');
await expectText(page, '.monaco-editor', 'type alias User', '/try/ loaded example');
```

Add to `sites/try-wasm/src/lib/components/workspace-shell.test.ts`:

```ts
it('keeps the output tabset accessible after switching explorer modes', async () => {
  render(Page);

  await fireEvent.click(screen.getByRole('tab', { name: 'AST' }));
  await fireEvent.click(screen.getByRole('tab', { name: 'Problems' }));

  expect(screen.getByRole('tablist', { name: 'Output panels' })).not.toBeNull();
  expect(screen.getByRole('tabpanel').textContent).toContain('No problems.');
});
```

- [ ] **Step 2: Run the focused tests to confirm red**

Run:

```bash
cd sites/try-wasm
npx vitest run src/lib/components/workspace-shell.test.ts src/lib/components/activity-results.test.ts
cd ../../docs
node scripts/check-playground-e2e.mjs
```

Expected: FAIL because the e2e script still targets the old shell and the new accessibility assertions are not yet satisfied.

- [ ] **Step 3: Finish polish for labels, tooltips, and stable output behavior**

Update `sites/try-wasm/src/lib/components/ActivityBar.svelte` so the tab buttons remain explicit:

```svelte
{#each panels as panel}
  <Tabs.Trigger
    class={`activity-button ${selectedPanel === panel.id ? 'is-active' : ''}`}
    value={panel.id}
    title={panel.label}
    aria-label={panel.label}
  >
    <span class="activity-icon" aria-hidden="true">{panel.icon}</span>
    <span class="sr-only">{panel.label}</span>
  </Tabs.Trigger>
{/each}
```

Update `sites/try-wasm/src/routes/+page.svelte` so compiler errors append to `problems` and successful state changes append to `logs`:

```svelte
$effect(() => {
  if (!cstResult.ok) {
    problems = cstResult.errors.map((error) => ({
      code: `compiler/${error.phase}`,
      message: error.message,
      severity: 'error',
      source: 'compiler'
    }));
  }
});
```

- [ ] **Step 4: Run the full local verification pass**

Run:

```bash
cd sites/try-wasm
npm test
```

Then run:

```bash
cd docs
npm run build:full
node scripts/check-playground-e2e.mjs
node scripts/check-build-orchestration.mjs
```

Expected: all Vitest, docs build, playground e2e, and build-orchestration checks PASS.

- [ ] **Step 5: Commit the regression and accessibility pass**

Run:

```bash
git add docs/scripts/check-playground-e2e.mjs sites/try-wasm/src/lib/components/ActivityBar.svelte sites/try-wasm/src/lib/components/workspace-shell.test.ts sites/try-wasm/src/lib/components/activity-results.test.ts sites/try-wasm/src/routes/+page.svelte
git commit -m "test: cover redesigned playground shell"
```

## Self-Review Checklist

- Spec coverage:
  - Tree-first workspace: Tasks 2 and 3.
  - Command surface plus explorer-local actions: Tasks 2 and 4.
  - GitHub import and diagnostics: Task 5.
  - Logs / Problems bottom panel: Task 3 plus Task 6.
  - Query remains secondary but preserved: Tasks 3 and 6.
  - Resizing and constrained layout behavior: Task 2 plus Task 6.
- Placeholder scan:
  - No `TODO`, `TBD`, or “handle later” language remains.
  - Every task names exact files and commands.
- Type consistency:
  - Shared types live in `src/lib/playground/types.ts`.
  - Actions, examples, diagnostics, and GitHub imports use the same type names across tasks.

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-04-25-playground-redesign.md`. Two execution options:

1. Subagent-Driven (recommended) - I dispatch a fresh subagent per task, review between tasks, fast iteration
2. Inline Execution - Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?
