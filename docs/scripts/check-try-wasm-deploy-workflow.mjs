#!/usr/bin/env node

import { readFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(here, '..', '..');
const deployWorkflow = await readFile(resolve(repoRoot, '.github', 'workflows', 'deploy.yml'), 'utf8');

function fail(message) {
  console.error(`check-try-wasm-deploy-workflow: FAIL - ${message}`);
  process.exit(1);
}

if (!deployWorkflow.includes("- 'sites/try-wasm/**'")) {
  fail('deploy workflow must trigger when the SvelteKit try-wasm site changes');
}

if (!deployWorkflow.includes('sites/try-wasm/package-lock.json')) {
  fail('deploy workflow npm cache must include the try-wasm package lock');
}

if (!deployWorkflow.includes('Build web artifacts')) {
  fail('deploy workflow must have a named Mill artifact build step');
}

if (!deployWorkflow.includes('./mill krueger.webapp.writeToDocsSrc')) {
  fail('deploy workflow must build the Laminar webapp artifact before site build');
}

if (!deployWorkflow.includes('./mill krueger.webapp-wasm.writeToWasmSite')) {
  fail('deploy workflow must populate try-wasm/static/wasm before SvelteKit builds');
}

if (!deployWorkflow.includes('working-directory: sites/try-wasm') || !deployWorkflow.includes('npm ci && npm run build')) {
  fail('deploy workflow must build the SvelteKit try-wasm site from sites/try-wasm');
}

if (!deployWorkflow.includes('docs/dist/try-wasm') || !deployWorkflow.includes('cp -R sites/try-wasm/build/. docs/dist/try-wasm/')) {
  fail('deploy workflow must stitch SvelteKit build output into docs/dist/try-wasm');
}

const order = [
  'Build web artifacts',
  'Build WASM playground (SvelteKit)',
  'Build site (Scaladoc + Astro)',
  'Stitch WASM playground into Pages artifact',
  'Upload Pages artifact'
].map((needle) => deployWorkflow.indexOf(needle));

if (order.some((index) => index === -1)) {
  fail('deploy workflow is missing one or more required step names');
}

for (let i = 1; i < order.length; i += 1) {
  if (order[i - 1] > order[i]) {
    fail('deploy workflow step order must be Mill artifacts -> SvelteKit -> Astro -> stitch -> upload');
  }
}

console.log('check-try-wasm-deploy-workflow: OK - deploy workflow builds and stitches /try-wasm into Pages.');
