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

if (!deployWorkflow.includes('./mill docs.site')) {
  fail('deploy workflow must run ./mill docs.site (full Pages artifact including try-wasm)');
}

if (!deployWorkflow.includes('Upload Pages artifact')) {
  fail('deploy workflow must upload the Pages artifact');
}

const millIdx = deployWorkflow.indexOf('./mill docs.site');
const uploadIdx = deployWorkflow.indexOf('Upload Pages artifact');
if (millIdx === -1 || uploadIdx === -1 || millIdx > uploadIdx) {
  fail('deploy workflow must run ./mill docs.site before Upload Pages artifact');
}

console.log(
  'check-try-wasm-deploy-workflow: OK - deploy workflow runs docs.site and uploads dist (try-wasm included via Mill).'
);
