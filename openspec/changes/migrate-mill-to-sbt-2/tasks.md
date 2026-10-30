# Tasks

## 1. Build and platform parity (krueger-3x2.1)

- [x] 1.1 Capture Mill task and full-test baseline; add and run failing sbt load, source/resource, discovery and POM contracts before production edits.
- [x] 1.2 Implement pinned sbt projects and frameworks; verify JVM/JS/Native/WebGC full suites, cold/warm behavior and documented local packaging commands.

## 2. Generated artifacts and site (krueger-3x2.2)

- [x] 2.1 Add failing staging contracts for missing producers, stale outputs and source maps, then implement and pass them.
- [x] 2.2 Migrate API/site tasks, npm callers and runtime consumers; verify browser/WebGC smoke, all API roots and complete site, documenting developer composition.

## 3. CI and release automation (krueger-3x2.3)

- [x] 3.1 Add failing workflow/aggregate and publish-boundary contracts; migrate all callers and caches, retaining triggers and secrets, then pass the contracts.
- [x] 3.2 Verify exact-candidate full qualification, unsigned local packaging and signed staging without remote publication; document release operations.

## 4. Contributor docs and integration (krueger-3x2.4)

- [x] 4.1 Verify runnable onboarding and developer recipe; remove active Mill definitions after parity and validate the final repository checks and diff.
- [ ] 4.2 Push a reviewable PR with docs links and validation evidence; verify all current-head CI gates and address verified review findings.

## Workflow follow-up

- Archive/sync OpenSpec and verify local HTML assets.
- Merge through normal protections; verify merge SHA, tree and downstream CI/deployment.
- Close child beads and epic only after their acceptance criteria hold; push tracker state.
