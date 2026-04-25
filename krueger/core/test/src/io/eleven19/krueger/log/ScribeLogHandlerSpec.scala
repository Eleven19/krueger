package io.eleven19.krueger.log

import kyo.*
import zio.test.*

object ScribeLogHandlerSpec extends ZIOSpecDefault:

    def spec = suite("ScribeLogHandler / InMemoryLogRecorder")(

        test("InMemoryLogRecorder captures every level emitted via Kyo Log"):
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
            val _ = Sync.Unsafe.evalOrThrow(Memo.run(program))(using summon[Frame], AllowUnsafe.embrace.danger)
            val events = recorder.snapshot()
            assertTrue(events.map(_.message) == List("trace-msg", "debug-msg", "info-msg", "warn-msg", "error-msg"))
        ,

        test("InMemoryLogRecorder preserves emission order"):
            val recorder = InMemoryLogRecorder.unsafeMake()
            val program =
                Log.let(InMemoryLogRecorder.layer(recorder)) {
                    for
                        _ <- Log.info("first")
                        _ <- Log.info("second")
                        _ <- Log.info("third")
                    yield ()
                }
            val _ = Sync.Unsafe.evalOrThrow(Memo.run(program))(using summon[Frame], AllowUnsafe.embrace.danger)
            assertTrue(recorder.snapshot().map(_.message) == List("first", "second", "third"))
        ,

        test("ScribeLogHandler does not throw on every level"):
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
            val _ = Sync.Unsafe.evalOrThrow(Memo.run(program))(using summon[Frame], AllowUnsafe.embrace.danger)
            assertCompletes
    )
