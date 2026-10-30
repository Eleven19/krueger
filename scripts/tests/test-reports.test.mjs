import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { verifyReports } from '../ci/verify-test-reports.mjs';

test('qualification requires actual test events for every discovered suite', async t => {
  const dir = await mkdtemp(join(tmpdir(), 'krueger-reports-'));
  t.after(() => rm(dir, { recursive: true, force: true }));
  await writeFile(join(dir, 'TEST-A.xml'), '<testsuite name="A" tests="2" failures="0" errors="0" skipped="0"/>');
  assert.equal(await verifyReports(dir, ['A']), 2);
  await assert.rejects(verifyReports(dir, ['A', 'B']), /B.*missing/);
  await writeFile(join(dir, 'TEST-A.xml'), '<testsuite name="A" tests="0" failures="0" errors="0" skipped="0"/>');
  await assert.rejects(verifyReports(dir, ['A']), /A.*no executed tests/);
});
test('failure, malformed, missing and all-skipped reports cannot qualify', async t => {
  const dir = await mkdtemp(join(tmpdir(), 'krueger-reports-'));
  t.after(() => rm(dir, { recursive: true, force: true }));
  await assert.rejects(verifyReports(dir, ['A']), /missing/);
  for (const xml of ['bad XML', '<testsuite name="A" tests="1" failures="1" errors="0"/>', '<testsuite name="A" tests="1" failures="0" errors="0" skipped="1"/>']) {
    await writeFile(join(dir, 'TEST-A.xml'), xml);
    await assert.rejects(verifyReports(dir, ['A']), /A/);
  }
});
