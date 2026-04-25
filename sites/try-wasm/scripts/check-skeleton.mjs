#!/usr/bin/env node

import { access, readFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const siteRoot = resolve(here, '..');

function fail(message) {
  console.error(`check-skeleton: FAIL - ${message}`);
  process.exit(1);
}

async function readJson(path) {
  return JSON.parse(await readFile(resolve(siteRoot, path), 'utf8').catch(() => fail(`missing ${path}`)));
}

async function requireFile(path) {
  await access(resolve(siteRoot, path)).catch(() => fail(`missing ${path}`));
}

const pkg = await readJson('package.json');
const svelteConfig = await readFile(resolve(siteRoot, 'svelte.config.js'), 'utf8').catch(() => fail('missing svelte.config.js'));
const viteConfig = await readFile(resolve(siteRoot, 'vite.config.ts'), 'utf8').catch(() => fail('missing vite.config.ts'));

if (pkg.scripts?.['build:wasm'] !== 'cd ../.. && ./mill krueger.webapp-wasm.writeToWasmSite') {
  fail('package.json must expose build:wasm that invokes Mill from the repo root');
}

if (pkg.scripts?.['build:full'] !== 'npm run build:wasm && npm run build') {
  fail('package.json must expose build:full as build:wasm && build');
}

for (const dep of ['@sveltejs/adapter-static', '@sveltejs/kit', 'svelte', 'vite']) {
  if (!pkg.devDependencies?.[dep]) fail(`package.json missing devDependency ${dep}`);
}

if (!svelteConfig.includes("adapter-static") || !svelteConfig.includes("fallback: '404.html'")) {
  fail('svelte.config.js must configure adapter-static with fallback 404.html');
}

if (!svelteConfig.includes("base: dev ? '' : '/krueger/try'")) {
  fail('svelte.config.js must set production base to /krueger/try');
}

if (!viteConfig.includes('sveltekit()')) {
  fail('vite.config.ts must install the SvelteKit Vite plugin');
}

await requireFile('src/routes/+page.svelte');
await requireFile('src/app.html');
await requireFile('static/wasm/.gitkeep');

console.log('check-skeleton: OK - SvelteKit static skeleton is configured for /krueger/try.');
