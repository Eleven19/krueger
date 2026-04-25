# Kyo Service Pattern (Convention)

This document defines how Krueger code declares, consumes, and provides
services using Kyo's effect system. Every new service in the project
follows this pattern.

> **Kyo API note.** Krueger pins the Kyo SNAPSHOT
> (`>= 1.0-RC1+214-534321a9-SNAPSHOT`). On this snapshot the legacy `IO`
> effect was renamed to `Sync` (with `IO` deprecated). Throughout this doc
> and in all production code, prefer `Sync` over `IO`.

## Three rules

1. **Contract first.** A service is a `trait` whose methods return Kyo
   effect-tracked types (`A < S`). Methods declare exactly what effects
   they need (e.g., `Sync`, `Abort[E]`, `Env[Other]`).
2. **Consume via `Env`.** Callers depend on `Env[Service]` in the effect
   row, never on a concrete impl. The compiler tracks the dependency so
   forgetting to provide a layer is a compile error.
3. **Provide via `Layer`.** Production code wires layers using
   `Layer.init[Target](layers*)` (or a direct `Layer { ... }`
   constructor). Tests swap in an in-memory layer with the same shape.

## Logging is a service

All in-process logging flows through Kyo's `Log` effect. The production
binding (`ScribeLogLayer.default`) wires a scribe-backed `Log.Unsafe`
handler; tests wire `InMemoryLogRecorder.layer(recorder)` to capture
emissions for assertion. Service code never imports `scribe` directly.

`Log` is special-cased by Kyo: instead of `Env[Log]`, callers swap the
handler with `Log.let(log)(...)`. The factories in
`io.eleven19.krueger.log` return a `Log` value ready to pass to
`Log.let`:

- `ScribeLogLayer.default: Log` — production handler over scribe's root
  logger.
- `ScribeLogLayer.forLogger(logger): Log` — production handler over a
  named/configured scribe logger.
- `InMemoryLogRecorder.layer(recorder): Log` — test handler that
  appends every emission to the supplied recorder.

For application services other than logging, follow the
contract / `Env` / `Layer` rules.

## Worked example

The example below defines a `Counter` service that increments an
in-memory counter and logs each step. It is wired with the production
log binding for the live program and the in-memory recorder for the
test.

### Contract

```scala
package io.eleven19.krueger.example

import kyo.*

// Step 1: Contract — a trait whose methods return Kyo effect-typed values.
// `Sync` covers the side-effecting bump; `Log` is implicit through the
// in-scope handler installed by `Log.let`.
trait Counter:
    def increment: Unit < Sync
    def value: Int < Sync
```

### Consume via `Env`

```scala
import kyo.*

// Callers depend on `Env[Counter]`, never on a concrete impl.
// The effect row also tracks `Sync` for the in-process counter and the
// log emissions performed by the impl.
def runWork: Int < (Sync & Env[Counter]) =
    for
        c <- Env.get[Counter]
        _ <- c.increment
        _ <- c.increment
        n <- c.value
        _ <- Log.info(s"counter=$n")
    yield n
```

### Provide via `Layer` (production)

```scala
import kyo.*
import io.eleven19.krueger.log.ScribeLogLayer

val productionCounter: Layer[Counter, Any] =
    Layer {
        new Counter:
            private val ref = java.util.concurrent.atomic.AtomicInteger(0)

            def increment: Unit < Sync =
                Sync.defer {
                    ref.incrementAndGet()
                    ()
                }.map(_ => Log.debug(s"counter incremented to ${ref.get}"))

            def value: Int < Sync =
                Sync.defer(ref.get)
    }

// Compose `Counter` (and any other Env-tracked services) with `Layer.init`.
// `Log` is provided separately via `Log.let`, not as a layer.
val productionLayer: Layer[Counter, Any] =
    Layer.init[Counter](productionCounter)

// Run the program: install the production log handler, then provide
// the production layers via `Env.runLayer`.
val program: Int < Sync =
    Log.let(ScribeLogLayer.default) {
        Memo.run(Env.runLayer(productionLayer)(runWork))
    }
```

### Provide via `Layer` (tests)

The test wiring reuses the production `Counter` impl and only swaps
the `Log` handler for the in-memory recorder. Library code is identical
between the two runs — only the bindings change.

```scala
import kyo.*
import io.eleven19.krueger.log.InMemoryLogRecorder

val recorder = InMemoryLogRecorder.unsafeMake()

val testProgram: Int < Sync =
    Log.let(InMemoryLogRecorder.layer(recorder)) {
        Memo.run(Env.runLayer(productionLayer)(runWork))
    }

// At the test boundary, evaluate the Kyo program and assert on
// `recorder.snapshot()` — the captured `LogRecord`s are deterministic
// and ordered by emission.
val n: Int =
    Sync.Unsafe.evalOrThrow(testProgram)(using
        summon[Frame],
        AllowUnsafe.embrace.danger
    )

assert(n == 2)
assert(recorder.snapshot().map(_.message).contains("counter=2"))
```

The same `Sync.Unsafe.evalOrThrow(...)` shape evaluates the production
`program` at any host boundary (CLI `main`, JVM tests, the playground
runner, the wasm shell).

## Why these rules

- **Contracts in the type system.** `Env[Service]` and `Sync` in the
  effect row mean forgetting to provide a layer or an unsafe runner is
  a compile error, not a runtime null.
- **Layers are values.** `Layer.init` composes them and `Env.runLayer`
  applies a set of layers at a specific call site. Layers can be swapped
  at any boundary — production, test, or future host (CLI, wasm,
  playground).
- **No global state.** Every service is provided locally to the
  computation that needs it. There is no service locator and no
  `ServiceLoader`.
- **Logging is observable but decoupled.** Library code says
  `Log.info(...)`; the host (CLI, JVM tests, browser) decides via
  `Log.let` what backend receives the emissions. Library code never
  imports a backend.

## Adding a new service

1. **Define the `trait`** in the module that owns the contract. Methods
   return `A < S` with the smallest effect row that captures the work.
2. **Add at least two layers** in the same module:
   - a production layer (`Layer { ... }` or `Layer.init[...]`),
   - a test layer (in-memory or fake) with identical contract.
3. **Cross-platform check.** If the service is consumed on JVM, JS, and
   Native, ensure the trait's methods compile on all three (most Kyo
   effects support all three; effects that don't, such as `Async` on
   Native, must be gated platform-by-platform).
4. **Document the service.** Add an entry under `docs/cookbook/` per
   the value-unlock-docs convention, linking back to this doc as the
   pattern reference. The cookbook entry should show a minimal
   end-to-end snippet (contract → call site → production layer → test
   layer) the way `Counter` is shown above.
5. **Reference for `Log` specifically.** New services that emit logs
   should call `Log.info` / `Log.debug` / etc. directly — they do not
   need their own `Env[Log]` parameter. The host installs the handler
   once with `Log.let` at the program edge.
