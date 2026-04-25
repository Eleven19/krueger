import { describe, expect, it } from 'vitest';

import {
  backends,
  defaultBackend,
  fallbackBackend,
  isAvailable,
  pickInitialBackend
} from './backend';

describe('backend metadata', () => {
  it('exposes both webgc and js backend ids in declaration order', () => {
    expect(backends.map((b) => b.id)).toEqual(['webgc', 'js']);
  });

  it('marks webgc as requiring WebAssembly GC and js as not requiring it', () => {
    expect(backends.find((b) => b.id === 'webgc')?.requiresWasmGc).toBe(true);
    expect(backends.find((b) => b.id === 'js')?.requiresWasmGc).toBe(false);
  });

  it('defaults to the WASM backend and falls back to the JS backend', () => {
    expect(defaultBackend).toBe('webgc');
    expect(fallbackBackend).toBe('js');
  });
});

describe('pickInitialBackend', () => {
  it('returns the WASM default when WebAssembly GC is supported', () => {
    expect(pickInitialBackend(true)).toBe('webgc');
  });

  it('returns the WASM default while WebAssembly GC support is still being probed', () => {
    expect(pickInitialBackend(null)).toBe('webgc');
  });

  it('falls back to the JS backend when WebAssembly GC is unsupported', () => {
    expect(pickInitialBackend(false)).toBe('js');
  });
});

describe('isAvailable', () => {
  it('reports the WASM backend as unavailable when WebAssembly GC is unsupported', () => {
    expect(isAvailable('webgc', false)).toBe(false);
  });

  it('reports the WASM backend as available when WebAssembly GC is supported', () => {
    expect(isAvailable('webgc', true)).toBe(true);
  });

  it('always reports the JS backend as available regardless of WebAssembly GC support', () => {
    expect(isAvailable('js', false)).toBe(true);
    expect(isAvailable('js', null)).toBe(true);
    expect(isAvailable('js', true)).toBe(true);
  });
});
