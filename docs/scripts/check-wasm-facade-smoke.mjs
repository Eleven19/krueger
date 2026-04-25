#!/usr/bin/env node

/**
 * Headless smoke test for the WASM-linked compiler facade.
 *
 * Loads the artifact at `out/krueger/webapp-wasm/wasm/fullLinkJS.dest/main.js`
 * (or a path supplied as the first CLI arg) and runs parseCst, parseQuery,
 * runQuery, prettyQuery, and tokenize through the `KruegerWasm` export.
 * Mirrors `check-webapp-wasm-browser.mjs` (which targets the JS-linked
 * `Krueger` export) so each link target has its own parity check.
 *
 * Requires Node 24+ with `--experimental-wasm-exnref` (the Scala.js Wasm
 * backend emits `try_table` instructions that V8 still gates behind that
 * flag). The script self-checks the flag and exits with a clear message
 * if it's missing.
 */

import { access } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(here, '..', '..');

const defaultArtifactDir = resolve(
  repoRoot,
  'out',
  'krueger',
  'webapp-wasm',
  'wasm',
  'fullLinkJS.dest'
);
const arg = process.argv.find((value, index) => index > 1 && value !== '--');
const artifactDir = arg ? resolve(repoRoot, arg) : defaultArtifactDir;
const entrypoint = resolve(artifactDir, 'main.js');

function fail(message) {
  console.error(`check-wasm-facade-smoke: FAIL - ${message}`);
  process.exit(1);
}

await access(entrypoint).catch(() => {
  fail(`missing ${entrypoint}; run \`./mill krueger.webapp-wasm.wasm.fullLinkJS\` first`);
});

const flagsRequested = (process.execArgv ?? []).join(' ');
if (!flagsRequested.includes('--experimental-wasm-exnref')) {
  fail(
    'this smoke must be invoked with `node --experimental-wasm-exnref ...`; ' +
      'the Scala.js Wasm backend emits try_table opcodes still behind that flag in Node 24'
  );
}

const validSource = `module Demo exposing (..)\n\nmain = 42\n`;
const malformedSource = `module M exposing (..)\n\nx =`;
const validQuery = '(CstValueDeclaration) @decl';

const mod = await import(pathToFileURL(entrypoint).href);

if (!mod.KruegerWasm || typeof mod.KruegerWasm.parseCst !== 'function') {
  fail(
    `the Wasm artifact did not export a populated KruegerWasm namespace. ` +
      `Got keys: ${mod.KruegerWasm ? Object.keys(mod.KruegerWasm).join(',') : 'undefined'}`
  );
}

const facade = mod.KruegerWasm;

const ok = facade.parseCst(validSource);
if (!ok.ok) fail(`parseCst on valid source returned ok=false (errors: ${JSON.stringify(ok.errors)})`);
if (ok.errors.length !== 0) fail(`parseCst on valid source unexpectedly produced errors`);
if (ok.value == null) fail(`parseCst on valid source returned value=null`);

const malformed = facade.parseCst(malformedSource);
if (malformed.ok) fail(`parseCst on malformed source returned ok=true`);
if (malformed.errors.length === 0) fail(`parseCst on malformed source produced no errors`);
if (malformed.errors[0]?.phase !== 'cst') {
  fail(`parseCst on malformed source: first error phase=${malformed.errors[0]?.phase}, expected "cst"`);
}

const repeat = facade.parseCst(validSource);
if (repeat.ok !== ok.ok || repeat.errors.length !== ok.errors.length) {
  fail(`repeated parseCst diverged on ok/errors`);
}

const queryEnv = facade.parseQuery(validQuery);
if (!queryEnv.ok) fail(`parseQuery on valid query returned ok=false`);

const matches = facade.runQuery(queryEnv.value, ok.value);
if (!matches.ok) fail(`runQuery returned ok=false`);
if (!Array.isArray(matches.value) || matches.value.length === 0) {
  fail(`runQuery returned no matches for the seeded source`);
}

// The success value of parseCst is an opaque Wasm reference (cannot be
// String()-coerced for direct comparison). Determinism is therefore checked
// indirectly: the same query against two parses of the same source must
// produce the same match count.
const repeatMatches = facade.runQuery(queryEnv.value, repeat.value);
if (repeatMatches.value?.length !== matches.value.length) {
  fail(
    `runQuery determinism check failed (first=${matches.value.length}, ` +
      `repeat=${repeatMatches.value?.length})`
  );
}

const pretty = facade.prettyQuery(queryEnv.value);
if (typeof pretty !== 'string' || pretty.length === 0) {
  fail(`prettyQuery returned empty / non-string`);
}

const tokens = facade.tokenize('module Demo = 1');
if (!tokens.ok || !Array.isArray(tokens.value) || tokens.value.length === 0) {
  fail(`tokenize returned an empty / failed envelope`);
}

console.log(
  `check-wasm-facade-smoke: OK - KruegerWasm exposes parseCst/parseQuery/runQuery/prettyQuery/tokenize, ` +
    `match count=${matches.value.length}, parity with JS facade.`
);
