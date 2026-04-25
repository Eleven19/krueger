#!/usr/bin/env node

import { access, readFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const siteRoot = resolve(here, '..');

function fail(message) {
  console.error(`check-build-output: FAIL - ${message}`);
  process.exit(1);
}

async function requireFile(path) {
  await access(resolve(siteRoot, path)).catch(() => fail(`missing ${path}`));
}

await requireFile('build/index.html');
await requireFile('build/404.html');
await requireFile('build/wasm/facade/main.js');
await requireFile('build/wasm/webgc/main.wasm');

const html = await readFile(resolve(siteRoot, 'build/index.html'), 'utf8');

if (!html.includes('assets: "/krueger/try"')) {
  fail('build/index.html must configure SvelteKit assets for /krueger/try');
}

if (!html.includes('Try Krueger workspace')) {
  fail('build/index.html must contain the SvelteKit landing page content');
}

console.log('check-build-output: OK - SvelteKit static output is deployable under /krueger/try.');
