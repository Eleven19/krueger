#!/usr/bin/env node
/*
 * Post-build check for the full-bleed /try/ playground page.
 *
 * Runs AFTER `astro build` and asserts the structural contract that
 * beads -95s (REQ-docs-trypage-001..003) requires:
 *
 *   1. dist/try/index.html exists (the page is routed).
 *   2. It contains a `<div id="try-krueger-root">` mount point — this is
 *      what `Main.mount("#try-krueger-root")` targets in the Laminar bundle.
 *   3. It contains a `<noscript>` fallback so users with JS disabled
 *      see a readable message rather than a blank page.
 *   4. It loads a bootstrap script (any .js under /krueger/) that will
 *      eventually call into the Scala.js ESModule.
 *   5. It renders the shared top nav (Try anchors + GitHub link),
 *      proving Header.astro was applied outside of Starlight's content
 *      collection.
 *   6. It does NOT render Starlight's sidebar chrome — this is the
 *      full-bleed guarantee. Starlight's content pages emit an
 *      `<aside class="sidebar">`; the /try/ page must not.
 *
 * Exits 0 on success, 1 with a descriptive error otherwise.
 */

import { readFile } from 'node:fs/promises';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const distDir = join(here, '..', 'dist');
const tryPage = join(distDir, 'try', 'index.html');

function fail(msg) {
    console.error(`check-try-page: FAIL — ${msg}`);
    process.exit(1);
}

const html = await readFile(tryPage, 'utf8').catch(() => {
    fail(`missing ${tryPage} — run \`npm run build\` first`);
});

if (!/<div[^>]*id=["']try-krueger-root["']/.test(html)) {
    fail('missing <div id="try-krueger-root"> mount point (REQ-docs-trypage-001)');
}

if (!/<noscript[\s>]/.test(html)) {
    fail('missing <noscript> fallback (REQ-docs-trypage-002 edge/boundary)');
}

if (!/<script[^>]+src=["'][^"']*\.js["']/.test(html)) {
    fail('missing bootstrap <script src="…"> tag (REQ-docs-trypage-001)');
}

if (!/href="[^"]*\/krueger\/try\/?"/.test(html)) {
    fail('missing shared top-nav anchor to /krueger/try/ — Header.astro not applied?');
}

if (!/href="[^"]*\/krueger\/try-wasm\/?"/.test(html)) {
    fail('missing shared top-nav anchor to /krueger/try-wasm/ — Header.astro not applied?');
}

if (!/github\.com\/Eleven19\/krueger/.test(html)) {
    fail('missing GitHub social link — Header.astro did not render SocialIcons');
}

if (/<aside[^>]*class=["'][^"']*sidebar/i.test(html)) {
    fail('Starlight sidebar chrome leaked into /try/ — page is not full-bleed (REQ-docs-trypage-003)');
}

console.log('check-try-page: OK — /try/ page is full-bleed, has mount point, noscript fallback, bootstrap script, shared nav.');
