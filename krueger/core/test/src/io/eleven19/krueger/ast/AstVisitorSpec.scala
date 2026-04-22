package io.eleven19.krueger.ast

import zio.test.*

import io.eleven19.krueger.Span
import io.eleven19.krueger.ast.AstVisitor.*
import io.eleven19.krueger.cst.*
import io.eleven19.krueger.parser.CstLowering

object AstVisitorSpec extends ZIOSpecDefault:

    private val sp                  = Span.zero
    private def cn(name: String)    = CstName(name)(sp)
    private def cqn(parts: String*) = CstQualifiedName(parts.map(cn).toList)(sp)

    private val sampleCst: CstModule =
        CstModule(
            CstModuleDeclaration(ModuleType.Plain, cqn("M"), CstExposingAll()(sp))(sp),
            IndexedSeq(CstImport(cqn("List"), None, None)(sp)),
            IndexedSeq.empty
        )(sp)

    private val sampleAst: Module = CstLowering.lowerModule(sampleCst)

    private class TagVisitor extends AstVisitor[String]:
        def visitNode(node: AstNode): String                         = "Node"
        override def visitQualifiedName(node: QualifiedName): String = s"QN(${node.fullName})"
        override def visitIntLiteral(node: IntLiteral): String       = s"Int(${node.value})"
        override def visitImport(node: Import): String               = s"Imp(${node.moduleName.fullName})"
        override def visitModule(node: Module): String               = s"Mod(${node.name.fullName})"

    def spec = suite("AstVisitor")(
        suite("dispatch")(
            test("visit routes to the specific visitor method") {
                val v            = new TagVisitor
                val lit: AstNode = IntLiteral(5L)(sp)
                assertTrue(AstVisitor.visit(lit, v) == "Int(5)")
            },
            test("visit falls back to visitNode when no override is provided") {
                val v          = new TagVisitor
                val s: AstNode = StringLiteral("hi")(sp)
                assertTrue(AstVisitor.visit(s, v) == "Node")
            }
        ),
        suite("traversal")(
            test("children returns direct children of a module") {
                // Module's children are: exposing :: imports ::: declarations
                assertTrue(AstVisitor.children(sampleAst).size == 2)
            },
            test("count counts all lowered nodes") {
                // Module + ExposingAll + Import = 3
                assertTrue(AstVisitor.count(sampleAst) == 3)
            },
            test("foldLeft visits pre-order") {
                val tags = AstVisitor
                    .foldLeft(sampleAst, List.empty[String])((acc, n) =>
                        (n match
                            case _: Module      => "M"
                            case _: Import      => "I"
                            case _: ExposingAll => "EA"
                            case _              => "?"
                        ) :: acc
                    )
                    .reverse
                assertTrue(tags == List("M", "EA", "I"))
            },
            test("collect picks up nodes matching a partial function") {
                val imports = AstVisitor.collect(sampleAst) { case i: Import => i.moduleName.fullName }
                assertTrue(imports == List("List"))
            }
        ),
        suite("extension methods")(
            test("node.visit delegates to AstVisitor.visit") {
                val v             = new TagVisitor
                val node: AstNode = IntLiteral(9L)(sp)
                assertTrue(node.visit(v) == "Int(9)")
            },
            test("node.children delegates to AstVisitor.children") {
                assertTrue(sampleAst.children.size == 2)
            },
            test("node.count delegates to AstVisitor.count") {
                assertTrue(sampleAst.count == 3)
            },
            test("node.fold delegates to AstVisitor.foldLeft") {
                assertTrue(sampleAst.fold(0)((acc, _) => acc + 1) == 3)
            },
            test("node.collect delegates to AstVisitor.collect") {
                val names = sampleAst.collect { case i: Import => i.moduleName.fullName }
                assertTrue(names == List("List"))
            }
        )
    )
