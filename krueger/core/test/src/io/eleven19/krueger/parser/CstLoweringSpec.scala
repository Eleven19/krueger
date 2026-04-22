package io.eleven19.krueger.parser

import zio.test.*

import io.eleven19.krueger.Span
import io.eleven19.krueger.cst.*
import io.eleven19.krueger.ast

object CstLoweringSpec extends ZIOSpecDefault:

    private val sp                 = Span.zero
    private def n(name: String)    = CstName(name)(sp)
    private def qn(parts: String*) = CstQualifiedName(parts.map(n).toList)(sp)

    private def moduleWithDecl(decl: CstDeclaration): CstModule =
        CstModule(
            CstModuleDeclaration(ModuleType.Plain, qn("M"), CstExposingAll()(sp))(sp),
            Nil,
            List(decl)
        )(sp)

    def spec = suite("CstLowering")(
        test("lowerModule maps module name, imports, and declarations") {
            val cst = CstModule(
                CstModuleDeclaration(ModuleType.Plain, qn("Main"), CstExposingAll()(sp))(sp),
                List(CstImport(qn("List"), None, None)(sp)),
                Nil
            )(sp)
            val m = CstLowering.lowerModule(cst)
            assertTrue(
                m.name.fullName == "Main",
                m.exposing.isInstanceOf[ast.ExposingAll],
                m.imports.map(_.moduleName.fullName) == List("List")
            )
        },
        test("lowerModule lowers an explicit exposing list") {
            val items = List[CstExposedItem](
                CstExposedValue(n("foo"))(sp),
                CstExposedOperator(n("++"))(sp),
                CstExposedType(n("Foo"), Some(CstExposedConstructorsAll()(sp)))(sp),
                CstExposedType(n("Bar"), None)(sp)
            )
            val cst = CstModule(
                CstModuleDeclaration(ModuleType.Plain, qn("M"), CstExposingExplicit(items)(sp))(sp),
                Nil,
                Nil
            )(sp)
            val m = CstLowering.lowerModule(cst)
            val exp = m.exposing match
                case e: ast.ExposingExplicit => e.items
                case _                       => Nil
            val types  = exp.collect { case t: ast.ExposedType => (t.name, t.exposeConstructors) }
            val values = exp.collect { case v: ast.ExposedValue => v.name }
            val ops    = exp.collect { case o: ast.ExposedOperator => o.name }
            assertTrue(
                values == List("foo"),
                ops == List("++"),
                types == List(("Foo", true), ("Bar", false))
            )
        },
        test("lowerQualifiedName flattens parts via fullName") {
            val cst = CstModule(
                CstModuleDeclaration(ModuleType.Plain, qn("Http", "Body"), CstExposingAll()(sp))(sp),
                Nil,
                Nil
            )(sp)
            assertTrue(CstLowering.lowerModule(cst).name.fullName == "Http.Body")
        },
        test("lowerPattern strips CstParenthesizedPattern") {
            val inner   = CstVariablePattern(n("x"))(sp)
            val wrapped = CstParenthesizedPattern(inner)(sp)
            val lowered = CstLowering.lowerPattern(wrapped)
            assertTrue(lowered == ast.VariablePattern("x")(sp))
        },
        test("lowerPattern strips nested parens") {
            val nested  = CstParenthesizedPattern(CstParenthesizedPattern(CstVariablePattern(n("y"))(sp))(sp))(sp)
            val lowered = CstLowering.lowerPattern(nested)
            assertTrue(lowered == ast.VariablePattern("y")(sp))
        },
        test("lowerExpression preserves CstParenthesized as ast.Parenthesized") {
            val wrapped = CstParenthesized(CstIntLiteral(1L)(sp))(sp)
            val lowered = CstLowering.lowerExpression(wrapped)
            assertTrue(lowered.isInstanceOf[ast.Parenthesized])
        },
        test("lowerLetBinding uses variable name when pattern is a variable") {
            val decl = CstValueDeclaration(
                None,
                n("main"),
                Nil,
                CstLetIn(
                    List(
                        CstLetBinding(
                            None,
                            CstVariablePattern(n("x"))(sp),
                            Nil,
                            CstIntLiteral(1L)(sp)
                        )(sp)
                    ),
                    CstVariableRef(qn("x"))(sp)
                )(sp)
            )(sp)
            val m     = CstLowering.lowerModule(moduleWithDecl(decl))
            val letIn = m.declarations.head.asInstanceOf[ast.ValueDeclaration].body.asInstanceOf[ast.LetIn]
            assertTrue(letIn.bindings.head.name == "x")
        },
        test("lowerLetBinding falls back to <pattern> for non-variable patterns") {
            val decl = CstValueDeclaration(
                None,
                n("main"),
                Nil,
                CstLetIn(
                    List(
                        CstLetBinding(
                            None,
                            CstUnitPattern()(sp),
                            Nil,
                            CstIntLiteral(1L)(sp)
                        )(sp)
                    ),
                    CstIntLiteral(2L)(sp)
                )(sp)
            )(sp)
            val m     = CstLowering.lowerModule(moduleWithDecl(decl))
            val letIn = m.declarations.head.asInstanceOf[ast.ValueDeclaration].body.asInstanceOf[ast.LetIn]
            assertTrue(letIn.bindings.head.name == "<pattern>")
        },
        test("lowerDeclaration carries a value's type annotation onto the AST") {
            val annotated = CstValueDeclaration(
                annotation = Some(
                    CstTypeAnnotation(
                        n("foo"),
                        CstTypeReference(qn("Int"))(sp)
                    )(sp)
                ),
                name = n("foo"),
                patterns = Nil,
                body = CstIntLiteral(42L)(sp)
            )(sp)
            val lowered = CstLowering.lowerDeclaration(annotated).asInstanceOf[ast.ValueDeclaration]
            assertTrue(
                lowered.name == "foo",
                lowered.typeAnnotation.exists(_.isInstanceOf[ast.TypeReference])
            )
        },
        test("lowerTypeExpression lowers function types") {
            val a       = CstTypeVariable(n("a"))(sp)
            val b       = CstTypeVariable(n("b"))(sp)
            val t       = CstFunctionType(a, b)(sp)
            val lowered = CstLowering.lowerTypeExpression(t)
            assertTrue(lowered.isInstanceOf[ast.FunctionType])
        }
    )
