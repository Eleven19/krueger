package io.eleven19.krueger.log

import kyo.*
import kyo.test.*

class ScribeLogHandlerSpec extends Test[Any]:

    "ScribeLogHandler / InMemoryLogRecorder" - {

        "InMemoryLogRecorder captures every level emitted via Kyo Log" in {
            val recorder = InMemoryLogRecorder.unsafeMake()
            val program =
                Log.let(InMemoryLogRecorder.layer(recorder)) {
                    for
                        _ <- Log.trace("trace-msg")
                        _ <- Log.debug("debug-msg")
                        _ <- Log.info("info-msg")
                        _ <- Log.warn("warn-msg")
                        _ <- Log.error("error-msg")
                    yield ()
                }
            program.andThen(Log.flush).map { _ =>
                val events = recorder.snapshot()
                assert(events.map(_.message) == List("trace-msg", "debug-msg", "info-msg", "warn-msg", "error-msg"))
            }
        }

        "InMemoryLogRecorder preserves emission order" in {
            val recorder = InMemoryLogRecorder.unsafeMake()
            val program =
                Log.let(InMemoryLogRecorder.layer(recorder)) {
                    for
                        _ <- Log.info("first")
                        _ <- Log.info("second")
                        _ <- Log.info("third")
                    yield ()
                }
            program.andThen(Log.flush).map { _ =>
                val events = recorder.snapshot()
                assert(events.map(_.message) == List("first", "second", "third"))
            }
        }

        "ScribeLogHandler does not throw on every level" in {
            val program =
                Log.let(ScribeLogLayer.default) {
                    for
                        _ <- Log.trace("trace-msg")
                        _ <- Log.debug("debug-msg")
                        _ <- Log.info("info-msg")
                        _ <- Log.warn("warn-msg")
                        _ <- Log.error("error-msg")
                    yield ()
                }
            program.andThen(Log.flush).map(_ => succeed)
        }
    }
