# Unist Tree Projection Design

## Context

The playground needs an AST Explorer-style treeview for CST and AST output. The current JS/WASM facade returns opaque Scala handles for `parseCst` and `parseAst`, which is correct for query execution but not enough for browser-side inspection. Krueger already has `QueryableTree[T]` instances for CST and AST with stable node type, children, fields, and text. This design adds a unist-compatible projection layer on top of those type classes, then exposes that projection to the web facade in the same feature PR before the Svelte treeview consumes it.

The unist shape is intentionally plain data: nodes have `type`, optional `data`, optional `position`, and parent nodes have `children`. Krueger will use this as an interchange format, not as the internal CST/AST representation.

## EARS Requirements

- REQ-unist-001 (When): When a `QueryableTree[T]` node is projected to unist, the system shall emit a JSON-compatible node with `type`, optional `value`, optional `position`, `data`, and ordered `children`.
- REQ-unist-002 (If): If a node has no children, then the system shall omit `children` or emit an empty children collection consistently according to the projection API contract.
- REQ-unist-003 (Where): Where a node is a CST or AST node with a `Span`, the system shall expose deterministic positional data using zero-based offsets and one-based line/column points when source text is available.
- REQ-unist-004 (When): When CST and AST roots are projected, the system shall preserve `QueryableTree.children` traversal order and named field metadata so the UI can label child rows without re-deriving structure.
- REQ-unist-005 (If): If the JS/WASM facade receives malformed source for CST or AST parsing, then the system shall keep returning the existing error envelope shape instead of a unist value.
- REQ-unist-006 (Where): Where multiple exports are added for playground consumption, the JS and WebGC facades shall expose the same method names and plain object shapes.

## Architecture

Add a small unist model to `krueger.trees`, backed by a type class that can project any `QueryableTree[T]` into serializable data:

- `UnistNode` is the canonical Scala product type for interchange. It contains `nodeType: String`, `value: Option[String]`, `position: Option[UnistPosition]`, `data: UnistData`, and `children: IndexedSeq[UnistNode]`.
- `UnistPosition` and `UnistPoint` mirror unist source locations. Points are one-based line/column with optional zero-based offset. Krueger spans remain zero-based `[start, end)` offsets internally.
- `UnistData` carries Krueger-specific metadata that unist leaves to ecosystems: `fields: Map[String, IndexedSeq[Int]]` maps field names to direct child indexes, and `childCount: Int` provides a cheap summary for UI rows.
- `UnistProjection[T]` supplies optional span access and delegates node type, children, fields, and text to `QueryableTree[T]`.

The CST and AST implementations live beside the existing queryable instances in `krueger/core`. They should reuse `CstQueryableTree.given` and `AstQueryableTree.given` rather than duplicating field/child logic.

## Facade Shape

The feature PR will add facade methods after the core projection is green:

- `parseCstUnist(source: String): CompilerEnvelope[UnistNode]`
- `parseAstUnist(source: String): CompilerEnvelope[UnistNode]`

The JS/WASM boundary will convert `UnistNode` to plain JavaScript objects:

```json
{
  "type": "CstModule",
  "position": {
    "start": { "line": 1, "column": 1, "offset": 0 },
    "end": { "line": 4, "column": 10, "offset": 41 }
  },
  "data": {
    "fields": { "declarations": [2] },
    "childCount": 3
  },
  "children": [
    { "type": "CstModuleDeclaration", "data": { "fields": {}, "childCount": 2 }, "children": [] }
  ]
}
```

String-valued leaves, such as `CstName`, use `value` for the primary text supplied by `QueryableTree.text`.

## Components

Core tree model:
- New file `krueger/trees/src/io/eleven19/krueger/trees/unist/UnistNode.scala`
- New file `krueger/trees/src/io/eleven19/krueger/trees/unist/UnistProjection.scala`

CST/AST adapters:
- New file `krueger/core/src/io/eleven19/krueger/cst/CstUnistProjection.scala`
- New file `krueger/core/src/io/eleven19/krueger/ast/AstUnistProjection.scala`

Facade/API:
- Modify `krueger/webapp-wasm/src/io/eleven19/krueger/webappwasm/KruegerJs.scala`
- Modify `krueger/webapp-wasm/wasm/src/io/eleven19/krueger/webappwasm/wasm/WasmFacade.scala`
- Modify `sites/try-wasm/src/lib/krueger.ts`

Playground UI:
- Replace `TreeView.svelte` raw-only rendering with an explorer that consumes unist nodes.
- Keep raw fallback for loading text and any legacy opaque values.

## Data Flow

1. A user edits Elm source in the playground.
2. The client calls `parseCstUnist` or `parseAstUnist` for display and keeps the existing `parseCst` handle for query execution.
3. The compiler parses source into CST/AST.
4. The relevant `UnistProjection` converts the root into plain tree data using `QueryableTree` order and fields.
5. The facade serializes that tree to JS objects in the existing compiler envelope.
6. `TreeView.svelte` renders the unist tree with disclosure state, labels, text snippets, child counts, search, expand/collapse, and error fallback.

## Error Handling

Projection should not hide parse failures. Existing compiler envelopes remain authoritative: successful parse returns `ok=true` with a unist value; failed parse returns `ok=false`, `value=null`, and the existing error list.

If source text is unavailable for a projection, spans may still be represented as offset-only data or omitted. The facade methods always have source text, so playground CST/AST output should include full start/end points.

## Testing

The PR will follow strict red-green-refactor. Coverage must include:

- Happy path: toy `QueryableTree` projection creates ordered parent/literal nodes.
- Negative/failure path: malformed CST/AST source still returns existing error envelopes for unist facade methods.
- Boundary path: empty children, empty field sequences, and zero-length spans produce deterministic output.
- Regression path: CST and AST unist projections preserve existing `QueryableTree.children` order and field names.
- Integration path: JS and WebGC facades expose matching `parseCstUnist` and `parseAstUnist` shapes.
- UI path: treeview renders nested unist nodes, supports expand/collapse and search, and preserves existing parse error rendering.

## Single-PR Delivery Plan

Although the work is staged, it will be delivered in one PR:

1. Add core unist model and generic projection tests.
2. Add CST and AST projection instances and tests.
3. Add JS/WASM facade exports and browser-facing TypeScript normalization.
4. Replace the playground tree view with the AST Explorer-style component.
5. Run Scala, webapp, and docs/playground checks before closing the Beads issue and pushing the branch.
