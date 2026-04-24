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
                val start = QueryLogic.readContext[Ctx, Log, Err]
                QueryLogic.updateContext[Ctx, Log, Err](_ + 5)
                val end = QueryLogic.readContext[Ctx, Log, Err]
                end - start
            }
            assertTrue(result.value == Right(5), result.context == 7)
        },
        test("accumulates logs in order") {
            val result = QueryLogic.run[Ctx, Log, Err, Unit](initialContext = 0) {
                QueryLogic.log[Ctx, Log, Err]("first")
                QueryLogic.log[Ctx, Log, Err]("second")
            }
            assertTrue(result.logs == Vector("first", "second"))
        },
        test("accumulates errors while continuing") {
            val result = QueryLogic.run[Ctx, Log, Err, Int](initialContext = 3) {
                QueryLogic.error[Ctx, Log, Err]("bad-a")
                QueryLogic.error[Ctx, Log, Err]("bad-b")
                QueryLogic.readContext[Ctx, Log, Err]
            }
            assertTrue(result.value == Left(Vector("bad-a", "bad-b")), result.context == 3)
        },
        test("returns successful value when no errors were emitted") {
            val result = QueryLogic.run[Ctx, Log, Err, Int](initialContext = 10) {
                QueryLogic.log[Ctx, Log, Err]("ok")
                QueryLogic.readContext[Ctx, Log, Err]
            }
            assertTrue(result.value == Right(10), result.logs == Vector("ok"), result.errors.isEmpty)
        }
    )
