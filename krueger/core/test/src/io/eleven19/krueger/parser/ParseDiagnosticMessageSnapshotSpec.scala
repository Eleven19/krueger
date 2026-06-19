package io.eleven19.krueger.parser

import parsley.{Failure, Success}
import zio.test.*

import io.eleven19.krueger.Krueger
import io.eleven19.krueger.compiler.DiagnosticCodes
import io.eleven19.krueger.compiler.ParseDiagnostic
import io.eleven19.krueger.lexer.{ElmTokenizer, ElmTokenizerConfig}

object ParseDiagnosticMessageSnapshotSpec extends ZIOSpecDefault:

    def spec = suite("ParseDiagnosticMessageSnapshot")(
        test("malformed module value documents the friendly end-of-input message shape") {
            val source = "module M exposing (..)\n\nx ="
            Krueger.parseCst(source) match
                case Failure(diagnostic: ParseDiagnostic) =>
                    assertTrue(
                        diagnostic.message.startsWith("-- PARSE ERROR (ELM-P001)"),
                        diagnostic.message.contains("I ran into the end of the file unexpectedly."),
                        diagnostic.message.contains("I was expecting one of the following:"),
                        diagnostic.message.contains("1| module M exposing (..)"),
                        diagnostic.message.contains("3| x ="),
                        diagnostic.message.linesIterator.toList.last == "      ^",
                        diagnostic.contextLines.nonEmpty,
                        diagnostic.contextLines.count(_.isErrorLine) == 1
                    )
                case Success(_) => assertTrue(false)
        },
        test("tokenizer failure documents the friendly unexpected-character message shape") {
            val result = ElmTokenizer.run("main @", ElmTokenizerConfig(includeTrivia = false, recoverUnknown = false))
            assertTrue(
                result.errors.exists {
                    case io.eleven19.krueger.compiler.CompileError.ParseError("tokenize", diagnostic) =>
                        diagnostic.message.startsWith("-- TOKENIZE ERROR (ELM-T001)") &&
                        diagnostic.message.contains("I ran into an unexpected character:") &&
                        diagnostic.message.contains("1| main @") &&
                        diagnostic.message.linesIterator.toList.last == "        ^" &&
                        diagnostic.contextLines.count(_.isErrorLine) == 1
                    case _ => false
                }
            )
        }
    )
