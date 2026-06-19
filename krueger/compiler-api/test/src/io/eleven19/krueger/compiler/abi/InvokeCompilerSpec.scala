package io.eleven19.krueger.compiler.abi

import zio.test.*

import io.eleven19.krueger.Krueger as CoreKrueger
import io.eleven19.krueger.compiler.CompileError
import io.eleven19.krueger.compiler.ParseDiagnostic

import InvokeJson.decode
import InvokeJson.given

object InvokeCompilerSpec extends ZIOSpecDefault:

    private val validSource =
        """module Demo exposing (..)
          |
          |main = 42
          |""".stripMargin

    private val malformedSource = "module Demo exposing (..)\n\nmain ="

    private def expectedParseInvokeError(source: String): InvokeError =
        CoreKrueger.parseCst(source) match
            case parsley.Failure(diagnostic: ParseDiagnostic) =>
                InvokeError.fromCompileError(CompileError.ParseError(phase = "cst", diagnostic = diagnostic))
            case parsley.Success(_) =>
                throw new AssertionError(s"expected parse failure for: $source")

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
            val expected   = expectedParseInvokeError(malformedSource)

            assertTrue(
                !response.ok,
                response.value.isEmpty,
                response.logs.isEmpty,
                response.errors == Vector(expected),
                response.errors.head.contextLines.nonEmpty,
                response.errors.head.contextLines.count(_.isErrorLine) == 1
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
