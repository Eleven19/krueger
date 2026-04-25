package io.eleven19.krueger.cst

import io.eleven19.krueger.parser.ModuleParser
import io.eleven19.krueger.cst.CstQueryableTree.given
import io.eleven19.krueger.trees.QueryableTree
import kyo.*
import parsley.{Failure, Success}
import zio.test.*

object KyoCstVisitorSpec extends ZIOSpecDefault:
    private val sampleSource =
        """module Main exposing (..)
          |
          |x = 1
          |""".stripMargin

    private def parsedCst: CstNode =
        ModuleParser.module.parse(sampleSource) match
            case Success(m)   => m
            case Failure(msg) => sys.error(s"baseline parse failure: $msg")

    def spec = suite("KyoCstVisitor")(
        test("visit invokes callback for every CST node in pre-order"):
            val out = KyoCstVisitor.fold(parsedCst, 0) { (acc, _) => (acc + 1): Int < Any }.eval
            assertTrue(out > 0)
        ,
        test("visit order matches the pure CstVisitor traversal order"):
            val qt = QueryableTree[CstNode]
            val pureOrder = CstVisitor.foldLeft(parsedCst, Vector.empty[String]) { (acc, n) =>
                acc :+ qt.nodeType(n).toString
            }
            val kyoOrder = KyoCstVisitor.fold(parsedCst, Vector.empty[String]) { (acc, n) =>
                (acc :+ qt.nodeType(n).toString): Vector[String] < Any
            }.eval
            assertTrue(kyoOrder == pureOrder)
        ,
        test("Abort.fail in callback short-circuits visitation"):
            val out = Abort.run[String] {
                KyoCstVisitor.visit(parsedCst)(_ => Abort.fail("stop"))
            }.eval
            assertTrue(out.toString.contains("stop"))
    )
