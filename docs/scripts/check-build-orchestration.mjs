#!/usr/bin/env node

import { access, readFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(here, '..', '..');

function fail(message) {
    console.error(`check-build-orchestration: FAIL - ${message}`);
    process.exit(1);
}

const sbtBuild = await readFile(resolve(repoRoot, 'build.sbt'), 'utf8');
const docsPackage = JSON.parse(await readFile(resolve(repoRoot, 'docs', 'package.json'), 'utf8'));

for (const task of ['stageWasm', 'apiDocs', 'prepareLocalDevSite', 'site']) {
    if (!sbtBuild.includes(`${task} := Def.uncached`)) fail(`sbt must expose uncached ${task} orchestration`);
}

if (docsPackage.scripts['build:wasm'] !== 'cd .. && ./sbt --batch stageWasm') {
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

if (docsPackage.scripts['prepare:try-wasm'] !== 'cd .. && ./sbt --batch prepareLocalDevSite') {
    fail('docs/package.json must expose prepare:try-wasm -> sbt prepareLocalDevSite');
}

if (!docsPackage.scripts['dev:full']?.includes('npm run prepare:try-wasm')) {
    fail('docs/package.json dev:full must run prepare:try-wasm before astro dev');
}

if (docsPackage.scripts['site:build'] !== 'npm run build:full') {
    fail('docs/package.json site:build must alias npm run build:full');
}

try {
    await access(resolve(repoRoot, 'docs', 'src', 'pages', 'try', 'index.astro'));
} catch {
    fail('docs/src/pages/try/index.astro must exist so Astro dev serves /try/ instead of falling through to Starlight 404');
}

console.log('check-build-orchestration: OK - sbt site, prepareLocalDevSite, and npm scripts orchestrate full site.');
