package io.eleven19.krueger.parser

import parsley.{Failure, Success}
import zio.test.*

import io.eleven19.krueger.Krueger
import io.eleven19.krueger.compiler.DiagnosticCodes
import io.eleven19.krueger.compiler.ParseDiagnostic

object ParseDiagnosticParserSpec extends ZIOSpecDefault:

    private val malformedSource = "module M exposing (..)\n\nx ="

    def spec = suite("ParseDiagnosticParser")(
        test("happy path: valid source produces zero diagnostics") {
            val source = "module M exposing (..)\n\nx = 1\n"
            Krueger.parseCst(source) match
                case Success(_) => assertTrue(true)
                case Failure(_) => assertTrue(false)
        },
        test("malformed source produces ELM-P001 with span and expected tokens") {
            Krueger.parseCst(malformedSource) match
                case Failure(diagnostic: ParseDiagnostic) =>
                    assertTrue(
                        diagnostic.code == DiagnosticCodes.UnexpectedEndOfInput,
                        diagnostic.span.line == 3,
                        diagnostic.span.column == 4,
                        diagnostic.span.start == 27,
                        diagnostic.span.end == 27,
                        diagnostic.expected.nonEmpty,
                        diagnostic.message.contains("unexpected end of input")
                    )
                case Success(_) => assertTrue(false)
        },
        test("empty source produces ELM-P001 at start of file") {
            Krueger.parseCst("") match
                case Failure(diagnostic: ParseDiagnostic) =>
                    assertTrue(
                        diagnostic.code == DiagnosticCodes.UnexpectedEndOfInput,
                        diagnostic.span.line == 1,
                        diagnostic.span.column == 1,
                        diagnostic.span.start == 0,
                        diagnostic.span.end == 0,
                        diagnostic.expected.contains("module")
                    )
                case Success(_) => assertTrue(false)
        }
    )
