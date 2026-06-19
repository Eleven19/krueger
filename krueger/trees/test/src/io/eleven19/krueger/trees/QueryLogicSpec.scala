package io.eleven19.krueger.trees

import zio.test.*

import io.eleven19.krueger.trees.query.QueryLogic

object QueryLogicSpec extends ZIOSpecDefault:

    private type Ctx = Int
    private type Log = String
    private type Err = String

    def spec = suite("QueryLogic")(
        test("threads and updates context") {
            val result = QueryLogic.run[Ctx, Log, Err, Int](initialContext = 2) {
                for
                    start <- QueryLogic.readContext[Ctx, Log, Err]
                    _     <- QueryLogic.updateContext[Ctx, Log, Err](_ + 5)
                    end   <- QueryLogic.readContext[Ctx, Log, Err]
                yield end - start
            }
            assertTrue(result.value == Right(5), result.context == 7)
        },
        test("accumulates logs in order") {
            val result = QueryLogic.run[Ctx, Log, Err, Unit](initialContext = 0) {
                for
                    _ <- QueryLogic.log[Ctx, Log, Err]("first")
                    _ <- QueryLogic.log[Ctx, Log, Err]("second")
                yield ()
            }
            assertTrue(result.logs == Vector("first", "second"))
        },
        test("accumulates errors while continuing") {
            val result = QueryLogic.run[Ctx, Log, Err, Int](initialContext = 3) {
                for
                    _   <- QueryLogic.error[Ctx, Log, Err]("bad-a")
                    _   <- QueryLogic.error[Ctx, Log, Err]("bad-b")
                    ctx <- QueryLogic.readContext[Ctx, Log, Err]
                yield ctx
            }
            assertTrue(result.value == Left(Vector("bad-a", "bad-b")), result.context == 3)
        },
        test("returns successful value when no errors were emitted") {
            val result = QueryLogic.run[Ctx, Log, Err, Int](initialContext = 10) {
                for
                    _   <- QueryLogic.log[Ctx, Log, Err]("ok")
                    ctx <- QueryLogic.readContext[Ctx, Log, Err]
                yield ctx
            }
            assertTrue(result.value == Right(10), result.logs == Vector("ok"), result.errors.isEmpty)
        },
        test("setContext replaces threaded context") {
            val result = QueryLogic.run[Ctx, Log, Err, Int](initialContext = 1) {
                for
                    _   <- QueryLogic.setContext[Ctx, Log, Err](99)
                    ctx <- QueryLogic.readContext[Ctx, Log, Err]
                yield ctx
            }
            assertTrue(result.value == Right(99), result.context == 99)
        },
        test("failFast aborts and surfaces the error in the result envelope") {
            val result = QueryLogic.run[Ctx, Log, Err, Int](initialContext = 0) {
                for
                    _ <- QueryLogic.failFast[Ctx, Log, Err]("fatal")
                yield 42
            }
            assertTrue(
                result.value == Left(Vector("fatal")),
                result.errors == Vector("fatal")
            )
        },
        test("failFast after soft errors merges both into the result envelope") {
            val result = QueryLogic.run[Ctx, Log, Err, Int](initialContext = 0) {
                for
                    _ <- QueryLogic.error[Ctx, Log, Err]("soft-a")
                    _ <- QueryLogic.failFast[Ctx, Log, Err]("fatal")
                yield 42
            }
            assertTrue(
                result.value == Left(Vector("soft-a", "fatal")),
                result.errors == Vector("soft-a", "fatal")
            )
        }
    )
