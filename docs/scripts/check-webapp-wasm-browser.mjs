#!/usr/bin/env node

import { access, readFile } from 'node:fs/promises';
import { createServer } from 'node:http';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { chromium } from 'playwright';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(here, '..', '..');
const defaultFacadeDir = resolve(repoRoot, 'out', 'krueger', 'webapp-wasm', 'fullLinkJS.dest');
const facadeArg = process.argv.find((arg, index) => index > 1 && arg !== '--');
const configuredFacadeDir = facadeArg ?? process.env.KRUEGER_WEBAPP_WASM_FACADE_DIR;
const facadeDir = configuredFacadeDir
    ? resolve(repoRoot, configuredFacadeDir)
    : defaultFacadeDir;
const entrypoint = join(facadeDir, 'main.js');

function fail(message) {
    console.error(`check-webapp-wasm-browser: FAIL - ${message}`);
    process.exit(1);
}

await access(entrypoint).catch(() => {
    fail(`missing ${entrypoint}; run \`./mill krueger.webapp-wasm.fullLinkJS\` first`);
});

const contentTypes = new Map([
    ['.html', 'text/html; charset=utf-8'],
    ['.js', 'text/javascript; charset=utf-8'],
    ['.map', 'application/json; charset=utf-8'],
]);

const server = createServer(async (req, res) => {
    const url = new URL(req.url ?? '/', 'http://127.0.0.1');
    const pathname = url.pathname === '/' ? '/index.html' : url.pathname;
    const filePath = pathname === '/index.html' ? null : join(facadeDir, pathname);

    try {
        if (filePath === null) {
            res.setHeader('content-type', 'text/html; charset=utf-8');
            res.end('<!doctype html><meta charset="utf-8"><title>Krueger Browser Parity</title>');
            return;
        }

        const body = await readFile(filePath);
        const ext = filePath.slice(filePath.lastIndexOf('.'));
        res.setHeader('content-type', contentTypes.get(ext) ?? 'application/octet-stream');
        res.end(body);
    } catch {
        res.statusCode = 404;
        res.end('not found');
    }
});

await new Promise((resolveListen) => server.listen(0, '127.0.0.1', resolveListen));

try {
    const { port } = server.address();
    const browser = await chromium.launch({ headless: true });
    try {
        const page = await browser.newPage();
        await page.goto(`http://127.0.0.1:${port}/`);
        const result = await page.evaluate(async () => {
            const mod = await import('/main.js');
            const krueger = mod.Krueger ?? globalThis.Krueger;
            const valid = krueger.parseCst('module Demo exposing (..)\n\nmain = 42\n');
            const malformed = krueger.parseCst('module Demo exposing (..)\n\nmain =\n');
            const again = krueger.parseCst('module Demo exposing (..)\n\nmain = 42\n');
            return {
                validOk: valid.ok === true,
                validValue: String(valid.value),
                malformedOk: malformed.ok === false,
                malformedErrorCount: malformed.errors.length,
                malformedPhase: malformed.errors[0]?.phase,
                malformedMessage: malformed.errors[0]?.message,
                deterministic: String(valid.value) === String(again.value),
            };
        });

        if (!result.validOk || !result.validValue.includes('CstModule(')) fail(`valid parseCst failed: ${JSON.stringify(result)}`);
        if (!result.malformedOk || result.malformedErrorCount < 1) fail(`malformed parseCst did not return errors: ${JSON.stringify(result)}`);
        if (result.malformedPhase !== 'cst') fail(`malformed parseCst phase mismatch: ${JSON.stringify(result)}`);
        if (!String(result.malformedMessage).includes('unexpected end of input')) {
            fail(`malformed parseCst message mismatch: ${JSON.stringify(result)}`);
        }
        if (!result.deterministic) fail(`repeated parseCst was not deterministic: ${JSON.stringify(result)}`);
    } finally {
        await browser.close();
    }
} finally {
    await new Promise((resolveClose) => server.close(resolveClose));
}

console.log('check-webapp-wasm-browser: OK - Chromium loaded the Scala.js compiler facade and matched BDD compiler semantics.');
