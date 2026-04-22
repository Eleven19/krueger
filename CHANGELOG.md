# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project uses tags like `v0.1.0` while changelog versions omit the
leading `v`.

## [Unreleased]

### Added

- Scala Native 0.5.11 cross-build target for `krueger-core` (`krueger.core.native`).
- Comprehensive unit test suite for `krueger-core` under `krueger/core/test/src/` using ZIO Test. Sources are shared across JVM, Scala.js, and Scala Native via per-platform `sources` overrides on each test submodule. Covers `Span`, `ElmLexer` primitives (identifiers, operators, keywords, literals, enclosers, commaSep, line & nested block comments), `ModuleParser`, `CstLowering` (including paren-pattern stripping and the `<pattern>` fallback name in `lowerLetBinding`), both visitor systems (dispatch, traversal, and `@targetName`-guarded extension methods), and the top-level `Krueger.parse{Cst,Ast}` API.
- BDD integration test module `krueger.itest` (JVM-only) powered by cucumber-scala on the JUnit 5 platform engine (`io.cucumber:cucumber-scala_3:8.39.1`, `cucumber-junit-platform-engine:7.20.1`, `cucumber-picocontainer` for step-definition DI). Feature files at `krueger/itest/resources/features/` drive a `TestDriver` through per-concern step-definition classes (`ModuleParserSteps`, `DeclarationSteps`, `ExpressionSteps`, `PatternSteps`, `LoweringSteps`). Run with `./mill krueger.itest`.

### Changed

- Bumped minimum supported JDK to 25 (Temurin).
- Bumped Scala to 3.8.3.
- Adopted Mill's `PlatformScalaModule` for the cross-platform `krueger-core` module. Shared sources remain at `krueger/core/src/`; each platform can add its own `krueger/core/src-jvm/`, `src-js/`, or `src-native/` directory when platform-specific code is needed.
- `ModuleParser.qualifiedName` now parses dot-separated qualified names (`Data.List`, `Http.Body`) instead of requiring space-separated upper identifiers.

### Fixed

- `ElmLexer` now targets the parsley 4.6.x API: `SpaceDesc.copy` uses the `commentLine` / `commentStart` / `commentEnd` / `nestedComments` parameter names, `floating.decimalDouble` replaces the non-existent `floating.doubleDecimal`, and the deprecated `lexer.lexeme.numeric`, `lexer.lexeme.enclosing`, and `lexer.lexeme.separators` wrappers are replaced with the flattened `lexer.lexeme.*` API.
- `CstVisitor` and `AstVisitor` extension methods (`visit`, `children`, `count`, `fold`, `collect`) are disambiguated from the matching `object` methods with `@targetName` annotations so the after-erasure signatures do not clash.
- Build now actually compiles the `krueger-core` sources on every platform. Previously the default source layout looked for `krueger/core/jvm/src/` (etc.) which did not exist, so `./mill __.compile` reported success without compiling user code.
- Parser backtracking: `ElmLexer.lowerIdentifier` and `upperIdentifier` are now `atomic`, so a case-filter rejection no longer leaves an identifier half-consumed and able to wreck surrounding alternatives (e.g. `import Html\n\nmain = 42` now parses — the second `main` used to be eaten by `some(upperIdentifier)` in a qualified name). `DeclarationParser.declaration` makes `typeAliasDeclaration` atomic so `type alias ...` and `type Maybe ...` cleanly fall through to `customTypeDeclaration`. `ExpressionParser.unitLit`, `PatternParser.unitPat`, and `DeclarationParser.recordType`'s optional extension variable, and the `exposing (..)` branch of `ModuleParser.exposingList`, are now atomic so shared-prefix ambiguities (`()` vs `(expr)`, `(x)` pattern, `{ r | … }` vs `{ f = v }`, `(..)` vs `(items)`) backtrack cleanly.

### Documentation

- README now documents the Scala Native target, the Scala Native toolchain requirements, and the bumped JDK 25 requirement.
- README adds a Testing section covering the cross-platform unit-test layout and the cucumber-based `krueger.itest` workflow.

### CI

- CI, release, publish, and Copilot setup workflows now use JDK 25.
- New `build-native` job compiles and runs tests on the Scala Native target.
- Release and publish jobs install the Scala Native toolchain (clang, libunwind, libgc) so native artifacts are produced alongside JVM and Scala.js ones.
- `build` job now runs `krueger.itest` after the JVM unit tests; `lint` also checks formatting of the itest module.

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
