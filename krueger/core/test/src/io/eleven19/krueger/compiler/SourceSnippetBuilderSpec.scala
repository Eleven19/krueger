package io.eleven19.krueger.compiler

import zio.test.*

object SourceSnippetBuilderSpec extends ZIOSpecDefault:

    def spec = suite("SourceSnippetBuilder")(
        test("includes two lines before and one line after when available") {
            val source = "module Demo exposing (..)\n\nmain =\n"
            val snippet = SourceSnippetBuilder.build(
                source = source,
                errorLine = 3,
                column = 7,
                errorWidth = 0
            )
            assertTrue(
                snippet.contextLines.map(_.line) == List(1, 2, 3, 4),
                snippet.contextLines.count(_.isErrorLine) == 1,
                snippet.rendered.contains("1| module Demo exposing (..)"),
                snippet.rendered.contains("3| main ="),
                snippet.rendered.contains("4|"),
                snippet.rendered.linesIterator.toList.last == "         ^"
            )
        },
        test("clamps context at the start of the file") {
            val snippet = SourceSnippetBuilder.build(
                source = "module M",
                errorLine = 1,
                column = 7,
                errorWidth = 0
            )
            assertTrue(
                snippet.contextLines.map(_.line) == List(1),
                snippet.rendered.startsWith("1| module M")
            )
        },
        test("preserves a trailing empty line after a final newline") {
            val snippet = SourceSnippetBuilder.build(
                source = "x =\n",
                errorLine = 2,
                column = 1,
                errorWidth = 0
            )
            assertTrue(
                snippet.contextLines.exists(line => line.line == 2 && line.text.isEmpty),
                snippet.rendered.contains("2| ")
            )
        }
    )
