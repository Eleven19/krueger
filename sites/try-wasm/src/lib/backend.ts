/**
 * Compiler backend metadata used by the playground UI.
 *
 * The two ids match the strings the Scala-side facade accepts via
 * `Krueger.setBackend(id)` (see `KruegerJs.scala` and `BackendLoader.scala`).
 * Keep them in sync — they form the wire contract between the SvelteKit UI
 * and the Scala.js facade.
 */

export type BackendId = 'webgc' | 'js';

export type BackendInfo = {
  id: BackendId;
  label: string;
  description: string;
  /** True when this backend depends on the browser supporting WebAssembly GC. */
  requiresWasmGc: boolean;
};

export const backends: readonly BackendInfo[] = [
  {
    id: 'webgc',
    label: 'WASM',
    description: 'WebAssembly GC compiler artifact (default when supported).',
    requiresWasmGc: true
  },
  {
    id: 'js',
    label: 'JavaScript',
    description: 'Pure Scala.js JavaScript build — works in every browser.',
    requiresWasmGc: false
  }
];

export const defaultBackend: BackendId = 'webgc';
export const fallbackBackend: BackendId = 'js';

export function backendInfo(id: BackendId): BackendInfo {
  const found = backends.find((candidate) => candidate.id === id);
  if (found === undefined) throw new Error(`Unknown backend id: ${id}`);
  return found;
}

/**
 * Pick the initial backend given the host's WebAssembly GC support.
 *
 * - `null` (still detecting): defer to the WASM default; the UI may swap to
 *   the fallback once the probe resolves.
 * - `true` (supported): use the WASM-backed default.
 * - `false` (unsupported): force the JavaScript fallback.
 */
export function pickInitialBackend(wasmGcSupported: boolean | null): BackendId {
  if (wasmGcSupported === false) return fallbackBackend;
  return defaultBackend;
}

export function isAvailable(id: BackendId, wasmGcSupported: boolean | null): boolean {
  if (!backendInfo(id).requiresWasmGc) return true;
  return wasmGcSupported !== false;
}
