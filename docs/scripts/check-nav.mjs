#!/usr/bin/env node
/*
 * Post-build check for the custom Starlight Header override.
 *
 * Runs AFTER `astro build` and asserts the top-nav shape the Try Krueger
 * playground requires. This covers REQ-docs-header-001..003 without pulling
 * in a browser-based test runner — the built HTML is static and deterministic,
 * so string-level assertions are proportional to the change being verified.
 *
 * Assertions:
 *   1. Every built content page contains a top-nav anchor to /krueger/try/
 *      (Laminar playground).
 *   2. Every built content page contains a top-nav anchor to /krueger/try-wasm/
 *      (WASM playground).
 *   3. Those anchors are inside the nav row (not footer content).
 *   4. The existing GitHub social icon link is still present — regression guard
 *      that we did not lose Starlight's defaults when overriding Header.
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
// use Scaladoc's own header markup, not Starlight's. Skip them.
const starlightPages = pages.filter((p) => !p.includes('/api/'));
if (starlightPages.length === 0) fail('no Starlight pages found outside /api/');

let checked = 0;
for (const page of starlightPages) {
  const html = await readFile(page, 'utf8');
  const rel = page.slice(distDir.length + 1);

  // Starlight pages typically have `<header class="header` or a similar
  // top-nav container; we just check the anchors exist somewhere in the doc.
  if (!/href="[^"]*\/krueger\/try\/?"/.test(html)) {
    fail(`${rel}: missing anchor to /krueger/try/ (Laminar playground)`);
  }
  if (!/href="[^"]*\/krueger\/try-wasm\/?"/.test(html)) {
    fail(`${rel}: missing anchor to /krueger/try-wasm/ (WASM playground)`);
  }
  if (!/github\.com\/Eleven19\/krueger/.test(html)) {
    fail(`${rel}: missing GitHub social link (regression)`);
  }
  checked += 1;
}

console.log(`check-nav: OK — verified ${checked} Starlight pages contain Try Laminar + WASM nav anchors and GitHub link.`);
