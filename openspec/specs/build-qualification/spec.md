# build-qualification Specification

## Purpose

Contributors and release automation can qualify the complete compiler and its generated artifacts using reproducible commands.

## Requirements

### Requirement: REQ-BUILD-001 Complete platform qualification
When a contributor requests full qualification, the build SHALL compile and execute the existing JVM, JavaScript, Native, WebGC and BDD suites with preserved sources and resources. (bead: krueger-3x2.1)

#### Scenario: Full platform run
- **WHEN** full qualification runs on a clean checkout
- **THEN** every supported suite executes and reports nonzero test discovery

#### Scenario: Missing tests
- **WHEN** a required suite has no discovered tests or a test fails
- **THEN** qualification fails and identifies the project

#### Scenario: Warm cache
- **WHEN** full qualification repeats without source changes
- **THEN** tests execute again rather than accepting cached test success

### Requirement: REQ-BUILD-002 Generated artifact parity
When documentation or playground staging runs, the build SHALL produce the JS facade, WebGC loader and binary, all three combined API trees and the stitched site. (bead: krueger-3x2.2)

#### Scenario: Full site
- **WHEN** the full site command succeeds
- **THEN** its output contains the landing page, API trees and playground with both compiler backends

#### Scenario: Missing producer
- **WHEN** a required linked file or documentation tree is absent
- **THEN** staging fails before replacing the previous destination

#### Scenario: Removed output
- **WHEN** staged outputs are deleted after a successful build
- **THEN** repeating staging restores them and removes stale files and source map references

### Requirement: REQ-BUILD-003 Candidate and publication gates
If selected build or test work fails, is cancelled or is unexpectedly skipped, then the aggregate gate SHALL fail. Publication SHALL preserve coordinates, metadata, version and credential boundaries and require qualification of its candidate. (bead: krueger-3x2.3)

#### Scenario: Local packaging
- **WHEN** packaging runs without publication credentials
- **THEN** local jars, sources, documentation and POMs preserve the supported platform identities

#### Scenario: Failed candidate
- **WHEN** any selected prerequisite fails, is cancelled or is unexpectedly skipped
- **THEN** merge or publication qualification fails

#### Scenario: Release isolation
- **WHEN** a contributor validates local packaging
- **THEN** no remote publication or release creation occurs

### Requirement: REQ-BUILD-004 Contributor migration guide
When contributors follow onboarding, the documentation SHALL provide runnable setup, build, format, full-test, link, site and local-publish commands plus a complete integration recipe. (bead: krueger-3x2.4)

#### Scenario: Developer recipe
- **WHEN** a contributor follows the documented recipe
- **THEN** they can test a library change and inspect it through the playground
