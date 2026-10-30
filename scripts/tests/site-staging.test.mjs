import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, mkdir, writeFile, readFile, readdir, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { stageWasm, stageApi } from '../build/site.mjs';

async function fixture(t) {
  const root = await mkdtemp(join(tmpdir(), 'krueger-stage-'));
  t.after(() => rm(root, { recursive: true, force: true }));
  for (const p of ['facade', 'webgc', 'output', 'jvm', 'js', 'native']) await mkdir(join(root, p));
  for (const p of ['facade', 'webgc']) {
    await writeFile(join(root, p, 'main.js'), 'export const hello = 1;\n//# sourceMappingURL=main.js.map\n');
    await writeFile(join(root, p, 'main.js.map'), '{}');
  }
  await writeFile(join(root, 'webgc', 'main.wasm'), Buffer.from([0, 97, 115, 109]));
  return root;
}
test('staging copies both backends, strips maps and restores deleted output', async t => {
  const root = await fixture(t);
  const target = join(root, 'output');
  await writeFile(join(target, 'stale.js'), 'old');
  for (let i = 0; i < 2; i++) {
    await stageWasm(join(root, 'facade'), join(root, 'webgc'), target);
    assert.deepEqual((await readdir(target)).sort(), ['.gitkeep', 'facade', 'webgc']);
    assert.deepEqual(await readdir(join(target, 'facade')), ['main.js']);
    assert.equal(await readFile(join(target, 'facade/main.js'), 'utf8'), 'export const hello = 1;\n');
    assert.equal((await readFile(join(target, 'webgc/main.wasm'))).length, 4);
    await rm(target, { recursive: true });
  }
});
test('missing or empty WebGC artifacts fail before replacing the old site', async t => {
  const root = await fixture(t);
  await writeFile(join(root, 'output/keep'), 'old');
  for (const mode of ['empty', 'absent']) {
    if (mode === 'empty') await writeFile(join(root, 'webgc/main.wasm'), '');
    else await rm(join(root, 'webgc/main.wasm'));
    await assert.rejects(stageWasm(join(root, 'facade'), join(root, 'webgc'), join(root, 'output')), /main.wasm/);
    assert.equal(await readFile(join(root, 'output/keep'), 'utf8'), 'old');
  }
});
test('API staging preserves landing page and excludes every platform from search', async t => {
  const root = await fixture(t);
  for (const p of ['jvm', 'js', 'native']) await writeFile(join(root, p, 'index.html'), '<html lang="en">API</html>');
  await writeFile(join(root, 'output/index.html'), 'landing');
  await stageApi(Object.fromEntries(['jvm', 'js', 'native'].map(p => [p, join(root, p)])), join(root, 'output'));
  assert.equal(await readFile(join(root, 'output/index.html'), 'utf8'), 'landing');
  for (const p of ['jvm', 'js', 'native']) assert.match(await readFile(join(root, 'output', p, 'index.html'), 'utf8'), /data-pagefind-ignore="true"/);
});
