package io.eleven19.krueger.webapp.components

import zio.test.*

import io.eleven19.krueger.Krueger
import io.eleven19.krueger.cst.CstModule
import io.eleven19.krueger.cst.CstNode
import io.eleven19.krueger.cst.CstQueryableTree.given

/** [[TreeOutline]] is the pure data shape that [[TreeView]] renders. Locking it down here means the Laminar layer can
  * stay dumb — a transform from a ready-made node/label tree to DOM.
  *
  * Tests exercise REQ-webapp-components-003 (use QueryableTree typeclass, not custom traversal) by building from the
  * real CST/AST parser output rather than fabricating tree nodes manually.
  */
object TreeOutlineSpec extends ZIOSpecDefault:

    private val sample = "module M exposing (..)\n\nx = 1\n"

    private val cstRoot: CstModule =
        Krueger.parseCst(sample).toOption.getOrElse(sys.error("fixture CST parse must succeed"))

    def spec = suite("TreeOutline")(
        test("from CstModule root yields a node with the case-class simple name as label") {
            val out = TreeOutline.from[CstNode](cstRoot)
            assertTrue(out.nodeType == "CstModule")
        },
        test("happy path: CstModule outline contains at least the module declaration child") {
            val out           = TreeOutline.from[CstNode](cstRoot)
            val childTypes    = out.children.map(_.nodeType)
            val moduleDeclIdx = childTypes.indexOf("CstModuleDeclaration")
            assertTrue(
                out.children.nonEmpty,
                moduleDeclIdx >= 0
            )
        },
        test("edge: a leaf-like node with no children yields TreeOutline.children empty") {
            val out = TreeOutline.from[CstNode](cstRoot)
            val leaf = out.flatten
                .find(n => n.children.isEmpty && n.text.isDefined)
            assertTrue(leaf.isDefined)
        },
        test("determinism: TreeOutline.from is stable across repeated builds for the same root") {
            val a = TreeOutline.from[CstNode](cstRoot)
            val b = TreeOutline.from[CstNode](cstRoot)
            assertTrue(a == b)
        },
        test("text content is surfaced for leaves so TreeView can show literal values inline") {
            val out      = TreeOutline.from[CstNode](cstRoot)
            val withText = out.flatten.filter(_.text.isDefined).map(_.nodeType)
            assertTrue(
                withText.nonEmpty,
                withText.contains("CstName")
            )
        }
    )
