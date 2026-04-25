#!/usr/bin/env node
/**
 * Copy sites/try-wasm/build into docs/dist/try (mirrors CI / docs.site stitch).
 * Run after `npm run build` in docs/ so docs/dist exists.
 *
 * The source directory keeps the historical `try-wasm` name; only the
 * deployment URL is `/krueger/try/`.
 */

import { cpSync, existsSync, mkdirSync, rmSync, statSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const src = join(repoRoot, 'sites', 'try-wasm', 'build');
const dest = join(repoRoot, 'docs', 'dist', 'try');

function fail(msg) {
  console.error(`stitch-try-wasm-dist: FAIL — ${msg}`);
  process.exit(1);
}

if (!existsSync(src) || !statSync(src).isDirectory()) {
  fail(`missing ${src} — run npm run build:try-wasm (or ./mill docs.site) first`);
}

if (existsSync(dest)) rmSync(dest, { recursive: true });
mkdirSync(dest, { recursive: true });
cpSync(src, dest, { recursive: true });
console.log(`stitch-try-wasm-dist: OK — ${src} -> ${dest}`);
