package io.eleven19.krueger.cst

import parsley.{Failure, Success}
import zio.test.*

import io.eleven19.krueger.Krueger
import io.eleven19.krueger.Span
import io.eleven19.krueger.cst.CstQueryableTree.given
import io.eleven19.krueger.trees.NodeTypeName
import io.eleven19.krueger.trees.QueryableTree
import io.eleven19.krueger.trees.query.*

object CstQueryableTreeSpec extends ZIOSpecDefault:

    private def parse(src: String): CstModule = Krueger.parseCst(src) match
        case Success(m)   => m
        case Failure(msg) => throw new AssertionError(s"parse failed: $msg")

    private val source =
        """module App exposing (..)
          |
          |import Html
          |
          |main = 42
          |""".stripMargin

    private val moduleTree: CstModule      = parse(source)
    private val root: CstNode              = moduleTree
    private val qt: QueryableTree[CstNode] = summon[QueryableTree[CstNode]]

    private def typeNameOf(n: CstNode): String = NodeTypeName.unwrap(qt.nodeType(n))

    def spec = suite("QueryableTree[CstNode]")(
        suite("nodeType")(
            test("uses simple class name for concrete variants") {
                assertTrue(
                    typeNameOf(moduleTree) == "CstModule",
                    typeNameOf(moduleTree.moduleDecl) == "CstModuleDeclaration",
                    typeNameOf(moduleTree.moduleDecl.name) == "CstQualifiedName"
                )
            },
            test("distinguishes value declarations from other declaration kinds") {
                val valueDecls = moduleTree.declarations.collect { case v: CstValueDeclaration => v }
                assertTrue(
                    valueDecls.size == 1,
                    valueDecls.head.name.value == "main",
                    valueDecls.forall(v => typeNameOf(v) == "CstValueDeclaration")
                )
            }
        ),
        suite("children")(
            test("match CstVisitor.children for every node in the parsed module") {
                val mismatches = CstVisitor.collect(moduleTree) {
                    case n if qt.children(n) != CstVisitor.children(n) => n
                }
                assertTrue(mismatches.isEmpty)
            }
        ),
        suite("fields")(
            test("CstValueDeclaration exposes name, body, patterns, annotation") {
                val valueDecl = moduleTree.declarations
                    .collectFirst { case v: CstValueDeclaration =>
                        v
                    }
                    .getOrElse(throw new AssertionError("no value declaration"))
                val fs = qt.fields(valueDecl)
                assertTrue(
                    fs.keySet == Set("annotation", "name", "patterns", "body"),
                    fs("name") == Seq(valueDecl.name),
                    fs("body") == Seq(valueDecl.body),
                    fs("patterns") == valueDecl.patterns.toSeq,
                    fs("annotation") == valueDecl.annotation.toSeq
                )
            },
            test("CstModule exposes moduleDecl, imports, declarations") {
                val fs = qt.fields(moduleTree)
                assertTrue(
                    fs.keySet.contains("moduleDecl"),
                    fs.keySet.contains("imports"),
                    fs.keySet.contains("declarations"),
                    fs("moduleDecl") == Seq(moduleTree.moduleDecl),
                    fs("imports") == moduleTree.imports.toSeq,
                    fs("declarations") == moduleTree.declarations.toSeq
                )
            },
            test("CstName has no fields") {
                val name = moduleTree.moduleDecl.name.parts.head
                assertTrue(qt.fields(name).isEmpty)
            }
        ),
        suite("text")(
            test("CstName returns its value") {
                val name = moduleTree.moduleDecl.name.parts.head
                assertTrue(qt.text(name).contains(name.value))
            },
            test("CstIntLiteral stringifies its value") {
                val intLit = CstIntLiteral(42L)(Span.zero)
                assertTrue(qt.text(intLit).contains("42"))
            },
            test("CstStringLiteral returns its raw string") {
                val strLit = CstStringLiteral("hi")(Span.zero)
                assertTrue(qt.text(strLit).contains("hi"))
            },
            test("compound nodes return None") {
                assertTrue(qt.text(moduleTree).isEmpty, qt.text(moduleTree.moduleDecl).isEmpty)
            }
        ),
        suite("integration with Matcher")(
            test("a node-pattern query surfaces every value declaration") {
                val query = QueryParser.parse("(CstValueDeclaration name: (CstName) @n)") match
                    case Success(q) => q
                    case Failure(e) => throw new AssertionError(s"bad query: $e")
                val ms    = Matcher.matches(query, root).toList
                val names = ms.flatMap(_.captures.get("n")).collect { case n: CstName => n.value }
                assertTrue(names.toSet == Set("main"))
            }
        )
    )
