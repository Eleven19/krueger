---
title: Contributing
description: How to build, test, and submit changes to Krueger.
---

## Build

```sh
# Compile (JVM)
mill krueger.core.jvm.compile

# Compile for Scala.js
mill krueger.core.js.compile

# Compile for Scala Native
mill krueger.core.native.compile
```

## Test

```sh
# ZIO Test unit suites (cross-platform)
mill krueger.core.jvm.test
mill krueger.core.js.test
mill krueger.core.native.test

# cucumber-scala BDD integration tests (JVM)
mill krueger.itest
```

## Docs site

The handbook under `docs/` is **Starlight / Astro** with a **Laminar** playground
at [`/krueger/try/`](https://eleven19.github.io/krueger/try/) and a **SvelteKit +
WASM** playground at
[`/krueger/try-wasm/`](https://eleven19.github.io/krueger/try-wasm/). GitHub
Pages runs the same pipeline as `./mill docs.site`: Scaladoc, Laminar bundle,
WASM artifacts for try-wasm, SvelteKit production build, Astro build, then a
copy of `sites/try-wasm/build/` into `docs/dist/try-wasm/`.

### Local dev (full site, including `/try-wasm/`)

`astro dev` only sees `/krueger/try-wasm/` if the SvelteKit app is built and
mirrored into `docs/public/try-wasm/` (gitignored). The one-step prep is:

```sh
cd docs
npm ci
npm run dev:full    # ./mill docs.prepareLocalDevSite && astro dev
```

Open `http://localhost:4321/krueger/`, `/krueger/try/`, and `/krueger/try-wasm/`.

### Production-sized artifact / GitHub Pages parity

Build everything exactly as deploy does:

```sh
./mill docs.site
```

Output: `docs/dist/` (including `dist/try-wasm/`). Equivalent npm orchestration:

```sh
cd docs && npm ci && npm run site:build   # alias for build:full — Mill + try-wasm + Astro + stitch
```

Preview the static tree:

```sh
cd docs && npm run preview   # serves docs/dist/
```

### Under the hood

1. **Scaladoc** for JVM, Scala.js, and Scala Native is emitted into
   `docs/public/api/{jvm,js,native}/` (gitignored):

   ```sh
   ./mill docs.writeToDocsPublic
   ./mill docs.apiJvm
   ./mill docs.apiJs
   ./mill docs.apiNative
   ```

2. **Laminar playground** — `./mill krueger.webapp.writeToDocsSrc` writes the
   Scala.js bundle consumed by `docs/src/pages/try.astro`.

3. **try-wasm** — `./mill krueger.webapp-wasm.writeToWasmSite` populates
   `sites/try-wasm/static/wasm/` before `npm run build` in `sites/try-wasm`.

4. **Astro** — `docs/public/` (API HTML, optional `try-wasm/` mirror during dev)
   is copied into `docs/dist/` on `npm run build`.

### Playwright checks

From `docs/` after `npm ci`:

```sh
npx playwright install
npm run test:playground-e2e
```

## Workflow

Krueger follows strict Red-Green-Refactor TDD. Open an issue (or a `bd`
ticket) before starting non-trivial work, then submit a PR against `main`.

See the top-level [`CLAUDE.md`](https://github.com/Eleven19/krueger/blob/main/CLAUDE.md)
and [`AGENTS.md`](https://github.com/Eleven19/krueger/blob/main/AGENTS.md)
for the full engineering conventions.
