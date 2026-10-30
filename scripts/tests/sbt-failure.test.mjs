import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, rmSync } from 'node:fs';
import { resolve } from 'node:path';
import { spawnSync } from 'node:child_process';

test('full qualification propagates a real failing Kyo assertion', () => {
  mkdirSync('.dev/docs', { recursive: true });
  const dir = mkdtempSync(resolve('.dev/docs/failing-suite-'));
  try {
    writeFileSync(`${dir}/MigrationFailureSpec.scala`, `import kyo.test.*
class MigrationFailureSpec extends Test[Any]:
    "migration qualification failure sentinel" in assert(false)
`);
    const result = spawnSync('./sbt', ['--batch', `set treesJvm / Test / unmanagedSourceDirectories += file("${dir}")`, 'treesJvm/Test/testFull'], { encoding: 'utf8', timeout: 180000, maxBuffer: 8 * 1024 * 1024 });
    assert.notEqual(result.status, 0);
    assert.match(result.stdout + result.stderr, /migration qualification failure sentinel/);
    assert.match(result.stdout + result.stderr, /1 failed/);
    assert.doesNotMatch(result.stdout + result.stderr, /Not found:|Syntax Error/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test('format violations fail the contributor check', () => {
  mkdirSync('.dev/docs', { recursive: true });
  const dir = mkdtempSync(resolve('.dev/docs/format-probe-'));
  try {
    writeFileSync(`${dir}/BadFormat.scala`, 'object BadFormat{val x=1}\n');
    const result = spawnSync('./sbt', ['--batch', `set treesJvm / Test / unmanagedSourceDirectories += file("${dir}")`, 'treesJvm/Test/scalafmtCheck'], { encoding: 'utf8', timeout: 120000 });
    assert.notEqual(result.status, 0);
    assert.match(result.stdout + result.stderr, /not formatted|must be formatted|scalafmt.*failed/i);
  } finally { rmSync(dir, { recursive: true, force: true }); }
});
