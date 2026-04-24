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

The full site (docs + Scaladoc for JVM, Scala.js, Scala Native) builds in one
command:

```sh
./mill docs.site        # generates Scaladoc, runs `npm ci` + `npm run build`
```

Or, from `docs/` using the Node toolchain only:

```sh
cd docs
npm run build:full      # same as ./mill docs.site, but npm-driven
```

Both produce the final publishable artifact at `docs/dist/` — which is what
the `Deploy Docs` workflow uploads to GitHub Pages.

### Iteration shortcuts

When you only need part of the build:

```sh
./mill docs.writeToDocsPublic   # Scaladoc only (JVM + JS + Native)
./mill docs.apiJvm              # JVM tree only
./mill docs.apiJs               # Scala.js tree only
./mill docs.apiNative           # Scala Native tree only

cd docs && npm run dev          # hot-reload preview at http://localhost:4321/krueger/
cd docs && npm run build        # Astro only, assumes Scaladoc already present
cd docs && npm run preview      # serve the built docs/dist/ locally
```

The output under `docs/public/api/` is gitignored — always regenerated.

## Workflow

Krueger follows strict Red-Green-Refactor TDD. Open an issue (or a `bd`
ticket) before starting non-trivial work, then submit a PR against `main`.

See the top-level [`CLAUDE.md`](https://github.com/Eleven19/krueger/blob/main/CLAUDE.md)
and [`AGENTS.md`](https://github.com/Eleven19/krueger/blob/main/AGENTS.md)
for the full engineering conventions.
