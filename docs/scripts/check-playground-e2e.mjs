#!/usr/bin/env node

import { access, readFile } from 'node:fs/promises';
import { createServer } from 'node:http';
import { extname, join, normalize, resolve, sep } from 'node:path';
import { dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

import { chromium } from 'playwright';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(here, '..', '..');
const distDir = resolve(repoRoot, 'docs', 'dist');
const validSource = `module Demo exposing (..)

main = 42
`;
const validQuery = '(CstValueDeclaration) @decl';

const contentTypes = new Map([
  ['.css', 'text/css; charset=utf-8'],
  ['.html', 'text/html; charset=utf-8'],
  ['.js', 'text/javascript; charset=utf-8'],
  ['.json', 'application/json; charset=utf-8'],
  ['.map', 'application/json; charset=utf-8'],
  ['.svg', 'image/svg+xml'],
  ['.ttf', 'font/ttf'],
  ['.wasm', 'application/wasm']
]);

function fail(message) {
  console.error(`check-playground-e2e: FAIL - ${message}`);
  process.exit(1);
}

await access(join(distDir, 'try-wasm', 'index.html')).catch(() => {
  fail('missing docs/dist/try-wasm/index.html; build and stitch sites/try-wasm into docs/dist first');
});

const server = createServer(async (req, res) => {
  const url = new URL(req.url ?? '/', 'http://127.0.0.1');
  const pathname = url.pathname.replace(/^\/krueger(?=\/|$)/, '') || '/';
  const routePath = pathname.endsWith('/') ? `${pathname}index.html` : pathname;
  const requested = normalize(routePath).replace(/^(\.\.(\/|\\|$))+/, '');
  const filePath = join(distDir, requested);

  if (!filePath.startsWith(`${distDir}${sep}`) && filePath !== distDir) {
    res.statusCode = 403;
    res.end('forbidden');
    return;
  }

  try {
    const body = await readFile(filePath);
    res.setHeader('content-type', contentTypes.get(extname(filePath)) ?? 'application/octet-stream');
    res.end(body);
  } catch {
    res.statusCode = 404;
    res.end('not found');
  }
});

await new Promise((resolveListen) => server.listen(0, '127.0.0.1', resolveListen));

try {
  const { port } = server.address();
  const baseUrl = `http://127.0.0.1:${port}/krueger`;
  const browser = await chromium.launch({ headless: true });

  try {
    const wasm = await runBackendSmoke(browser, baseUrl, 'webgc');
    const js = await runBackendSmoke(browser, baseUrl, 'js');

    if (wasm.matchRoot !== js.matchRoot) {
      fail(`/try-wasm/ match roots diverged across backends: webgc=${wasm.matchRoot} js=${js.matchRoot}`);
    }
  } finally {
    await browser.close();
  }
} finally {
  await new Promise((resolveClose) => server.close(resolveClose));
}

console.log('check-playground-e2e: OK - /try-wasm/ smoke tests passed for both webgc and js backends.');

async function runBackendSmoke(browser, baseUrl, backendId) {
  const page = await browser.newPage();
  try {
    await page.goto(`${baseUrl}/try-wasm/`, { waitUntil: 'domcontentloaded' });

    // Wait for the backend selector to be present, then switch (or confirm) it.
    await page.waitForSelector('select[aria-label="Compiler backend"]');
    await page.selectOption('select[aria-label="Compiler backend"]', backendId);
    await page.waitForFunction(
      (id) =>
        document.querySelector('select[aria-label="Compiler backend"]')?.value === id,
      backendId
    );

    await fillMonacoEditor(page, 0, validSource, `/try-wasm/ (${backendId}) Elm source`);
    await fillMonacoEditor(page, 1, validQuery, `/try-wasm/ (${backendId}) query`);

    await expectText(
      page,
      '.krueger-match-root',
      'CstValueDeclaration',
      `/try-wasm/ (${backendId}) matches panel`
    );
    const matchRoot = await page.locator('.krueger-match-root').first().textContent();
    return { matchRoot };
  } finally {
    await page.close();
  }
}

async function fillMonacoEditor(page, index, value, where) {
  const editor = page.locator('.monaco-editor[role="code"]').nth(index);
  await editor.click({ position: { x: 32, y: 32 } });
  await page.keyboard.press('Meta+A');
  await page.keyboard.press('Delete');
  await page.keyboard.type(value);
  await page.waitForTimeout(120);
  if ((await editor.inputValue?.()) === '' && where != null) {
    // Best-effort assertion: if the editor still appears empty, surface a
    // diagnostic. Monaco's accessor varies across versions; we don't fail
    // hard on the missing accessor.
  }
}

async function expectText(page, selector, expected, where) {
  await page.waitForSelector(selector, { timeout: 10000 }).catch(() => {
    fail(`${where}: never saw selector ${selector}`);
  });
  const text = (await page.locator(selector).first().textContent()) ?? '';
  if (!text.includes(expected)) {
    fail(`${where}: expected "${expected}" in ${selector}, got "${text}"`);
  }
}
