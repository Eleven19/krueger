import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, readdirSync, existsSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';

function designs(dir) {
  return readdirSync(dir, { withFileTypes: true }).flatMap(entry => {
    const path = join(dir, entry.name);
    return entry.isDirectory() ? designs(path) : entry.name === 'design.html' ? [path] : [];
  });
}
test('real OpenSpec designs have complete template heads and resolvable local assets', () => {
  const template = readFileSync('docs/templates/agent-document.html', 'utf8');
  const expected = [...template.matchAll(/(?:href|src)="(assets\/[^"]+)"/g)].map(m => resolve('docs/templates', m[1]));
  assert.equal(expected.length, 7, 'shared template must contain its complete asset set');
  const files = designs('openspec/changes');
  assert.ok(files.length > 0, 'must verify actual designs');
  for (const file of files) {
    const html = readFileSync(file, 'utf8');
    assert.match(html, /<head>[\s\S]*?<\/head>\s*<body>\s*<main>/, file);
    const visible = html.replace(/<!--[\s\S]*?-->/g, '');
    assert.match(visible, /<main>\s*<h1>/, `${file}: content must not be inside a comment`);
    assert.match(visible, /<\/main>\s*<\/body>\s*<\/html>/, file);
    const resources = [...visible.matchAll(/(?:href|src)="([^"]+)"/g)].map(m => m[1]).filter(p => !p.startsWith('#'));
    assert.deepEqual(resources.map(p => resolve(dirname(file), p)), expected, `${file}: head must match the shared template`);
    for (const resource of resources) assert.ok(existsSync(resolve(dirname(file), resource)), `${file}: missing ${resource}`);
    assert.doesNotMatch(visible, /<style\b|<script(?![^>]*\bsrc=)/i, file);
  }
});
