package io.eleven19.krueger.compiler

/** Formats structured parse diagnostics into human-friendly, Elm-inspired messages. */
object DiagnosticMessageFormatter:

    def format(
        source: String,
        code: String,
        line: Int,
        column: Int,
        unexpected: Option[String],
        expected: List[String],
        reasons: Seq[String],
        suggestion: Option[String],
        errorWidth: Int
    ): String =
        val header = formatHeader(code, line, column)
        val body =
            List(
                unexpectedExplanation(code, unexpected),
                expectedExplanation(expected),
                reasonsExplanation(reasons)
            ).filter(_.nonEmpty).mkString("\n\n")
        val snippet = sourceSnippet(source, line, column, errorWidth)
        val hint    = suggestion.map(s => s"\n\nHint: $s").getOrElse("")
        List(header, body, snippet).filter(_.nonEmpty).mkString("\n\n") + hint

    private def formatHeader(code: String, line: Int, column: Int): String =
        val kind =
            if code.startsWith("ELM-T") then "TOKENIZE ERROR"
            else "PARSE ERROR"
        s"-- $kind ($code) at line $line, column $column"

    private def unexpectedExplanation(code: String, unexpected: Option[String]): String =
        unexpected match
            case Some("end of input") =>
                "I ran into the end of the file unexpectedly."
            case Some(value) if code.startsWith("ELM-T") =>
                s"I ran into an unexpected character:\n\n    $value"
            case Some(token) =>
                s"I ran into an unexpected token:\n\n    $token"
            case None =>
                "I ran into something I did not expect here."

    private def expectedExplanation(expected: List[String]): String =
        if expected.isEmpty then ""
        else
            val items = expected.map(item => s"    $item").mkString("\n")
            s"I was expecting one of the following:\n\n$items"

    private def reasonsExplanation(reasons: Seq[String]): String =
        reasons.filter(_.nonEmpty) match
            case Nil   => ""
            case lines => lines.mkString("\n")

    private def sourceSnippet(source: String, line: Int, column: Int, errorWidth: Int): String =
        val sourceLine = lineAt(source, line)
        val gutter     = s"$line| "
        val caretWidth = errorWidth.max(1)
        val caretStart = gutter.length + (column - 1).max(0)
        val caret      = " " * caretStart + ("^" * caretWidth)
        s"$gutter$sourceLine\n$caret"

    private def lineAt(source: String, oneBasedLine: Int): String =
        if source.isEmpty then ""
        else source.linesIterator.toVector.lift(oneBasedLine - 1).getOrElse("")
