# Proposal

## Why

Contributors need sbt 2 tooling without losing the existing cross-platform compiler, playground, documentation or release behavior. This change replaces the active Mill build with the latest stable sbt 2.0 release.

## What Changes

- **BREAKING**: Replace Mill commands with pinned sbt 2.0.11 projects and documented task mappings.
- Preserve Scala 3.9.0, Kyo RC7, Scala.js 1.22.0, Native 0.5.12 and JDK 25, source layouts, compiler flags and Maven identities.
- Qualify all existing unit/BDD suites explicitly with full tests, detect empty discovery and verify restored generated outputs.
- Migrate site staging, npm callers, CI, caches and publication while retaining triggers and secrets.
- Provide contributor onboarding, integration examples and an end-to-end recipe.

## Capabilities

### New Capabilities

- `build-qualification`: Cross-platform build, test discovery, candidate qualification, artifact staging and local packaging contracts.

### Modified Capabilities

None. Language and library behavior is preserved.

## Beads

- **Tracked by**: `krueger-3x2`
- **Related**: `krueger-3x2.1` through `krueger-3x2.4` implement build, site, automation and landing groups.

## Impact

Build definitions and wrappers, project plugins, CI/release workflows, npm entry points, generated-artifact consumers, README and contributor documentation. No real release is published as validation.
