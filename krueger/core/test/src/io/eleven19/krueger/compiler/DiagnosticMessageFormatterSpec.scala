package io.eleven19.krueger.compiler

import zio.test.*

object DiagnosticMessageFormatterSpec extends ZIOSpecDefault:

    private val malformedSource = "module M exposing (..)\n\nx ="

    def spec = suite("DiagnosticMessageFormatter")(
        test("formats unexpected end of input with source snippet and expected tokens") {
            val message = DiagnosticMessageFormatter.format(
                source = malformedSource,
                code = DiagnosticCodes.UnexpectedEndOfInput,
                line = 3,
                column = 4,
                unexpected = Some("end of input"),
                expected = List("identifier", "digit"),
                reasons = Nil,
                suggestion = None,
                errorWidth = 0
            )
            assertTrue(
                message == """-- PARSE ERROR (ELM-P001) at line 3, column 4

I ran into the end of the file unexpectedly.

I was expecting one of the following:

    identifier
    digit

3| x =
      ^"""
            )
        },
        test("formats tokenizer unexpected character errors") {
            val message = DiagnosticMessageFormatter.format(
                source = "main @",
                code = DiagnosticCodes.TokenizerUnexpectedCharacter,
                line = 1,
                column = 6,
                unexpected = Some("@"),
                expected = Nil,
                reasons = Nil,
                suggestion = None,
                errorWidth = 1
            )
            assertTrue(
                message == """-- TOKENIZE ERROR (ELM-T001) at line 1, column 6

I ran into an unexpected character:

    @

1| main @
        ^"""
            )
        },
        test("appends hints when provided") {
            val message = DiagnosticMessageFormatter.format(
                source = "let x = 1",
                code = DiagnosticCodes.UnexpectedEndOfInput,
                line = 1,
                column = 10,
                unexpected = Some("end of input"),
                expected = List("in"),
                reasons = Nil,
                suggestion = Some("Did you forget `in` after a `let` binding?"),
                errorWidth = 0
            )
            assertTrue(
                message.endsWith("Hint: Did you forget `in` after a `let` binding?"),
                message.contains("I was expecting one of the following:")
            )
        }
    )
