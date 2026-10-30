import test from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { readFileSync, existsSync } from 'node:fs';

// Public sbt queries prove the build loads, retains identity and sees real sources/tests.
test('sbt loads the JVM tree library with its existing Maven identity and tests', () => {
  const result = spawnSync('./sbt', ['--batch', 'show sbtVersion', 'show treesJvm/moduleName', 'show treesJvm/Compile/sources', 'show treesJvm/Test/definedTests', 'show treesJvm/resolvers'], { encoding: 'utf8', timeout: 600000, maxBuffer: 8 * 1024 * 1024 });
  assert.equal(result.status, 0, `${result.error ?? ''}\n${result.stdout}\n${result.stderr}`);
  assert.match(result.stdout, /2\.0\.11/);
  assert.match(result.stdout, /krueger-trees/);
  assert.match(result.stdout, /krueger\/trees\/src\/.*Trees.scala/);
  assert.match(result.stdout, /QueryParserSpec/);
  assert.match(result.stdout, /central.sonatype.com\/repository\/maven-snapshots/);
});

test('all platform projects discover their shared suites and integration resources', () => {
  const projects = ['treesJvm', 'treesJs', 'treesNative', 'coreJvm', 'coreJs', 'coreNative', 'compilerApiJvm', 'compilerApiJs', 'compilerApiNative', 'compilerApiWasm', 'webappJs', 'webappWasm', 'integration'];
  const queries = projects.flatMap(p => [`show ${p}/name`, `show ${p}/Test/definedTests`]);
  const result = spawnSync('./sbt', ['--batch', ...queries, 'show integration/Test/resources', 'show compilerApiWasm/Compile/fullLinkJSOutput', 'show coreJs/moduleName'], { encoding: 'utf8', timeout: 600000, maxBuffer: 8 * 1024 * 1024 });
  assert.equal(result.status, 0, `${result.stdout}\n${result.stderr}`);
  for (const p of projects) assert.ok(result.stdout.includes(`[info] ${p}\n`), `${p} query did not execute`);
  for (const suite of ['QueryParserSpec', 'CompilerComponentSpec', 'CucumberSuite', 'ChicoryCompilerWasmBackendTest']) assert.match(result.stdout, new RegExp(suite));
  assert.match(result.stdout, /features\/query.feature/);
});

test('qualification rejects an empty required suite rather than reporting success', () => {
  const result = spawnSync('./sbt', ['--batch', 'set treesJvm / Test / definedTests := Seq.empty', 'treesJvm/verifyTests'], { encoding: 'utf8', timeout: 120000 });
  assert.notEqual(result.status, 0);
  assert.match(result.stdout + result.stderr, /treesJvm: no tests discovered/);
});

test('integration receives linked compiler paths and packaging preserves POM metadata', () => {
  const result = spawnSync('./sbt', ['--batch', 'show integration/Test/javaOptions', 'show coreJvm/makePom', 'show coreJs/makePom', 'show coreNative/makePom'], { encoding: 'utf8', timeout: 600000, maxBuffer: 8 * 1024 * 1024 });
  assert.equal(result.status, 0, `${result.stdout}\n${result.stderr}`);
  assert.match(result.stdout, /-Dkrueger.compiler-api.wasm.dir=.*target\/out\/linked\/compiler-api-wasm/);
  assert.match(result.stdout, /-Dkrueger.webapp-wasm.facade.dir=.*target\/out\/linked\/webapp-js/);
  assert.ok(existsSync("target/out/linked/compiler-api-wasm/main.wasm"), "compiler-api main.wasm was not linked");
  const poms = [...result.stdout.matchAll(/\[info\] (\$\{OUT\}[^\n]+\.pom)/g)].map(m => readFileSync(m[1].trim().replace('${OUT}', `${process.cwd()}/target/out`), 'utf8'));
  assert.equal(poms.length, 3);
  for (const pom of poms) {
    assert.match(pom, /<groupId>io.eleven19.krueger<\/groupId>/);
    assert.match(pom, /Apache-2.0/);
    assert.match(pom, /<id>DamianReeves<\/id>/);
    assert.match(pom, /An Elm dialect parser and compiler toolchain for Scala/);
  }
  for (const suffix of ['_3', '_sjs1_3', '_native0.5_3']) assert.ok(poms.some(p => p.includes(`<artifactId>krueger-core${suffix}</artifactId>`)));

});

test('compileAll preserves the Mill sweep of production, test and API projects', () => {
  const result = spawnSync('./sbt', ['--batch', 'compileAll', 'show integration/Test/sources', 'show apiNative/Compile/sources'], { encoding: 'utf8', timeout: 600000, maxBuffer: 8 * 1024 * 1024 });
  assert.equal(result.status, 0, `${result.stdout}\n${result.stderr}`);
  assert.match(result.stdout, /CucumberSuite.scala/);
  assert.match(result.stdout, /krueger\/trees\/src/);
  assert.match(result.stdout, /krueger\/core\/src/);
});
