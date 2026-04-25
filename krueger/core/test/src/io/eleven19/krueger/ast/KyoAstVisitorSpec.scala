package io.eleven19.krueger.ast

import io.eleven19.krueger.Krueger
import io.eleven19.krueger.ast.AstQueryableTree.given
import io.eleven19.krueger.trees.QueryableTree
import kyo.*
import parsley.{Failure, Success}
import zio.test.*

object KyoAstVisitorSpec extends ZIOSpecDefault:
    private val sampleSource =
        """module Main exposing (..)
          |
          |x = 1
          |""".stripMargin

    private def parsedAst: AstNode =
        Krueger.parseAst(sampleSource) match
            case Success(m)   => m
            case Failure(msg) => sys.error(s"baseline parse failure: $msg")

    def spec = suite("KyoAstVisitor")(
        test("visit invokes callback for every AST node in pre-order"):
            val out = KyoAstVisitor.fold(parsedAst, 0) { (acc, _) => (acc + 1): Int < Any }.eval
            assertTrue(out > 0)
        ,
        test("visit order matches the pure AstVisitor traversal order"):
            val qt = QueryableTree[AstNode]
            val pureOrder = AstVisitor.foldLeft(parsedAst, Vector.empty[String]) { (acc, n) =>
                acc :+ qt.nodeType(n).toString
            }
            val kyoOrder = KyoAstVisitor.fold(parsedAst, Vector.empty[String]) { (acc, n) =>
                (acc :+ qt.nodeType(n).toString): Vector[String] < Any
            }.eval
            assertTrue(kyoOrder == pureOrder)
        ,
        test("Abort.fail in callback short-circuits visitation"):
            val out = Abort.run[String] {
                KyoAstVisitor.visit(parsedAst)(_ => Abort.fail("stop"))
            }.eval
            assertTrue(out.toString.contains("stop"))
    )
