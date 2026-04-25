package io.eleven19.krueger.compiler

import kyo.*
import zio.test.*

object StageSpec extends ZIOSpecDefault:

    private val identityStage: Stage[Int, Int, Any] =
        Stage.identity[Int]

    private val pureStage: Stage[Int, String, Any] =
        Stage.pure((i: Int) => s"value=$i")

    def spec = suite("Stage")(
        test("identity returns the input unchanged") {
            val program: Int < Any = identityStage.run(42)
            val out                = program.eval
            assertTrue(out == 42)
        },
        test("pure applies a pure function") {
            val program: String < Any = pureStage.run(7)
            val out                   = program.eval
            assertTrue(out == "value=7")
        },
        test(">>> composes two stages preserving effect rows") {
            val composed: Stage[Int, String, Any] = identityStage >>> pureStage
            val program                           = composed.run(13)
            val out                               = program.eval
            assertTrue(out == "value=13")
        },
        test("composition order is left-to-right") {
            val plusOne: Stage[Int, Int, Any]     = Stage.pure((i: Int) => i + 1)
            val toStr: Stage[Int, String, Any]    = Stage.pure((i: Int) => i.toString)
            val pipeline: Stage[Int, String, Any] = plusOne >>> toStr
            val program                           = pipeline.run(4)
            val out                               = program.eval
            assertTrue(out == "5")
        },
        test("fromKyo lifts an effectful function") {
            val effStage: Stage[Int, Int, Any] =
                Stage.fromKyo((i: Int) => (i * 2): Int < Any)
            val program = effStage.run(21)
            val out     = program.eval
            assertTrue(out == 42)
        }
    )
