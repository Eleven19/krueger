import test from 'node:test';
import assert from 'node:assert/strict';
import { assertSuccessful } from '../ci/check-results.mjs';

test('aggregate accepts every selected successful job', () => {
  assert.doesNotThrow(() => assertSuccessful({ lint: { result: 'success' }, build: { result: 'success' } }, ['lint', 'build']));
});
for (const result of ['failure', 'cancelled', 'skipped', undefined]) {
  test(`aggregate rejects ${result ?? 'missing'} selected work`, () => {
    const jobs = { lint: { result: 'success' } };
    if (result) jobs.build = { result };
    assert.throws(() => assertSuccessful(jobs, ['lint', 'build']), /build/);
  });
}
test('aggregate reports failures in stable selection order and ignores unrelated work', () => {
  assert.throws(() => assertSuccessful({ a: { result: 'failure' }, b: { result: 'cancelled' } }, ['b', 'a']), /b: cancelled.*a: failure/);
  assert.doesNotThrow(() => assertSuccessful({ a: { result: 'success' }, b: { result: 'skipped' } }, ['a']));
});
