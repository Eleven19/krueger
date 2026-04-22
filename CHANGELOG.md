# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project uses tags like `v0.1.0` while changelog versions omit the
leading `v`.

## [Unreleased]

### Added

### Changed

### Fixed

### Documentation

### CI

## [0.1.0] - 2026-04-22

### Added

- Initial project scaffolding: Mill 1.1.5 build with Scala 3, JVM + Scala.js core modules, and `io.eleven19.krueger` Maven Central publishing support.
- GitHub Actions CI and release workflows (CI, Release, Publish Maven Central, Publish SNAPSHOT, Copilot Setup Steps).

### Changed

### Fixed

### Documentation

### CI

- Added CI workflow compiling and testing JVM and Scala.js modules.
- Added release and publish workflows backed by shared `scripts/ci/*` helpers and a composite `release-metadata` action.
