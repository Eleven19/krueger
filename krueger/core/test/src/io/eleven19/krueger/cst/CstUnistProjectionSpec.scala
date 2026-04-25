package io.eleven19.krueger.cst

import parsley.{Failure, Success}
import zio.test.*

import io.eleven19.krueger.Krueger
import io.eleven19.krueger.cst.CstQueryableTree.given
import io.eleven19.krueger.cst.CstUnistProjection.given
import io.eleven19.krueger.trees.unist.UnistPoint
import io.eleven19.krueger.trees.unist.UnistProjection

object CstUnistProjectionSpec extends ZIOSpecDefault:

    private val source =
        """module App exposing (..)
          |
          |main = 42
          |""".stripMargin

    private def parse(src: String): CstModule = Krueger.parseCst(src) match
        case Success(value) => value
        case Failure(msg)  => throw AssertionError(s"parse failed: $msg")

    private val moduleTree: CstNode = parse(source)

    def spec = suite("CstUnistProjection")(
        test("projects parsed CST root with type, child order, fields, and source position") {
            val node = UnistProjection.project(moduleTree, Some(source))
            assertTrue(
                node.`type` == "CstModule",
                node.children.map(_.`type`) == IndexedSeq("CstModuleDeclaration", "CstValueDeclaration"),
                node.data.fields.keySet == Set("moduleDecl", "imports", "declarations"),
                node.data.fields("moduleDecl") == IndexedSeq(0),
                node.position.exists(_.start == UnistPoint(1, 1, Some(0))),
                node.position.exists(_.end == UnistPoint(4, 1, Some(36)))
            )
        },
        test("projects CstName leaves with value text") {
            val node = UnistProjection.project(moduleTree, Some(source))
            val names = collect(node).filter(_.`type` == "CstName").flatMap(_.value)
            assertTrue(names.contains("App"), names.contains("main"))
        },
        test("preserves QueryableTree child count on every projected node") {
            val node = UnistProjection.project(moduleTree, Some(source))
            assertTrue(collect(node).forall(n => n.data.childCount == n.children.size))
        }
    )

    private def collect(
        node: io.eleven19.krueger.trees.unist.UnistNode
    ): List[io.eleven19.krueger.trees.unist.UnistNode] =
        node :: node.children.toList.flatMap(collect)
