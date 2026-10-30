#!/usr/bin/env bash
# Qualification is tied to this checkout and never accepts cached test success.
set -euo pipefail
./sbt --batch scalafmtCheckAll scalafmtSbtCheck qualifyAll site
node --test scripts/tests/site-staging.test.mjs scripts/tests/ci-results.test.mjs scripts/tests/publish.test.mjs scripts/tests/workflows.test.mjs scripts/tests/test-reports.test.mjs scripts/tests/designs.test.mjs
npm --prefix docs run test:build-orchestration
npm --prefix docs run test:try-wasm-deploy-workflow
npm --prefix docs exec -- playwright install --with-deps chromium
npm --prefix docs run test:webapp-wasm-browser -- -- target/out/linked/webapp-js
npm --prefix docs run test:wasm-facade-smoke
