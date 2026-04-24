#!/usr/bin/env node

import { readFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(here, '..', '..');

function fail(message) {
    console.error(`check-build-orchestration: FAIL - ${message}`);
    process.exit(1);
}

const docsMill = await readFile(resolve(repoRoot, 'docs', 'package.mill'), 'utf8');
const docsPackage = JSON.parse(await readFile(resolve(repoRoot, 'docs', 'package.json'), 'utf8'));

if (!docsMill.includes('build.krueger.webapp.writeToDocsSrc()')) {
    fail('docs.site must run krueger.webapp.writeToDocsSrc before Astro builds');
}

if (!docsMill.includes('build.krueger.`webapp-wasm`.writeToWasmSite()')) {
    fail('docs.site must run krueger.webapp-wasm.writeToWasmSite before Astro/SvelteKit builds');
}

if (docsPackage.scripts['build:wasm'] !== 'cd .. && ./mill krueger.webapp-wasm.writeToWasmSite') {
    fail('docs/package.json must expose build:wasm for local and CI orchestration');
}

if (!docsPackage.scripts['build:full']?.includes('npm run build:wasm')) {
    fail('docs/package.json build:full must include build:wasm');
}

console.log('check-build-orchestration: OK - docs.site and npm scripts orchestrate compiler web artifacts.');
