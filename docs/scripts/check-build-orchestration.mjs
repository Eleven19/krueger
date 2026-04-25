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

if (!docsMill.includes('copyWasmArtifactsToTryWasm(') ||
    !docsMill.includes('build.krueger.`webapp-wasm`.fullLinkJS()')) {
    fail('docs.site must stage the krueger.webapp-wasm + compiler-api.wasm artifacts (via copyWasmArtifactsToTryWasm) before Astro/SvelteKit builds');
}

if (!docsMill.includes('runTryWasmNpmBuild(') || !docsMill.includes('copyTryWasmBuildInto(')) {
    fail('docs.package.mill must build sites/try-wasm and stitch into docs output');
}

if (!docsMill.includes('prepareLocalDevSite')) {
    fail('docs.package.mill must expose docs.prepareLocalDevSite for full local dev');
}

if (docsMill.includes('build.krueger.webapp.') || docsMill.includes('copyWebappBundleToDocsSrc')) {
    fail('docs.package.mill must not reference the removed Laminar `webapp` module');
}

if (docsPackage.scripts['build:wasm'] !== 'cd .. && ./mill krueger.webapp-wasm.writeToWasmSite') {
    fail('docs/package.json must expose build:wasm for local and CI orchestration');
}

if (docsPackage.scripts['build:webapp']) {
    fail('docs/package.json must not expose build:webapp (Laminar bundle removed)');
}

if (!docsPackage.scripts['build:full']?.includes('npm run build:wasm')) {
    fail('docs/package.json build:full must include build:wasm');
}

if (!docsPackage.scripts['build:full']?.includes('npm run build:try-wasm')) {
    fail('docs/package.json build:full must include build:try-wasm');
}

if (!docsPackage.scripts['build:full']?.includes('npm run stitch:try-wasm')) {
    fail('docs/package.json build:full must stitch try-wasm into docs/dist');
}

if (docsPackage.scripts['build:full']?.includes('npm run build:webapp')) {
    fail('docs/package.json build:full must not invoke removed build:webapp step');
}

if (docsPackage.scripts['prepare:try-wasm'] !== 'cd .. && ./mill docs.prepareLocalDevSite') {
    fail('docs/package.json must expose prepare:try-wasm -> mill docs.prepareLocalDevSite');
}

if (!docsPackage.scripts['dev:full']?.includes('npm run prepare:try-wasm')) {
    fail('docs/package.json dev:full must run prepare:try-wasm before astro dev');
}

if (docsPackage.scripts['site:build'] !== 'npm run build:full') {
    fail('docs/package.json site:build must alias npm run build:full');
}

console.log('check-build-orchestration: OK - docs.site, prepareLocalDevSite, and npm scripts orchestrate full site.');
