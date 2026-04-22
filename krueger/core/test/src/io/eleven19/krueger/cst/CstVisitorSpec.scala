package io.eleven19.krueger.cst

import zio.test.*

import io.eleven19.krueger.Span
import io.eleven19.krueger.cst.CstVisitor.*

object CstVisitorSpec extends ZIOSpecDefault:

    private val sp = Span.zero

    private class TagVisitor extends CstVisitor[String]:
        def visitNode(node: CstNode): String                      = "Node"
        override def visitName(node: CstName): String             = s"Name(${node.value})"
        override def visitIntLiteral(node: CstIntLiteral): String = s"Int(${node.value})"

        override def visitVariablePattern(node: CstVariablePattern): String =
            s"VarPat(${node.name.value})"

    private val sampleModule: CstModule =
        CstModule(
            CstModuleDeclaration(
                ModuleType.Plain,
                CstQualifiedName(List(CstName("M")(sp)))(sp),
                CstExposingAll()(sp)
            )(sp),
            Nil,
            Nil
        )(sp)

    def spec = suite("CstVisitor")(
        suite("dispatch")(
            test("visit routes to the specific visitor method") {
                val v = new TagVisitor
                assertTrue(CstVisitor.visit(CstName("x")(sp), v) == "Name(x)")
            },
            test("visit falls back to visitNode when no override is provided") {
                val v = new TagVisitor
                assertTrue(CstVisitor.visit(CstCharPattern('a')(sp), v) == "Node")
            },
            test("visit dispatches int literal to visitIntLiteral") {
                val v = new TagVisitor
                assertTrue(CstVisitor.visit(CstIntLiteral(3L)(sp), v) == "Int(3)")
            },
            test("visit dispatches variable pattern to visitVariablePattern") {
                val v = new TagVisitor
                val p = CstVariablePattern(CstName("a")(sp))(sp)
                assertTrue(CstVisitor.visit(p, v) == "VarPat(a)")
            }
        ),
        suite("traversal")(
            test("children returns direct children of a module") {
                val kids = CstVisitor.children(sampleModule)
                assertTrue(kids.size == 1) // only moduleDecl
            },
            test("children returns empty list for a leaf") {
                assertTrue(CstVisitor.children(CstName("x")(sp)).isEmpty)
            },
            test("count counts all nodes pre-order") {
                // Module + ModuleDeclaration + QualifiedName + Name + ExposingAll = 5
                assertTrue(CstVisitor.count(sampleModule) == 5)
            },
            test("foldLeft visits pre-order") {
                val q = CstQualifiedName(List(CstName("x")(sp)))(sp)
                val tags = CstVisitor
                    .foldLeft(q, List.empty[String])((acc, n) =>
                        (n match
                            case _: CstQualifiedName => "Q"
                            case cn: CstName         => s"N:${cn.value}"
                            case _                   => "?"
                        ) :: acc
                    )
                    .reverse
                assertTrue(tags == List("Q", "N:x"))
            },
            test("collect picks up nodes matching a partial function") {
                val q      = CstQualifiedName(List(CstName("a")(sp), CstName("b")(sp)))(sp)
                val values = CstVisitor.collect(q) { case x: CstName => x.value }
                assertTrue(values == List("a", "b"))
            }
        ),
        suite("extension methods")(
            test("node.visit delegates to CstVisitor.visit") {
                val v             = new TagVisitor
                val node: CstNode = CstIntLiteral(7L)(sp)
                assertTrue(node.visit(v) == "Int(7)")
            },
            test("node.children delegates to CstVisitor.children") {
                val q: CstNode = CstQualifiedName(List(CstName("a")(sp)))(sp)
                assertTrue(q.children.size == 1)
            },
            test("node.count delegates to CstVisitor.count") {
                val node: CstNode = CstIntLiteral(1L)(sp)
                assertTrue(node.count == 1)
            },
            test("node.fold delegates to CstVisitor.foldLeft") {
                val q: CstNode = CstQualifiedName(List(CstName("a")(sp)))(sp)
                assertTrue(q.fold(0)((acc, _) => acc + 1) == 2)
            },
            test("node.collect delegates to CstVisitor.collect") {
                val q: CstNode = CstQualifiedName(List(CstName("x")(sp)))(sp)
                val values     = q.collect { case cn: CstName => cn.value }
                assertTrue(values == List("x"))
            }
        )
    )
