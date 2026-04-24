package io.eleven19.krueger.ast

import parsley.{Failure, Success}
import zio.test.*

import io.eleven19.krueger.Krueger
import io.eleven19.krueger.Span
import io.eleven19.krueger.ast.AstQueryableTree.given
import io.eleven19.krueger.trees.CaptureName
import io.eleven19.krueger.trees.FieldName
import io.eleven19.krueger.trees.NodeTypeName
import io.eleven19.krueger.trees.QueryableTree
import io.eleven19.krueger.trees.query.*

object AstQueryableTreeSpec extends ZIOSpecDefault:

    private def parse(src: String): Module = Krueger.parseAst(src) match
        case Success(m)   => m
        case Failure(msg) => throw new AssertionError(s"parse failed: $msg")

    private val source =
        """module App exposing (..)
          |
          |import Html
          |
          |main = 42
          |""".stripMargin

    private val moduleTree: Module         = parse(source)
    private val root: AstNode              = moduleTree
    private val qt: QueryableTree[AstNode] = summon[QueryableTree[AstNode]]

    private def typeNameOf(n: AstNode): String = NodeTypeName.unwrap(qt.nodeType(n))

    private def field(s: String): FieldName = FieldName.make(s).toOption.get
    private def cap(s: String): CaptureName = CaptureName.make(s).toOption.get

    def spec = suite("QueryableTree[AstNode]")(
        suite("nodeType")(
            test("uses simple class name for concrete variants") {
                assertTrue(
                    typeNameOf(moduleTree) == "Module",
                    typeNameOf(moduleTree.name) == "QualifiedName"
                )
            }
        ),
        suite("children")(
            test("match AstVisitor.children for every node in the parsed module") {
                val mismatches = AstVisitor.collect(moduleTree) {
                    case n if qt.children(n) != AstVisitor.children(n) => n
                }
                assertTrue(mismatches.isEmpty)
            }
        ),
        suite("fields")(
            test("ValueDeclaration exposes typeAnnotation, parameters, body") {
                val valueDecl = moduleTree.declarations
                    .collectFirst { case v: ValueDeclaration => v }
                    .getOrElse(throw new AssertionError("no value declaration"))
                val fs = qt.fields(valueDecl)
                assertTrue(
                    fs.keySet == Set(field("typeAnnotation"), field("parameters"), field("body")),
                    fs(field("body")) == Seq(valueDecl.body),
                    fs(field("parameters")) == valueDecl.parameters.toSeq,
                    fs(field("typeAnnotation")) == valueDecl.typeAnnotation.toSeq
                )
            },
            test("Module exposes exposing, imports, declarations as fields") {
                val fs = qt.fields(moduleTree)
                assertTrue(
                    fs.keySet == Set(field("exposing"), field("imports"), field("declarations")),
                    fs(field("exposing")) == Seq(moduleTree.exposing),
                    fs(field("imports")) == moduleTree.imports.toSeq,
                    fs(field("declarations")) == moduleTree.declarations.toSeq
                )
            }
        ),
        suite("text")(
            test("IntLiteral stringifies its value") {
                val lit = IntLiteral(7L)(Span.zero)
                assertTrue(qt.text(lit).contains("7"))
            },
            test("StringLiteral returns its raw string") {
                val lit = StringLiteral("hello")(Span.zero)
                assertTrue(qt.text(lit).contains("hello"))
            },
            test("QualifiedName returns its dotted full name") {
                assertTrue(qt.text(moduleTree.name).contains("App"))
            },
            test("ValueDeclaration surfaces its name") {
                val valueDecl = moduleTree.declarations
                    .collectFirst { case v: ValueDeclaration => v }
                    .getOrElse(throw new AssertionError("no value declaration"))
                assertTrue(qt.text(valueDecl).contains("main"))
            }
        ),
        suite("integration with Matcher")(
            test("a node-pattern query surfaces each value declaration") {
                val query = QueryParser.parse("(ValueDeclaration) @v") match
                    case Success(q) => q
                    case Failure(e) => throw new AssertionError(s"bad query: $e")
                val ms    = Matcher.matches(query, root).toList
                val names = ms.flatMap(_.captures.get(cap("v"))).collect { case v: ValueDeclaration => v.name }
                assertTrue(names.toSet == Set("main"))
            }
        )
    )
