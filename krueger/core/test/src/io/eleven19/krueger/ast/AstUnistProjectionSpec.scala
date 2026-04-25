package io.eleven19.krueger.ast

import parsley.{Failure, Success}
import zio.test.*

import io.eleven19.krueger.Krueger
import io.eleven19.krueger.ast.AstQueryableTree.given
import io.eleven19.krueger.ast.AstUnistProjection.given
import io.eleven19.krueger.trees.unist.UnistProjection

object AstUnistProjectionSpec extends ZIOSpecDefault:

    private val source =
        """module App exposing (..)
          |
          |main = 42
          |""".stripMargin

    private def parse(src: String): Module = Krueger.parseAst(src) match
        case Success(value) => value
        case Failure(msg)  => throw AssertionError(s"parse failed: $msg")

    private val moduleTree: AstNode = parse(source)

    def spec = suite("AstUnistProjection")(
        test("projects parsed AST root with type, fields, and child count") {
            val node = UnistProjection.project(moduleTree, Some(source))
            assertTrue(
                node.`type` == "Module",
                node.data.fields.keySet == Set("exposing", "imports", "declarations"),
                node.data.childCount == node.children.size
            )
        },
        test("projects AST declaration and literal text values") {
            val node = UnistProjection.project(moduleTree, Some(source))
            val values = collect(node).flatMap(_.value)
            assertTrue(values.contains("main"), values.contains("42"))
        },
        test("preserves AST traversal order for the module children") {
            val node = UnistProjection.project(moduleTree, Some(source))
            assertTrue(node.children.map(_.`type`) == IndexedSeq("ExposingAll", "ValueDeclaration"))
        }
    )

    private def collect(
        node: io.eleven19.krueger.trees.unist.UnistNode
    ): List[io.eleven19.krueger.trees.unist.UnistNode] =
        node :: node.children.toList.flatMap(collect)
