package io.eleven19.krueger.compiler

import zio.test.*

import io.eleven19.krueger.cst.CstNode
import io.eleven19.krueger.cst.CstQueryableTree.given

object CompilerComponentSpec extends ZIOSpecDefault:

    private def run[A](eff: CompilerComponent.CompileEff[Unit, A]): CompilerComponent.CompileResult[Unit, A] =
        CompilerComponent.runUnit(eff)

    private val compiler: CompilerComponent[Unit] = Krueger.compiler[Unit]

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
                val r = run(compiler.parseCst(simpleSource))
                assertTrue(
                    r.errors.isEmpty,
                    r.value.isRight
                )
            },
            test("negative: malformed source surfaces a ParseError; no exception thrown") {
                val r = run(compiler.parseCst(malformedSource))
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
                val r = run(compiler.parseCst(""))
                assertTrue(
                    r.logs != null,
                    r.errors != null
                )
            }
        ),
        suite("parseAst")(
            test("happy path: valid source produces an AST with no errors") {
                val r = run(compiler.parseAst(simpleSource))
                assertTrue(
                    r.errors.isEmpty,
                    r.value.isRight
                )
            },
            test("negative: malformed source surfaces a ParseError") {
                val r = run(compiler.parseAst(malformedSource))
                assertTrue(
                    r.errors.nonEmpty,
                    r.value.isLeft
                )
            }
        ),
        suite("parseQuery")(
            test("happy path: valid query parses to Query") {
                val r = run(compiler.parseQuery(simpleQuery))
                assertTrue(
                    r.errors.isEmpty,
                    r.value.isRight
                )
            },
            test("negative: malformed query surfaces a QueryError") {
                val r = run(compiler.parseQuery(malformedQuery))
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
                val r = run(compiler.parseQuery(""))
                assertTrue(
                    r.errors.nonEmpty,
                    r.value.isLeft
                )
            }
        ),
        suite("runQuery")(
            test("happy path: valid query against parsed CST produces non-empty matches") {
                val cstResult   = run(compiler.parseCst(simpleSource))
                val queryResult = run(compiler.parseQuery(simpleQuery))
                (cstResult.value, queryResult.value) match
                    case (Right(cst), Right(q)) =>
                        val r = run(compiler.runQuery[CstNode](q, cst))
                        assertTrue(
                            r.errors.isEmpty,
                            r.value.toOption.exists(_.nonEmpty)
                        )
                    case _ =>
                        assertTrue(false)
            },
            test("edge: query that matches no nodes returns empty list, no errors") {
                val cstResult   = run(compiler.parseCst(simpleSource))
                val queryResult = run(compiler.parseQuery("(nonexistent_node_type) @x"))
                (cstResult.value, queryResult.value) match
                    case (Right(cst), Right(q)) =>
                        val r = run(compiler.runQuery[CstNode](q, cst))
                        assertTrue(
                            r.errors.isEmpty,
                            r.value == Right(List.empty[MatchView])
                        )
                    case _ =>
                        assertTrue(false)
            },
            test("determinism: repeated runQuery calls produce equal results") {
                val cstResult   = run(compiler.parseCst(simpleSource))
                val queryResult = run(compiler.parseQuery(simpleQuery))
                (cstResult.value, queryResult.value) match
                    case (Right(cst), Right(q)) =>
                        val r1 = run(compiler.runQuery[CstNode](q, cst))
                        val r2 = run(compiler.runQuery[CstNode](q, cst))
                        assertTrue(r1.value == r2.value)
                    case _ =>
                        assertTrue(false)
            }
        ),
        suite("prettyQuery")(
            test("pure: returns canonical string for a parsed query") {
                val r = run(compiler.parseQuery(simpleQuery))
                r.value match
                    case Right(q) =>
                        val pretty = compiler.prettyQuery(q)
                        assertTrue(pretty.nonEmpty)
                    case Left(_) =>
                        assertTrue(false)
            }
        )
    )
