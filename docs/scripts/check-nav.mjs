#!/usr/bin/env node
/*
 * Post-build check for the custom Starlight Header override.
 *
 * Runs AFTER `astro build` and asserts the top-nav shape the Try Krueger
 * playground requires. Asserts:
 *
 *   1. Every built content page contains a top-nav anchor to /krueger/try-wasm/
 *      (the SvelteKit playground — the only Try playground after the Laminar
 *      page was removed).
 *   2. The existing GitHub social icon link is still present — regression guard
 *      that we did not lose Starlight's defaults when overriding Header.
 *   3. No anchor still points at the legacy /krueger/try/ path.
 *
 * Exits 0 on success, 1 with a descriptive error otherwise.
 */

import { readdir, readFile } from 'node:fs/promises';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { dirname } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const distDir = join(here, '..', 'dist');

async function walk(dir) {
  const out = [];
  for (const entry of await readdir(dir, { withFileTypes: true })) {
    const p = join(dir, entry.name);
    if (entry.isDirectory()) out.push(...(await walk(p)));
    else if (entry.name.endsWith('.html')) out.push(p);
  }
  return out;
}

function fail(msg) {
  console.error(`check-nav: FAIL — ${msg}`);
  process.exit(1);
}

const pages = await walk(distDir).catch(() => {
  fail(`dist/ not found at ${distDir} — run \`npm run build\` first`);
});

if (pages.length === 0) fail(`no built HTML pages under ${distDir}`);

// Pagefind-ignore pages (the generated Scaladoc trees) live at /api/... and
// use Scaladoc's own header markup, not Starlight's. The SvelteKit playground
// at /try-wasm/ ships its own header, so skip both.
const starlightPages = pages.filter(
  (p) => !p.includes('/api/') && !p.includes('/try-wasm/')
);
if (starlightPages.length === 0) fail('no Starlight pages found outside /api/ and /try-wasm/');

let checked = 0;
for (const page of starlightPages) {
  const html = await readFile(page, 'utf8');
  const rel = page.slice(distDir.length + 1);

  if (!/href="[^"]*\/krueger\/try-wasm\/?"/.test(html)) {
    fail(`${rel}: missing anchor to /krueger/try-wasm/ (Try playground)`);
  }
  if (/href="[^"]*\/krueger\/try\/?"/.test(html)) {
    fail(`${rel}: stale anchor to legacy /krueger/try/ — Laminar playground was removed`);
  }
  if (!/github\.com\/Eleven19\/krueger/.test(html)) {
    fail(`${rel}: missing GitHub social link (regression)`);
  }
  checked += 1;
}

console.log(`check-nav: OK — verified ${checked} Starlight pages link to /try-wasm/ and GitHub.`);
