import { describe, expect, it, vi } from 'vitest';

import { shouldLoadWasmCompiler, supportsWasmGc, wasmGcRequirementsText } from './wasm-gc';

describe('Wasm GC feature detection', () => {
  it('uses a WebAssembly.validate GC probe when WebAssembly is available', () => {
    const validate = vi.fn(() => true);

    expect(supportsWasmGc({ validate })).toBe(true);
    expect(validate).toHaveBeenCalledTimes(1);
    expect(validate.mock.calls[0]?.[0]).toBeInstanceOf(Uint8Array);
  });

  it('returns false when WebAssembly is missing or rejects the GC probe', () => {
    expect(supportsWasmGc(undefined)).toBe(false);
    expect(supportsWasmGc({ validate: () => false })).toBe(false);
  });

  it('guards compiler initialization on a positive detection result only', () => {
    expect(shouldLoadWasmCompiler(true)).toBe(true);
    expect(shouldLoadWasmCompiler(false)).toBe(false);
    expect(shouldLoadWasmCompiler(null)).toBe(false);
  });

  it('keeps user-facing browser requirements in one reusable string', () => {
    expect(wasmGcRequirementsText).toContain('Chrome 119+');
    expect(wasmGcRequirementsText).toContain('Firefox 120+');
    expect(wasmGcRequirementsText).toContain('Safari 18.2+');
  });
});
