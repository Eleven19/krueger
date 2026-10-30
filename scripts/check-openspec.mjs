import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { cpSync, existsSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { after, before, test } from 'node:test';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
let workspace;

function run(args, cwd = workspace) {
  return spawnSync('openspec', args, {
    cwd, encoding: 'utf8', timeout: 30_000,
    env: { ...process.env, OPENSPEC_TELEMETRY: '0', NO_COLOR: '1' },
  });
}

function json(args, cwd) {
  const result = run([...args, '--json'], cwd);
  assert.equal(result.status, 0, result.stderr || result.stdout);
  return JSON.parse(result.stdout);
}

function checkAssets(path) {
  const html = readFileSync(path, 'utf8');
  const resources = [...html.matchAll(/(?:href|src)="([^"]+)"/g)]
    .map((match) => match[1]).filter((value) => !value.startsWith('#'));
  assert.ok(resources.length >= 4, 'design must reference local styles and scripts');
  for (const resource of resources) {
    assert.ok(!/^(?:https?:|\/\/|data:)/.test(resource), `external resource: ${resource}`);
    assert.ok(existsSync(resolve(dirname(path), resource)), `missing resource: ${resource}`);
  }
  assert.doesNotMatch(html, /<style\b|<script(?![^>]*\bsrc=)/i);
}

before(() => {
  workspace = mkdtempSync(join(tmpdir(), 'krueger-openspec-'));
  cpSync(join(root, 'openspec'), join(workspace, 'openspec'), { recursive: true });
  cpSync(join(root, 'docs/templates'), join(workspace, 'docs/templates'), { recursive: true });
});

after(() => { if (workspace) rmSync(workspace, { recursive: true, force: true }); });

test('html-design resolves locally and validates', () => {
  const schema = json(['schema', 'which', 'html-design']);
  assert.equal(schema.source, 'project');
  const result = run(['schema', 'validate', 'html-design']);
  assert.equal(result.status, 0, result.stderr || result.stdout);
});

test('new changes default to HTML designs and enforce artifact dependencies', () => {
  const result = run(['new', 'change', 'check-html-design']);
  assert.equal(result.status, 0, result.stderr || result.stdout);
  const status = json(['status', '--change', 'check-html-design']);
  assert.equal(status.schemaName, 'html-design');
  assert.deepEqual(status.artifacts.map(({ id }) => id), ['proposal', 'specs', 'design', 'tasks']);
  assert.equal(status.artifacts.find(({ id }) => id === 'proposal').status, 'ready');
  const tasks = status.artifacts.find(({ id }) => id === 'tasks');
  assert.equal(tasks.status, 'blocked');
  assert.deepEqual(tasks.missingDeps, ['specs', 'design']);
  const design = json(['instructions', 'design', '--change', 'check-html-design']);
  assert.equal(design.outputPath, 'design.html');
  assert.match(design.template, /<!doctype html>/i);
  assert.match(design.instruction, /language-scala/);
});

test('design assets resolve from both the shared template and a new change', () => {
  checkAssets(join(workspace, 'docs/templates/agent-document.html'));
  const path = join(workspace, 'openspec/changes/check-html-design/design.html');
  cpSync(join(workspace, 'openspec/schemas/html-design/templates/design.html'), path);
  checkAssets(path);
});

test('archive guidance accounts for the extra directory depth', () => {
  const config = readFileSync(join(workspace, 'openspec/config.yaml'), 'utf8');
  assert.ok(config.includes('../../../docs/templates/assets/'));
  assert.ok(config.includes('../../../../docs/templates/assets/'));
  const active = join(workspace, 'openspec/changes/check-html-design');
  const archived = join(workspace, 'openspec/changes/archive/2026-10-29-check-html-design');
  cpSync(active, archived, { recursive: true });
  const path = join(archived, 'design.html');
  writeFileSync(path, readFileSync(path, 'utf8').replaceAll(
    '../../../docs/templates/assets/', '../../../../docs/templates/assets/'));
  checkAssets(path);
});

test('missing design templates fail schema validation with a useful diagnostic', () => {
  const path = join(workspace, 'openspec/schemas/html-design/templates/design.html');
  const original = readFileSync(path);
  try {
    rmSync(path);
    const result = run(['schema', 'validate', 'html-design']);
    assert.notEqual(result.status, 0);
    assert.match(result.stderr + result.stdout, /design\.html/);
  } finally {
    writeFileSync(path, original);
  }
});

test('an unknown schema fails instead of silently selecting the default', () => {
  const result = run(['schema', 'which', 'missing-krueger-schema']);
  assert.notEqual(result.status, 0);
  assert.match(result.stderr + result.stdout, /missing-krueger-schema/);
});
