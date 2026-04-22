package io.eleven19.krueger

import parsley.{Failure, Success}
import zio.test.*

import io.eleven19.krueger.cst.CommentKind

object KruegerSpec extends ZIOSpecDefault:

    private val minimal = "module Main exposing (..)\n"

    private val richer =
        """module App exposing (..)
          |
          |import Html
          |
          |main = 42
          |""".stripMargin

    private def parseCstOrFail(src: String): io.eleven19.krueger.cst.CstModule =
        Krueger.parseCst(src) match
            case Success(m)   => m
            case Failure(msg) => throw new AssertionError(s"parse failed: $msg\nSource:\n$src")

    private def parseAstOrFail(src: String): io.eleven19.krueger.ast.Module =
        Krueger.parseAst(src) match
            case Success(m)   => m
            case Failure(msg) => throw new AssertionError(s"parse failed: $msg\nSource:\n$src")

    def spec = suite("Krueger")(
        test("parseCst succeeds on minimal module") {
            val m = parseCstOrFail(minimal)
            assertTrue(m.moduleDecl.name.parts.map(_.value) == List("Main"))
        },
        test("parseAst succeeds on minimal module") {
            val m = parseAstOrFail(minimal)
            assertTrue(m.name.fullName == "Main")
        },
        test("parseCst succeeds on richer fixture") {
            val m = parseCstOrFail(richer)
            assertTrue(
                m.moduleDecl.name.parts.map(_.value) == List("App"),
                m.imports.size == 1,
                m.declarations.size == 1
            )
        },
        test("parseCst differentiates line, block, and doc comments") {
            val m = parseCstOrFail(
                """module App exposing (..)
                  |
                  |-- regular line
                  |{- regular block -}
                  |{-| module docs -}
                  |main = "-- not a comment"
                  |""".stripMargin
            )
            // The doc comment is associated with the `main` declaration via trivia
            val declDoc = m.declarations.head
                .asInstanceOf[io.eleven19.krueger.cst.CstValueDeclaration]
                .trivia
                .docComment
                .map(_.text.trim)
            // Non-doc comments remain in the module trivia
            val moduleComments = m.trivia.comments.filterNot(_.kind == CommentKind.Doc)
            assertTrue(
                moduleComments.map(_.kind) == IndexedSeq(CommentKind.Line, CommentKind.Block),
                moduleComments.map(_.text.trim) == IndexedSeq("regular line", "regular block"),
                declDoc.contains("module docs")
            )
        },
        test("parseAst lowers imports and declarations") {
            val m = parseAstOrFail(richer)
            assertTrue(
                m.imports.map(_.moduleName.fullName) == List("Html"),
                m.declarations.size == 1
            )
        },
        test("parseCst fails on malformed input") {
            Krueger.parseCst("module !!!") match
                case Failure(_) => assertCompletes
                case Success(_) => throw new AssertionError("expected failure, got success")
        }
    )
