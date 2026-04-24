package io.eleven19.krueger.compiler.abi

import zio.test.*

import InvokeJson.decode
import InvokeJson.given

object InvokeCompilerSpec extends ZIOSpecDefault:

    private val validSource =
        """module Demo exposing (..)
          |
          |main = 42
          |""".stripMargin

    private val malformedSource = "module Demo exposing (..)\n\nmain ="

    private val expectedMalformedSourceMessage =
        List(
            "(line 3, column 7):",
            "  unexpected end of input",
            "  expected \"\"\", \"'\", \"+\", \"-\", -, ., \\, case, digit, identifier, if, let, open brace, open parenthesis, or open square bracket",
            "  >",
            "  >main =",
            "         ^"
        ).mkString("\n")

    private def invoke(op: String, source: String): InvokeResponse =
        decode[InvokeResponse](InvokeCompiler.invoke(op, source))

    def spec = suite("InvokeCompiler")(
        test("happy path: parseCst returns a structured success envelope") {
            val response = invoke("parseCst", s"""{"source":${stringLiteral(validSource)}}""")

            assertTrue(
                response.ok,
                response.logs.isEmpty,
                response.errors.isEmpty,
                response.value.exists(value => value.startsWith("CstModule("))
            )
        },
        test("failure path: malformed source returns a structured parse error envelope") {
            val response = invoke("parseCst", s"""{"source":${stringLiteral(malformedSource)}}""")

            assertTrue(
                !response.ok,
                response.value.isEmpty,
                response.logs.isEmpty,
                response.errors == Vector(
                    InvokeError(
                        phase = "cst",
                        message = expectedMalformedSourceMessage,
                        span = None
                    )
                )
            )
        },
        test("edge path: unknown operation returns a structured internal error envelope") {
            val response = invoke("wat", "{}")

            assertTrue(
                !response.ok,
                response.value.isEmpty,
                response.logs.isEmpty,
                response.errors == Vector(
                    InvokeError(
                        phase = "internal",
                        message = "unknown operation: wat",
                        span = None
                    )
                )
            )
        },
        test("determinism path: the same parseCst input returns byte-identical JSON twice") {
            val input  = s"""{"source":${stringLiteral(validSource)}}"""
            val first  = InvokeCompiler.invoke("parseCst", input)
            val second = InvokeCompiler.invoke("parseCst", input)

            assertTrue(first == second)
        }
    )

    private def stringLiteral(value: String): String =
        "\"" + value.flatMap {
            case '\\' => "\\\\"
            case '"'  => "\\\""
            case '\n' => "\\n"
            case '\r' => "\\r"
            case '\t' => "\\t"
            case c    => c.toString
        } + "\""
