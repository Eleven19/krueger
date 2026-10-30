import { cp, mkdir, rm, stat } from 'node:fs/promises';
import { resolve, join } from 'node:path';
import { spawnSync } from 'node:child_process';
import { stageWasm, stageApi } from './site.mjs';

const root = resolve(import.meta.dirname, '../..');
function npm(dir, ...args) {
  const result = spawnSync('npm', args, { cwd: join(root, dir), stdio: 'inherit' });
  if (result.status !== 0) throw new Error(`npm ${args.join(' ')} failed in ${dir}: ${result.error ?? result.status}`);
}
async function copySite(to) {
  const from = join(root, 'sites/try-wasm/build');
  if (!(await stat(join(from, 'index.html')).catch(() => null))?.isFile()) throw new Error(`Missing playground index: ${from}`);
  await rm(to, { recursive: true, force: true });
  await mkdir(to, { recursive: true });
  await cp(from, to, { recursive: true });
}
try {
  switch (process.argv[2]) {
    case 'stage-wasm':
      await stageWasm(process.argv[3], process.argv[4], join(root, 'sites/try-wasm/static/wasm'));
      break;
    case 'stage-api':
      await stageApi(Object.fromEntries(['jvm', 'js', 'native'].map((p, i) => [p, process.argv[i + 3]])), join(root, 'docs/public/api'));
      break;
    case 'site':
    case 'prepare':
      npm('sites/try-wasm', 'ci');
      npm('sites/try-wasm', 'run', 'build');
      if (process.argv[2] === 'prepare') await copySite(join(root, 'docs/public/try'));
      else {
        // Avoid carrying a previously prepared local playground into the production build.
        await rm(join(root, 'docs/public/try'), { recursive: true, force: true });
        npm('docs', 'ci');
        npm('docs', 'run', 'build');
        await copySite(join(root, 'docs/dist/try'));
      }
      break;
    default: throw new Error(`Unknown site operation: ${process.argv[2]}`);
  }
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
}
