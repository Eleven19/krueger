package io.eleven19.krueger.compiler

import zio.test.*

import io.eleven19.krueger.cst.CstNode
import io.eleven19.krueger.cst.CstQueryableTree.given

object CompilerComponentSpec extends ZIOSpecDefault:

    private def run[A](eff: CompilerComponent.CompileEff[A]): CompilerComponent.CompileResult[A] =
        CompilerComponent.run(eff)

    private val component = Krueger.component

    private val simpleSource =
        """module M exposing (..)
          |
          |x = 1
          |""".stripMargin

    private val malformedSource = "module M exposing (..)\n\nx ="

    private val simpleQuery    = "(CstValueDeclaration) @v"
    private val malformedQuery = "(unbalanced"

    def spec = suite("CompilerComponent")(
        suite("parseCst")(
            test("happy path: valid source produces a CST with no errors") {
                val r = run(component.parseCst(simpleSource))
                assertTrue(
                    r.errors.isEmpty,
                    r.value.isRight
                )
            },
            test("negative: malformed source surfaces a ParseError; no exception thrown") {
                val r = run(component.parseCst(malformedSource))
                assertTrue(
                    r.errors.nonEmpty,
                    r.errors.forall {
                        case _: CompileError.ParseError => true
                        case _                          => false
                    },
                    r.value.isLeft
                )
            },
            test("edge: empty source still returns a well-formed Result (no exception)") {
                val r = run(component.parseCst(""))
                assertTrue(
                    r.logs != null,
                    r.errors != null
                )
            }
        ),
        suite("parseAst")(
            test("happy path: valid source produces an AST with no errors") {
                val r = run(component.parseAst(simpleSource))
                assertTrue(
                    r.errors.isEmpty,
                    r.value.isRight
                )
            },
            test("negative: malformed source surfaces a ParseError") {
                val r = run(component.parseAst(malformedSource))
                assertTrue(
                    r.errors.nonEmpty,
                    r.value.isLeft
                )
            }
        ),
        suite("parseQuery")(
            test("happy path: valid query parses to Query") {
                val r = run(component.parseQuery(simpleQuery))
                assertTrue(
                    r.errors.isEmpty,
                    r.value.isRight
                )
            },
            test("negative: malformed query surfaces a QueryError") {
                val r = run(component.parseQuery(malformedQuery))
                assertTrue(
                    r.errors.nonEmpty,
                    r.errors.forall {
                        case _: CompileError.QueryError => true
                        case _                          => false
                    },
                    r.value.isLeft
                )
            },
            test("edge: empty query surfaces a QueryError (not a crash)") {
                val r = run(component.parseQuery(""))
                assertTrue(
                    r.errors.nonEmpty,
                    r.value.isLeft
                )
            }
        ),
        suite("runQuery")(
            test("happy path: valid query against parsed CST produces non-empty matches") {
                val cstResult   = run(component.parseCst(simpleSource))
                val queryResult = run(component.parseQuery(simpleQuery))
                (cstResult.value, queryResult.value) match
                    case (Right(cst), Right(q)) =>
                        val r = run(component.runQuery[CstNode](q, cst))
                        assertTrue(
                            r.errors.isEmpty,
                            r.value.toOption.exists(_.nonEmpty)
                        )
                    case _ =>
                        assertTrue(false)
            },
            test("edge: query that matches no nodes returns empty list, no errors") {
                val cstResult   = run(component.parseCst(simpleSource))
                val queryResult = run(component.parseQuery("(nonexistent_node_type) @x"))
                (cstResult.value, queryResult.value) match
                    case (Right(cst), Right(q)) =>
                        val r = run(component.runQuery[CstNode](q, cst))
                        assertTrue(
                            r.errors.isEmpty,
                            r.value == Right(List.empty[MatchView])
                        )
                    case _ =>
                        assertTrue(false)
            },
            test("determinism: repeated runQuery calls produce equal results") {
                val cstResult   = run(component.parseCst(simpleSource))
                val queryResult = run(component.parseQuery(simpleQuery))
                (cstResult.value, queryResult.value) match
                    case (Right(cst), Right(q)) =>
                        val r1 = run(component.runQuery[CstNode](q, cst))
                        val r2 = run(component.runQuery[CstNode](q, cst))
                        assertTrue(r1.value == r2.value)
                    case _ =>
                        assertTrue(false)
            }
        ),
        suite("prettyQuery")(
            test("pure: returns canonical string for a parsed query") {
                val r = run(component.parseQuery(simpleQuery))
                r.value match
                    case Right(q) =>
                        val pretty = component.prettyQuery(q)
                        assertTrue(pretty.nonEmpty)
                    case Left(_) =>
                        assertTrue(false)
            }
        )
    )
