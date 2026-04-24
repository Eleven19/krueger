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

The Starlight site under `docs/` is built by **two** tools in sequence:

1. **Mill** generates Scaladoc HTML for each platform and mirrors it into
   `docs/public/api/{jvm,js,native}/` plus a landing page at
   `docs/public/api/index.html`:

   ```sh
   # Build all three Scaladoc trees + landing page.
   ./mill docs.writeToDocsPublic

   # Individual trees (for iteration):
   ./mill docs.apiJvm
   ./mill docs.apiJs
   ./mill docs.apiNative
   ```

   The output under `docs/public/api/` is gitignored — always regenerated.

2. **Astro / Starlight** bundles the Markdown content and copies
   `docs/public/` verbatim into `docs/dist/`:

   ```sh
   cd docs
   npm ci
   npm run build   # -> docs/dist/
   npm run dev     # local preview at http://localhost:4321/krueger/
   ```

For a combined local preview, run the Mill task first, then `npm run dev` (or
`npm run build && npm run preview`).

## Workflow

Krueger follows strict Red-Green-Refactor TDD. Open an issue (or a `bd`
ticket) before starting non-trivial work, then submit a PR against `main`.

See the top-level [`CLAUDE.md`](https://github.com/Eleven19/krueger/blob/main/CLAUDE.md)
and [`AGENTS.md`](https://github.com/Eleven19/krueger/blob/main/AGENTS.md)
for the full engineering conventions.
