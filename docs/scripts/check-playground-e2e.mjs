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
const malformedQuery = '';

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

await access(join(distDir, 'try', 'index.html')).catch(() => {
  fail('missing docs/dist/try/index.html; run ./mill docs.site first');
});
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
    const laminar = await runLaminarSmoke(browser, baseUrl);
    const wasm = await runWasmSmoke(browser, baseUrl);
    await runWasmFallbackSmoke(browser, baseUrl);

    if (laminar.matchRoot !== wasm.matchRoot) {
      fail(`/try/ and /try-wasm/ match roots diverged: ${laminar.matchRoot} !== ${wasm.matchRoot}`);
    }
  } finally {
    await browser.close();
  }
} finally {
  await new Promise((resolveClose) => server.close(resolveClose));
}

console.log('check-playground-e2e: OK - /try/ and /try-wasm/ playground smoke tests passed.');

async function runLaminarSmoke(browser, baseUrl) {
  const page = await browser.newPage();
  try {
    await page.goto(`${baseUrl}/try/`, { waitUntil: 'domcontentloaded' });
    await fillMonacoEditor(page, 0, validSource, '/try/ Elm source');
    await fillMonacoEditor(page, 1, validQuery, '/try/ query');

    await expectText(page, '.krueger-match-root', 'CstValueDeclaration', '/try/ matches panel');
    const matchRoot = await page.locator('.krueger-match-root').first().textContent();
    await page.getByRole('tab', { name: 'CST' }).click();
    await expectText(page, '.krueger-tree-type', 'CstModule', '/try/ CST panel');
    await page.getByRole('tab', { name: 'AST' }).click();
    await expectText(page, '.krueger-tree-type', 'Module', '/try/ AST panel');
    await page.getByRole('tab', { name: 'Canonical Query' }).click();
    await expectText(page, '.krueger-pretty-body', 'CstValueDeclaration', '/try/ canonical query panel');

    await fillMonacoEditor(page, 1, malformedQuery, '/try/ malformed query');
    await expectText(page, '.krueger-pretty-error', 'Query parse errors:', '/try/ malformed query error');

    return { matchRoot };
  } finally {
    await page.close();
  }
}

async function runWasmSmoke(browser, baseUrl) {
  const page = await browser.newPage();
  try {
    await page.goto(`${baseUrl}/try-wasm/`, { waitUntil: 'domcontentloaded' });
    await fillMonacoEditor(page, 0, validSource, '/try-wasm/ Elm source');
    await fillMonacoEditor(page, 1, validQuery, '/try-wasm/ query');

    await expectText(page, '.match-root', 'CstValueDeclaration', '/try-wasm/ matches panel');
    const matchRoot = await page.locator('.match-root').first().textContent();
    await page.getByRole('tab', { name: 'CST' }).click();
    await expectText(page, '.tree-body', 'CstModule', '/try-wasm/ CST panel');
    await page.getByRole('tab', { name: 'AST' }).click();
    await expectText(page, '.tree-body', 'Module', '/try-wasm/ AST panel');
    await page.getByRole('tab', { name: 'Canonical Query' }).click();
    await expectText(page, '.pretty-query', 'CstValueDeclaration', '/try-wasm/ canonical query panel');

    await fillMonacoEditor(page, 1, malformedQuery, '/try-wasm/ malformed query');
    await expectText(page, '.error-card', 'Query parse errors:', '/try-wasm/ malformed query error');

    return { matchRoot };
  } finally {
    await page.close();
  }
}

async function runWasmFallbackSmoke(browser, baseUrl) {
  const context = await browser.newContext();
  try {
    await context.addInitScript(() => {
      const originalValidate = WebAssembly.validate.bind(WebAssembly);
      WebAssembly.validate = (bytes) => {
        if (bytes instanceof Uint8Array && bytes.length === 13 && bytes[11] === 0x5f) {
          return false;
        }
        return originalValidate(bytes);
      };
    });
    const page = await context.newPage();
    await page.goto(`${baseUrl}/try-wasm/`, { waitUntil: 'domcontentloaded' });
    await expectText(page, '[role="status"]', 'This browser does not support WebAssembly GC', '/try-wasm/ fallback banner');
  } finally {
    await context.close();
  }
}

async function fillMonacoEditor(page, index, value, label) {
  const editor = page.locator('.monaco-editor').nth(index);
  await editor.waitFor({ state: 'visible', timeout: 20000 }).catch((error) => {
    throw new Error(`${label}: Monaco editor ${index} did not become visible: ${error.message}`);
  });
  await editor.click();
  await page.keyboard.press(process.platform === 'darwin' ? 'Meta+A' : 'Control+A');
  await page.keyboard.press('Backspace');
  if (value.length > 0) {
    await page.keyboard.type(value);
  }
}

async function expectText(page, selector, expected, label) {
  await page.locator(selector).filter({ hasText: expected }).first().waitFor({ timeout: 20000 }).catch(async (error) => {
    const bodyText = (await page.locator('body').innerText().catch(() => '')).slice(0, 2000);
    throw new Error(`${label}: expected ${selector} to contain [${expected}]. Body excerpt:\n${bodyText}\n${error.message}`);
  });
}
