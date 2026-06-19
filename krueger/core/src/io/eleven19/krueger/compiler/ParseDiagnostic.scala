package io.eleven19.krueger.compiler

final case class SourceSpan(start: Int, end: Int, line: Int, column: Int) derives CanEqual

final case class ParseDiagnostic(
    code: String,
    span: SourceSpan,
    message: String,
    expected: List[String],
    suggestion: Option[String] = None
) derives CanEqual:

    def toCompilerSpan: Span = Span(start = span.start, end = span.end)

object ParseDiagnostic:

    def unexpectedEndOfInput(source: String, line: Int, column: Int, expected: List[String]): ParseDiagnostic =
        val start = SourceOffsets.offsetAt(source, line, column)
        ParseDiagnostic(
            code = DiagnosticCodes.UnexpectedEndOfInput,
            span = SourceSpan(start = start, end = start, line = line, column = column),
            message =
                formatMessage(line, column, unexpected = Some("end of input"), expected = expected, reasons = Nil),
            expected = expected
        )

    def unexpectedToken(
        source: String,
        line: Int,
        column: Int,
        width: Int,
        unexpected: String,
        expected: List[String]
    ): ParseDiagnostic =
        val start = SourceOffsets.offsetAt(source, line, column)
        val end   = (start + width.max(1)).min(source.length.max(start + 1))
        ParseDiagnostic(
            code = DiagnosticCodes.UnexpectedToken,
            span = SourceSpan(start = start, end = end, line = line, column = column),
            message = formatMessage(line, column, unexpected = Some(unexpected), expected = expected, reasons = Nil),
            expected = expected
        )

    def tokenizerUnexpectedCharacter(source: String, offset: Int, lexeme: String): ParseDiagnostic =
        val (line, column) = SourceOffsets.lineColumnAt(source, offset)
        ParseDiagnostic(
            code = DiagnosticCodes.TokenizerUnexpectedCharacter,
            span = SourceSpan(start = offset, end = offset + lexeme.length, line = line, column = column),
            message = s"Unexpected character '$lexeme'",
            expected = Nil
        )

    private def formatMessage(
        line: Int,
        column: Int,
        unexpected: Option[String],
        expected: List[String],
        reasons: Seq[String]
    ): String =
        val header         = s"(line $line, column $column):"
        val unexpectedLine = unexpected.map(u => s"  unexpected $u")
        val expectedLine =
            if expected.isEmpty then None
            else Some(s"  expected ${formatExpected(expected)}")
        val reasonLines = reasons.map(r => s"  $r")
        (header :: unexpectedLine.toList ::: expectedLine.toList ::: reasonLines.toList).mkString("\n")

    private def formatExpected(items: List[String]): String =
        if items.isEmpty then ""
        else if items.size == 1 then items.head
        else items.init.mkString(", ") + s", or ${items.last}"
