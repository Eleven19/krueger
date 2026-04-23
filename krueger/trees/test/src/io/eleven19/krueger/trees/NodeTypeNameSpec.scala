package io.eleven19.krueger.trees

import zio.test.*

object NodeTypeNameSpec extends ZIOSpecDefault:

    def spec = suite("NodeTypeName")(
        suite("validation")(
            test("accepts a non-blank identifier-like string") {
                assertTrue(NodeTypeName.make("CstIntLiteral").isRight)
            },
            test("accepts strings with internal whitespace") {
                assertTrue(NodeTypeName.make("foo bar").isRight)
            },
            test("rejects the empty string") {
                assertTrue(NodeTypeName.make("").isLeft)
            },
            test("rejects a whitespace-only string") {
                assertTrue(
                    NodeTypeName.make(" ").isLeft,
                    NodeTypeName.make("\t\n ").isLeft
                )
            }
        ),
        suite("equality and unwrap")(
            test("two NodeTypeNames with the same underlying string are equal") {
                val a = NodeTypeName.make("CstIntLiteral").toOption.get
                val b = NodeTypeName.make("CstIntLiteral").toOption.get
                assertTrue(a == b)
            },
            test("unwrap returns the original string") {
                val n = NodeTypeName.make("CstIntLiteral").toOption.get
                assertTrue(NodeTypeName.unwrap(n) == "CstIntLiteral")
            }
        )
    )
