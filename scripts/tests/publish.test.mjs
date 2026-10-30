import test from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';

test('publication refuses missing credentials before running any build or upload', () => {
  const env = { ...process.env, SONATYPE_USERNAME: '', SONATYPE_PASSWORD: '', PGP_SECRET: '', PGP_PASSPHRASE: '' };
  const result = spawnSync('bash', ['scripts/ci/publish-central.sh', '0.1.0-SNAPSHOT'], { env, encoding: 'utf8' });
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /SONATYPE_USERNAME is required/);
  assert.doesNotMatch(result.stdout, /welcome to sbt/);
});
test('publication uses sbt signing and separates snapshot upload from release promotion', () => {
  const script = readFileSync('scripts/ci/publish-central.sh', 'utf8');
  assert.doesNotMatch(script, /MILL_|\.\/mill/);
  assert.match(script, /publishSigned/);
  assert.match(script, /sonaRelease/);
  assert.match(script, /SNAPSHOT/);
  assert.match(script, /gpg.*--batch.*--import/);
});

test('publication rejects malformed versions before key import', () => {
  const result = spawnSync('bash', ['scripts/ci/publish-central.sh', '0.1.0;invalid'], {
    env: { ...process.env, SONATYPE_USERNAME: 'probe', SONATYPE_PASSWORD: 'probe', PGP_SECRET: 'probe', PGP_PASSPHRASE: 'probe' }, encoding: 'utf8'
  });
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /Invalid publish version/);
  assert.doesNotMatch(result.stdout + result.stderr, /imported|welcome to sbt/);
});
