package io.eleven19.krueger.compiler

import zio.test.*

object DiagnosticCodeSpec extends ZIOSpecDefault:

    def spec = suite("DiagnosticCode")(
        suite("validation")(
            test("accepts stable parse and tokenizer codes") {
                assertTrue(
                    DiagnosticCode.make("ELM-P001").isRight,
                    DiagnosticCode.make("ELM-P002").isRight,
                    DiagnosticCode.make("ELM-P003").isRight,
                    DiagnosticCode.make("ELM-T001").isRight
                )
            },
            test("rejects arbitrary strings") {
                assertTrue(
                    DiagnosticCode.make("").isLeft,
                    DiagnosticCode.make("PARSE_ERROR").isLeft,
                    DiagnosticCode.make("ELM-X001").isLeft,
                    DiagnosticCode.make("ELM-P01").isLeft
                )
            }
        ),
        suite("known codes")(
            test("exposes the current stable diagnostic codes") {
                assertTrue(
                    DiagnosticCode.unwrap(DiagnosticCode.UnexpectedEndOfInput) == "ELM-P001",
                    DiagnosticCode.unwrap(DiagnosticCode.UnexpectedToken) == "ELM-P002",
                    DiagnosticCode.unwrap(DiagnosticCode.SpecialisedParseFailure) == "ELM-P003",
                    DiagnosticCode.unwrap(DiagnosticCode.TokenizerUnexpectedCharacter) == "ELM-T001"
                )
            },
            test("classifies tokenizer codes for message formatting") {
                assertTrue(
                    DiagnosticCode.isTokenizer(DiagnosticCode.TokenizerUnexpectedCharacter),
                    !DiagnosticCode.isTokenizer(DiagnosticCode.UnexpectedEndOfInput)
                )
            }
        )
    )
