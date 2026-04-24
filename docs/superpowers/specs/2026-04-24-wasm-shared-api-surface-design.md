# Shared Wasm API Surface Design

Date: 2026-04-24
Status: Proposed and approved in-session

## Context

Krueger currently has a browser-oriented Scala.js WasmGC artifact and a JS facade in `webapp-wasm`, but no host-callable WebAssembly API surface that can be used consistently by both browsers and JVM-side Wasm runtimes such as Chicory.

The current Scala.js-emitted `compiler-api.wasm` artifact is not sufficient as the canonical shared boundary because:

- it expects JavaScript host imports such as `__scalaJSHelpers` and `wasm:js-string`,
- it exposes zero raw Wasm exports for host invocation,
- it cannot be used directly from Chicory as a stable callable ABI.

The goal of this design is to define one canonical logical contract that can be implemented by two Wasm artifacts:

- a browser-oriented WasmGC artifact, and
- a host-callable ABI artifact.

The existing JS-facing `Krueger.parseCst` / `runQuery` API must be preserved.

## Goals

- Define a shared low-level contract usable by both browser and JVM hosts.
- Preserve the current high-level JS facade API for existing web consumers.
- Support WasmGC-capable browsers without forcing the JVM path to emulate browser-only host imports.
- Provide a clean fallback path for non-WasmGC browsers.
- Make backend equivalence testable with deterministic JSON fixtures.

## Non-Goals

- Batching multiple sources or multiple requested outputs in the first ABI cut.
- Replacing the existing JS facade with a raw low-level host API.
- Forcing the browser runtime and JVM runtime to use the same physical Wasm binary.

Batching is tracked separately in `musing-chaum-7e7242-qbj`.

## Decision Summary

Use one canonical API contract and two Wasm deployment artifacts.

- `compiler-api-webgc`
  - browser-oriented artifact
  - optimized for WasmGC-capable environments
  - may depend on Scala.js/JS-host behavior

- `compiler-api-abi`
  - host-callable artifact
  - explicit linear-memory ABI over UTF-8 JSON
  - callable from Chicory
  - usable as browser fallback when WasmGC is unavailable

- `webapp-wasm`
  - remains the stable JS-facing adapter
  - preserves `parseCst`, `parseAst`, `parseQuery`, `runQuery`, `prettyQuery`
  - dispatches internally to either `compiler-api-webgc` or `compiler-api-abi`

This is one semantic contract with two transport implementations.

## Architecture

### Shared Contract

The canonical logical boundary is:

`invoke(op, inputJson) -> outputJson`

Where:

- `op` is a string naming the requested compiler operation
- `inputJson` is a UTF-8 JSON payload for that operation
- `outputJson` is a UTF-8 JSON payload using the existing envelope shape

Supported operations in the initial cut:

- `parseCst`
- `parseAst`
- `parseQuery`
- `runQuery`
- `prettyQuery`

### High-Level API Preservation

The JS-facing browser contract remains unchanged:

- `Krueger.parseCst(src)`
- `Krueger.parseAst(src)`
- `Krueger.parseQuery(src)`
- `Krueger.runQuery(query, root)`
- `Krueger.prettyQuery(query)`

`webapp-wasm` adapts those calls to the canonical low-level contract and returns the same current envelope shape:

- `ok`
- `value`
- `logs`
- `errors`

## Module Layout

### `compiler-api-core`

Purpose:

- Own the canonical operation model.
- Own JSON request/response codecs.
- Implement operation semantics independent of transport.

Responsibilities:

- Parse request payloads.
- Route `op` to the correct compiler operation.
- Serialize deterministic JSON responses.
- Normalize diagnostics and ordering.

Constraints:

- No browser runtime assumptions.
- No direct linear-memory handling.
- No direct Scala.js JS export assumptions.

### `compiler-api-webgc`

Purpose:

- Browser-oriented WasmGC artifact.

Responsibilities:

- Package the shared compiler semantics for WasmGC-capable browsers.
- Integrate with the browser path where WasmGC and JS-host features are available.

Constraints:

- May retain Scala.js-generated host-import requirements.
- Not treated as a direct Chicory target.

### `compiler-api-abi`

Purpose:

- Host-callable Wasm artifact with explicit exports.

Responsibilities:

- Export the linear-memory ABI.
- Decode UTF-8 JSON from guest memory.
- Invoke shared operation semantics.
- Encode response JSON back to guest memory.

Constraints:

- Must be directly callable from Chicory.
- Must not rely on browser-only or JS-only import surfaces.

### `webapp-wasm`

Purpose:

- Compatibility adapter for front-end consumers.

Responsibilities:

- Preserve the stable `Krueger.*` API.
- Detect runtime capabilities.
- Choose backend deterministically.
- Normalize backend differences away from callers.

## ABI Design

### Exported Functions

`compiler-api-abi` exports:

- `alloc(size: i32) -> i32`
- `free(ptr: i32, size: i32) -> ()`
- `invoke(op_ptr: i32, op_len: i32, input_ptr: i32, input_len: i32) -> i32`
- `result_len() -> i32`
- `result_free(ptr: i32, size: i32) -> ()`

### ABI Call Flow

1. Host UTF-8 encodes the operation string and input JSON.
2. Host calls `alloc` for each input buffer.
3. Host copies bytes into guest memory.
4. Host calls `invoke(...)`.
5. Guest serializes the response JSON and returns a result pointer.
6. Host calls `result_len()` to obtain the response size.
7. Host reads response bytes from guest memory.
8. Host frees request and response buffers using `free` / `result_free`.

### Input Schema

Initial request payloads:

- `parseCst`
  - `{ "source": "..." }`
- `parseAst`
  - `{ "source": "..." }`
- `parseQuery`
  - `{ "source": "..." }`
- `runQuery`
  - `{ "query": "...", "root": { ... }, "treeKind": "cst|ast" }`
- `prettyQuery`
  - `{ "query": "..." }`

### Output Schema

All operations return the same envelope structure as JSON:

```json
{
  "ok": true,
  "value": {},
  "logs": [],
  "errors": []
}
```

Semantics:

- `ok` indicates whether the operation succeeded.
- `value` contains the operation result when present.
- `logs` is ordered deterministically.
- `errors` is ordered deterministically and remains structured.

## Runtime Selection

`webapp-wasm` selects the backend internally.

Selection order:

1. Prefer `compiler-api-webgc` when the environment supports WasmGC.
2. Fall back to `compiler-api-abi` otherwise.

The caller never selects the backend directly through the preserved `Krueger.*` API.

This keeps browser integration stable while allowing:

- WasmGC-capable browsers to use the browser-optimized artifact,
- non-WasmGC browsers to use the ABI artifact,
- JVM/Chicory to use the ABI artifact exclusively.

## Artifact Naming

Use distinct artifact identities rather than overloading `main.wasm`.

Preferred conceptual names:

- `compiler-api-webgc`
- `compiler-api-abi`

The concrete emitted filenames can still be adapted by the build, but the module identity should remain explicit in Mill tasks, copy tasks, and tests.

## Testing Strategy

### Contract Fixtures

Maintain one shared set of request/response fixtures for:

- `parseCst`
- `parseAst`
- `parseQuery`
- `runQuery`
- `prettyQuery`

The same semantic requests must produce byte-equivalent JSON responses across backends.

### JVM / Chicory Coverage

Chicory tests target `compiler-api-abi` and must cover:

- happy path
- malformed source/query
- empty input
- repeated deterministic calls
- stable diagnostic payloads

### Browser-Oriented Coverage

Browser-oriented Wasm tests target `compiler-api-webgc` and run the same semantic fixture set.

### Facade Coverage

`webapp-wasm` tests must prove:

- the preserved JS API still works,
- the selected backend is transparent to callers,
- responses are equivalent regardless of whether `webgc` or `abi` is used.

## Rollout Plan

1. Extract the canonical operation and JSON contract into shared code.
2. Implement `compiler-api-abi`.
3. Point Chicory integration tests at `compiler-api-abi`.
4. Teach `webapp-wasm` to dispatch between `compiler-api-webgc` and `compiler-api-abi`.
5. Add parity tests to keep both artifacts behaviorally identical.
6. Address batching in follow-up issue `musing-chaum-7e7242-qbj`.

## Risks

- `runQuery` requires a stable serialized representation of query/root values across both artifacts.
- If JSON serialization is not normalized, byte-equivalence tests will produce noisy failures.
- Browser backend selection must not leak backend-specific errors into the preserved JS API.
- Build tasks must copy and name both artifacts clearly enough for runtime selection and test wiring.

## Open Follow-Up Work

- `musing-chaum-7e7242-9h0`
  - direct Chicory validation remains blocked for the current Scala.js artifact and should pivot toward the new ABI artifact
- `musing-chaum-7e7242-s3o`
  - fallback artifact work is superseded in spirit by the more general `compiler-api-abi` direction and should be reconciled during implementation planning
- `musing-chaum-7e7242-qbj`
  - extend the ABI for batched multi-source and multi-output requests
