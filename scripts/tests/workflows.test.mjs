import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';

test('all active workflows use sbt and preserve the independent platform checks', () => {
  for (const name of readdirSync('.github/workflows')) {
    const text = readFileSync(`.github/workflows/${name}`, 'utf8');
    assert.doesNotMatch(text, /\.\/mill|setup-mill-caches|mill-build\/|build\.mill\.yaml/, name);
  }
  assert.match(readFileSync('.github/workflows/copilot-setup-steps.yml', 'utf8'), /sbt --batch compileAll/);
  const ci = readFileSync('.github/workflows/ci.yml', 'utf8');
  for (const task of ['testJvmFull', 'testJsFull', 'testNativeFull', 'testWasmFull', 'testIntegrationFull', 'site']) assert.ok(ci.includes(task), `CI missing ${task}`);
  assert.match(ci, /if:.*always\(\)/);
  assert.match(ci, /check-results.mjs lint build build-js build-native build-docs/);
  for (const name of ['publish-central.yml', 'publish-snapshot.yml', 'release.yml']) {
    assert.match(readFileSync(`.github/workflows/${name}`, 'utf8'), /scripts\/ci\/qualify.sh/);
  }
});

test('qualification cannot read signing secrets and missing publication credentials fail', () => {
  for (const name of ['publish-central.yml', 'publish-snapshot.yml']) {
    const workflow = readFileSync(`.github/workflows/${name}`, 'utf8');
    assert.doesNotMatch(workflow, /^      (?:SONATYPE_|PGP_)/m, 'credentials must not be job-wide');
    assert.match(workflow, /^          PGP_SECRET:/m);
    assert.doesNotMatch(workflow, /if: env.SONATYPE_USERNAME/);
    assert.ok(workflow.indexOf('scripts/ci/qualify.sh') < workflow.indexOf('PGP_SECRET:'));
  }
});
