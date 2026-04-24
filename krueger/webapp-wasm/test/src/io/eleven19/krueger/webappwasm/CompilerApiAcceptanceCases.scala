package io.eleven19.krueger.webappwasm

object CompilerApiAcceptanceCases:

    final case class ValidParseCst(
        source: String,
        expectedValueFragment: String
    ) derives CanEqual

    final case class MalformedParseCst(
        source: String,
        expectedPhase: String,
        expectedMessageFragment: String
    ) derives CanEqual

    val validParseCst: ValidParseCst =
        ValidParseCst(
            source = "module Demo exposing (..)\n\nmain = 42\n",
            expectedValueFragment = "CstModule("
        )

    val malformedParseCst: MalformedParseCst =
        MalformedParseCst(
            source = "module Demo exposing (..)\n\nmain =\n",
            expectedPhase = "cst",
            expectedMessageFragment = "unexpected end of input"
        )
