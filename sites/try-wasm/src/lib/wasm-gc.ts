export const wasmGcRequirementsText = 'Chrome 119+, Firefox 120+, or Safari 18.2+';

type WebAssemblyProbe = Pick<typeof WebAssembly, 'validate'> | undefined;

const wasmGcProbe = new Uint8Array([
  0x00, 0x61, 0x73, 0x6d, // magic
  0x01, 0x00, 0x00, 0x00, // version
  0x01, 0x03, 0x01, 0x5f, 0x00 // type section: one empty GC struct type
]);

export function supportsWasmGc(webAssembly?: WebAssemblyProbe): boolean {
  const candidate = arguments.length === 0 ? globalThis.WebAssembly : webAssembly;

  if (candidate === undefined) return false;

  try {
    return candidate.validate(wasmGcProbe);
  } catch {
    return false;
  }
}

export function shouldLoadWasmCompiler(wasmGcSupported: boolean | null): boolean {
  return wasmGcSupported === true;
}
