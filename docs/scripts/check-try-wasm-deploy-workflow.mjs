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

if (!deployWorkflow.includes('./sbt --batch site')) {
  fail('deploy workflow must run ./sbt --batch site (full Pages artifact including try-wasm)');
}

if (!deployWorkflow.includes('Upload Pages artifact')) {
  fail('deploy workflow must upload the Pages artifact');
}

const sbtIdx = deployWorkflow.indexOf('./sbt --batch site');
const uploadIdx = deployWorkflow.indexOf('Upload Pages artifact');
if (sbtIdx === -1 || uploadIdx === -1 || sbtIdx > uploadIdx) {
  fail('deploy workflow must run ./sbt --batch site before Upload Pages artifact');
}

console.log(
  'check-try-wasm-deploy-workflow: OK - deploy workflow runs docs.site and uploads dist (try-wasm included via sbt).'
);
