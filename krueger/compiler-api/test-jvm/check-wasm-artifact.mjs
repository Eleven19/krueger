#!/usr/bin/env node

import fs from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();
const artifactDir =
  process.argv[2] ??
  path.join(repoRoot, 'out', 'krueger', 'compiler-api', 'wasm', 'fullLinkJS.dest');

const expectedFiles = ['main.js', 'main.wasm'];

const missing = expectedFiles.filter((name) => {
  const file = path.join(artifactDir, name);
  return !fs.existsSync(file) || !fs.statSync(file).isFile();
});

if (missing.length > 0) {
  console.error(`Missing WASM link artifacts in ${artifactDir}: ${missing.join(', ')}`);
  process.exit(1);
}

const empty = expectedFiles.filter((name) => fs.statSync(path.join(artifactDir, name)).size === 0);

if (empty.length > 0) {
  console.error(`Empty WASM link artifacts in ${artifactDir}: ${empty.join(', ')}`);
  process.exit(1);
}

console.log(`WASM link artifacts present in ${artifactDir}: ${expectedFiles.join(', ')}`);
