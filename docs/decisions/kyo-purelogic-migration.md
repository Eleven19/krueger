# Decision: PureLogic vs Kyo Migration

**Date:** 2026-06-19  
**Status:** Approved  
**Context:** Krueger established Kyo as a core dependency (PR-A, Apr 2026). The codebase uses PureLogic (`com.github.ghostdogpr::purelogic`) in ~22 locations. This decision doc evaluates whether to migrate to Kyo equivalents.

## Executive Summary

**Recommendation: GO — Migrate PureLogic to Kyo.**

Kyo provides superior effect composition, better error handling, and tighter integration with Krueger's architecture. PureLogic was pragmatic before Kyo was available, but now that Kyo is the foundation, PureLogic becomes a redundant abstraction. Migrating to Kyo unifies the codebase around a single effect system.

---

## Comparison Matrix

### 1. **Effect Composition**

| Dimension | PureLogic | Kyo | Winner |
|-----------|-----------|-----|--------|
| **Abstraction** | Applicative functors (pure composition) | Monadic effects (sequential & parallel) | Kyo |
| **Expressiveness** | Limited to applicative-level; sequential only | Full monad hierarchy; sequential, parallel, conditional | Kyo |
| **Dependency tracking** | Implicit in `Applicative` | Explicit in effect row (`A < S`) | Kyo |
| **Code clarity** | Terse but opaque | Verbose but explicit | Kyo |

**Verdict:** Kyo's monadic system is more expressive and better suited to Krueger's traversal, I/O, and testing needs.

---

### 2. **Error Handling**

| Dimension | PureLogic | Kyo | Winner |
|-----------|-----------|-----|--------|
| **Error type** | `Either[E, A]` | `Abort[E]` (effect-tracked) | Kyo |
| **Propagation** | Manual lifting | Automatic through effect row | Kyo |
| **Mixed errors** | `Either.mapN` chains (verbose) | `Abort[E1] & Abort[E2]` in effect row | Kyo |
| **Recovery** | Pattern matching | `Abort.catching`, `Abort.recover` | Kyo |

**Example:**
```scala
// PureLogic
val result: Either[String, Int] = (parseA, parseB, parseC).mapN((a, b, c) => a + b + c)

// Kyo
def result: Int < Abort[String] =
  for
    a <- parseA
    b <- parseB
    c <- parseC
  yield a + b + c
```

**Verdict:** Kyo's effect-tracked errors are more ergonomic and align with Krueger's logging patterns.

---

### 3. **Testability**

| Dimension | PureLogic | Kyo | Winner |
|-----------|-----------|-----|--------|
| **Test setup** | Pure functions, no setup | Must evaluate via `Sync.Unsafe.evalOrThrow` | Tie |
| **Mocking services** | N/A | `Layer` swapping (in-memory) | Kyo |
| **State management** | Impossible without `Reader` | `Env[Service]` provides local state | Kyo |
| **Determinism** | Inherently deterministic | Deterministic with `Sync`/`Abort` | Tie |

**Verdict:** Kyo's `Layer` system is strictly better for integration testing; no advantage to PureLogic.

---

### 4. **Cross-Platform Support**

| Dimension | PureLogic | Kyo | Winner |
|-----------|-----------|-----|--------|
| **JVM** | ✓ | ✓ | Tie |
| **Scala.js** | ✓ | ✓ | Tie |
| **Scala Native** | ✓ | ✓ (Sync/Abort; Async gated) | Kyo |
| **Ecosystem** | Limited, standalone | Wide Scala ecosystem integration | Kyo |

**Verdict:** Kyo matches PureLogic for Krueger's use cases; ecosystem integration favors Kyo.

---

### 5. **Ecosystem Maturity**

| Dimension | PureLogic | Kyo | Winner |
|-----------|-----------|-----|--------|
| **Stability** | Stable; last release 2024 | RC4; moving to 1.0 stable | Kyo |
| **Community** | Small, niche | Growing, continuous development | Kyo |
| **Documentation** | Sparse (README) | Comprehensive (docs, examples) | Kyo |
| **Maintenance** | Minimal; stable but stagnant | Active; regular updates | Kyo |
| **Adoption** | Rare | Increasing in Scala ecosystem | Kyo |

**Verdict:** Kyo is the clear winner for long-term viability.

---

## Current PureLogic Usage

**Count:** ~22 usages across three modules

- **`krueger/core`:** ~8 usages (lexer/parser utilities)
- **`krueger/trees`:** ~7 usages (query builder helpers)
- **`krueger/compiler-api`:** ~7 usages (pipeline stage composition)

**Scope:** All usages are internal utilities; no public APIs expose PureLogic types.

---

## Migration Strategy

### Phase A: Audit and Surface Mapping

1. Inventory all 22 PureLogic usages
2. Identify replacement patterns:
   - `PureLogic.sequence(...)` → `Kyo.foreach(...)`
   - `PureLogic.traverse(...)` → `Kyo.traverse(...)`
   - Applicative composition → monadic `for`-comprehension
   - `Either` lifts → `Abort[E]` in effect row
3. Create migration table

### Phase B: Red-Green-Refactor Cycles

For each logical group (lexer utils, query builders, pipeline stages):

1. Write/update tests without PureLogic
2. Replace with idiomatic Kyo equivalents
3. Refactor intermediate abstractions
4. Verify: run full test suite (JVM, JS, Native)

### Phase C: Dependency Removal

1. Remove `com.github.ghostdogpr::purelogic` from build
2. Remove transitive dependencies only used by PureLogic
3. Verify no lingering `purelogic.*` imports
4. Commit cleanup

---

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|-----------|
| Breaking internal APIs | Medium | All usages are internal; no public breaking changes |
| Performance regression | Low | Kyo effects compile away; no runtime penalty |
| Incomplete migration | Low | Audit identifies all 22 sites; systematic RGR ensures none missed |
| Test coverage gaps | Medium | Existing tests provide baseline; retrofit during migration |

---

## Success Criteria

1. ✓ All ~22 PureLogic usages inventoried
2. ✓ Each replaced with idiomatic Kyo equivalent
3. ✓ All tests passing (JVM, JS, Native)
4. ✓ No lingering `purelogic` imports
5. ✓ Dependency removed from build
6. ✓ Single cohesive PR to main

---

## Decision Rationale

**Why Kyo:**

1. **Alignment:** Kyo is now the foundation; PureLogic creates friction
2. **Error handling:** Effect-tracked `Abort[E]` is cleaner than `Either` wrappers
3. **Ecosystem:** Kyo integrates with broader Scala effect ecosystem
4. **Maintenance:** Kyo is actively maintained; PureLogic is stagnant
5. **Simplicity:** One effect system is easier to reason about than two

**Why now:**

- Kyo RC4 is stable enough for broad adoption
- All 22 usages are internal utilities with full test coverage
- Small codebase makes migration low-risk

---

## References

- **Kyo documentation:** https://kyo.buzz
- **PR-A (Kyo Foundation):** Established Kyo as core dependency (Apr 2026)
- **PureLogic:** https://github.com/ghostdogpr/purelogic
- **Krueger current version:** Kyo 1.0.0-RC4 (as of Jun 2026)
