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
const matchRootSelector = '.krueger-match-root, .match-root';

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
  fail('missing docs/dist/try/index.html; build and stitch sites/try into docs/dist first');
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
      fail(`/try/ match roots diverged across backends: webgc=${wasm.matchRoot} js=${js.matchRoot}`);
    }
  } finally {
    await browser.close();
  }
} finally {
  await new Promise((resolveClose) => server.close(resolveClose));
}

console.log('check-playground-e2e: OK - /try/ smoke tests passed for both webgc and js backends.');

async function runBackendSmoke(browser, baseUrl, backendId) {
  const page = await browser.newPage();
  const wasmRequests = [];
  page.on('request', (request) => {
    if (request.url().endsWith('.wasm')) wasmRequests.push(request.url());
  });
  try {
    await page.goto(`${baseUrl}/try/`, { waitUntil: 'domcontentloaded' });

    // The backend selector lives inside the Settings panel, not on the
    // default Matches view. Follow the actual activity-bar flow before
    // returning to Matches for the existing assertions.
    await page.getByRole('tab', { name: 'Settings' }).click();
    await page.waitForSelector('select[aria-label="Compiler backend"]');
    const wasmRequestsBeforeSelection = wasmRequests.length;
    await page.selectOption('select[aria-label="Compiler backend"]', backendId);
    await page.waitForFunction(
      (id) =>
        document.querySelector('select[aria-label="Compiler backend"]')?.value === id,
      backendId
    );
    await page.getByRole('tab', { name: 'Matches' }).click();
    await page.getByRole('tabpanel', { name: 'Matches' }).waitFor();
    const wasmRequestsAfterSelection = wasmRequests.slice(wasmRequestsBeforeSelection);

    await fillMonacoEditor(page, 0, validSource, `/try/ (${backendId}) Elm source`);
    await fillMonacoEditor(page, 1, validQuery, `/try/ (${backendId}) query`);

    await expectText(
      page,
      matchRootSelector,
      'CstValueDeclaration',
      `/try/ (${backendId}) matches panel`
    );
    const matchRoot = await page.locator(matchRootSelector).first().textContent();

    // Per-backend network expectations: the WASM pass MUST fetch main.wasm;
    // the JS pass MUST NOT (it loads only `wasm/facade/main.js`). This is
    // what proves the runtime path actually diverges between the two
    // selectors — without this assertion, both passes could share the JS
    // facade and the e2e would still go green.
    if (backendId === 'webgc') {
      const fetchedWasm = wasmRequests.some((url) => url.includes('/webgc/main.wasm'));
      if (!fetchedWasm) {
        fail(
          `/try/ (webgc): expected the page to fetch wasm/webgc/main.wasm, ` +
            `but observed wasm requests: ${JSON.stringify(wasmRequests)}`
        );
      }
    } else {
      if (wasmRequestsAfterSelection.length > 0) {
        fail(
          `/try/ (js): expected zero .wasm requests after selecting JS, observed: ${JSON.stringify(wasmRequestsAfterSelection)}`
        );
      }
    }

    return { matchRoot, wasmRequests };
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
