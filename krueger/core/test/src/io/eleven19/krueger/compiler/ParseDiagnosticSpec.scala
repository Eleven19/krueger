package io.eleven19.krueger.compiler

import zio.test.*

import io.eleven19.krueger.parser.{DiagnosticBody, ParseDiagnosticErrorBuilder}

object ParseDiagnosticSpec extends ZIOSpecDefault:

    def spec = suite("ParseDiagnostic")(
        test("classifies unexpected end of input as ELM-P001") {
            val diagnostic = ParseDiagnostic.unexpectedEndOfInput(
                source = "module M",
                line = 1,
                column = 9,
                expected = List("exposing", "where")
            )
            assertTrue(
                diagnostic.code == DiagnosticCodes.UnexpectedEndOfInput,
                diagnostic.span.line == 1,
                diagnostic.span.column == 9,
                diagnostic.span.start == 8,
                diagnostic.span.end == 8,
                diagnostic.expected == List("exposing", "where"),
                diagnostic.suggestion.isEmpty
            )
        },
        test("classifies unexpected token as ELM-P002") {
            val diagnostic = ParseDiagnostic.unexpectedToken(
                source = "x = @",
                line = 1,
                column = 5,
                width = 1,
                unexpected = "@",
                expected = List("identifier", "digit")
            )
            assertTrue(
                diagnostic.code == DiagnosticCodes.UnexpectedToken,
                diagnostic.span.start == 4,
                diagnostic.span.end == 5,
                diagnostic.message.contains("unexpected")
            )
        },
        test("tokenizer unexpected character uses ELM-T001") {
            val diagnostic = ParseDiagnostic.tokenizerUnexpectedCharacter(
                source = "main @",
                offset = 5,
                lexeme = "@"
            )
            assertTrue(
                diagnostic.code == DiagnosticCodes.TokenizerUnexpectedCharacter,
                diagnostic.span.start == 5,
                diagnostic.span.end == 6,
                diagnostic.span.line == 1,
                diagnostic.span.column == 6,
                diagnostic.message.contains("I ran into an unexpected character"),
                diagnostic.message.contains("@")
            )
        },
        test("suggestion helper recognizes missing in after let") {
            val diagnostic = ParseDiagnosticErrorBuilder("module M\n\nx = 1").format(
                (3, 1),
                (),
                DiagnosticBody.Vanilla(
                    unexpected = Some("end of input"),
                    expected = Set("in", "identifier"),
                    reasons = Nil,
                    errorWidth = 0
                )
            )
            assertTrue(
                diagnostic.suggestion.contains("Did you forget `in` after a `let` binding?")
            )
        }
    )
