package io.eleven19.krueger.compiler

import zio.test.*

object DiagnosticMessageFormatterSpec extends ZIOSpecDefault:

    private val malformedSource = "module M exposing (..)\n\nx ="

    def spec = suite("DiagnosticMessageFormatter")(
        test("formats unexpected end of input with surrounding context and expected tokens") {
            val formatted = DiagnosticMessageFormatter.format(
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
                formatted.message == """-- PARSE ERROR (ELM-P001) at line 3, column 4

I ran into the end of the file unexpectedly.

I was expecting one of the following:

    identifier
    digit

1| module M exposing (..)
2| 
3| x =
      ^""",
                formatted.contextLines.map(_.line) == List(1, 2, 3),
                formatted.contextLines.count(_.isErrorLine) == 1,
                formatted.contextLines.find(_.isErrorLine).exists(_.text == "x =")
            )
        },
        test("formats tokenizer unexpected character errors with surrounding context") {
            val formatted = DiagnosticMessageFormatter.format(
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
                formatted.message == """-- TOKENIZE ERROR (ELM-T001) at line 1, column 6

I ran into an unexpected character:

    @

1| main @
        ^""",
                formatted.contextLines == List(DiagnosticContextLine(1, "main @", isErrorLine = true))
            )
        },
        test("appends hints when provided") {
            val formatted = DiagnosticMessageFormatter.format(
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
                formatted.message.endsWith("Hint: Did you forget `in` after a `let` binding?"),
                formatted.message.contains("I was expecting one of the following:")
            )
        }
    )
