import { cp, mkdir, readFile, readdir, rm, stat, writeFile } from 'node:fs/promises';
import { join } from 'node:path';

async function requireFile(path) {
  const info = await stat(path).catch(() => null);
  if (!info?.isFile() || info.size === 0) throw new Error(`Missing or empty build artifact: ${path}`);
}

async function copyLinked(from, to) {
  await mkdir(to, { recursive: true });
  for (const name of (await readdir(from)).sort()) {
    if (name.endsWith('.map')) continue;
    const source = join(from, name);
    const target = join(to, name);
    if (name.endsWith('.js')) {
      const text = await readFile(source, 'utf8');
      await writeFile(target, text.split('\n').filter(line => !line.startsWith('//# sourceMappingURL=')).join('\n'));
    } else await cp(source, target, { recursive: true });
  }
}

export async function stageWasm(facade, webgc, target) {
  await Promise.all([requireFile(join(facade, 'main.js')), requireFile(join(webgc, 'main.js')), requireFile(join(webgc, 'main.wasm'))]);
  await rm(target, { recursive: true, force: true });
  await mkdir(target, { recursive: true });
  await writeFile(join(target, '.gitkeep'), '');
  await copyLinked(facade, join(target, 'facade'));
  await copyLinked(webgc, join(target, 'webgc'));
}

async function tagHtml(dir) {
  for (const entry of await readdir(dir, { withFileTypes: true })) {
    const path = join(dir, entry.name);
    if (entry.isDirectory()) await tagHtml(path);
    else if (entry.name.endsWith('.html')) {
      const text = await readFile(path, 'utf8');
      if (!text.includes('data-pagefind-ignore')) await writeFile(path, text.replace(/<html(\s|>)/, '<html data-pagefind-ignore="true"$1'));
    }
  }
}

export async function stageApi(platforms, target) {
  for (const source of Object.values(platforms)) await requireFile(join(source, 'index.html'));
  await mkdir(target, { recursive: true });
  for (const [platform, source] of Object.entries(platforms)) {
    const dest = join(target, platform);
    await rm(dest, { recursive: true, force: true });
    await cp(source, dest, { recursive: true });
    await tagHtml(dest);
  }
}
