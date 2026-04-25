package io.eleven19.krueger.lexer

import zio.test.*

import io.eleven19.krueger.compiler.CompileError

object ElmTokenizerSpec extends ZIOSpecDefault:

    private def valueOf(result: ElmTokenizer.TokenizeResult[Vector[ElmToken]]): Vector[ElmToken] =
        result.value match
            case Right(tokens) => tokens
            case Left(errors)  => throw new AssertionError(s"expected tokens, got errors: $errors")

    def spec = suite("ElmTokenizer")(
        test("tokenizes keywords, identifiers, operators, literals, punctuation, and spans") {
            val tokens = valueOf(ElmTokenizer.run("""module Main exposing (main = "hi")"""))

            assertTrue(
                tokens.map(_.kind) == Vector(
                    ElmTokenKind.Keyword,
                    ElmTokenKind.UpperIdentifier,
                    ElmTokenKind.Keyword,
                    ElmTokenKind.Punctuation,
                    ElmTokenKind.LowerIdentifier,
                    ElmTokenKind.Operator,
                    ElmTokenKind.StringLiteral,
                    ElmTokenKind.Punctuation
                ),
                tokens.map(t => (t.lexeme, t.start, t.end)) == Vector(
                    ("module", 0, 6),
                    ("Main", 7, 11),
                    ("exposing", 12, 20),
                    ("(", 21, 22),
                    ("main", 22, 26),
                    ("=", 27, 28),
                    ("\"hi\"", 29, 33),
                    (")", 33, 34)
                )
            )
        },
        test("excludes trivia by default and includes whitespace, newlines, and comments when configured") {
            val source = "main = 1 -- greeting\nnext = 2"
            val defaultTokens = valueOf(ElmTokenizer.run(source))
            val triviaTokens = valueOf(ElmTokenizer.run(source, ElmTokenizerConfig(includeTrivia = true, recoverUnknown = true)))

            assertTrue(
                !defaultTokens.exists(t =>
                    t.kind == ElmTokenKind.Whitespace || t.kind == ElmTokenKind.Newline || t.kind == ElmTokenKind.Comment
                ),
                triviaTokens.exists(t => t.kind == ElmTokenKind.Whitespace && t.lexeme == " "),
                triviaTokens.exists(t => t.kind == ElmTokenKind.Comment && t.lexeme == "-- greeting"),
                triviaTokens.exists(t => t.kind == ElmTokenKind.Newline && t.lexeme == "\n")
            )
        },
        test("matches longest operators before shorter prefixes") {
            val tokens = valueOf(ElmTokenizer.run("a -> b |> c"))

            assertTrue(tokens.filter(_.kind == ElmTokenKind.Operator).map(_.lexeme) == Vector("->", "|>"))
        },
        test("recovers unknown input with a token and diagnostic log when configured") {
            val result = ElmTokenizer.run("main @ value")
            val tokens = valueOf(result)

            assertTrue(
                tokens.exists(t => t.kind == ElmTokenKind.Unknown && t.lexeme == "@"),
                result.logs.exists(_.contains("Recovered unknown token '@' at 5"))
            )
        },
        test("uses the error channel for unrecovered unknown input") {
            val result = ElmTokenizer.run("main @ value", ElmTokenizerConfig(includeTrivia = false, recoverUnknown = false))

            assertTrue(
                result.value.isLeft,
                result.errors.exists {
                    case CompileError.ParseError("tokenize", message, Some(span)) =>
                        message.contains("Unexpected character '@'") && span.start == 5 && span.end == 6
                    case _ => false
                }
            )
        }
    )
