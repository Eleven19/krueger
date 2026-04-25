package io.eleven19.krueger.compiler

import zio.test.*

import io.eleven19.krueger.cst.CstNode
import io.eleven19.krueger.cst.CstQueryableTree.given

object CompilerComponentSpec extends ZIOSpecDefault:

    private final case class Snapshot[A](
        logs: Vector[String],
        errors: Vector[CompileError],
        value: Either[Vector[CompileError], A]
    ) derives CanEqual

    private def run[A](eff: CompilerComponent.CompileEff[Unit, A]): CompilerComponent.CompileResult[Unit, A] =
        CompilerComponent.runUnit(eff)

    private def snapshot[A](r: CompilerComponent.CompileResult[Unit, A]): Snapshot[A] =
        Snapshot(
            logs = r.logs,
            errors = r.errors,
            value = r.value
        )

    private def requireRight[A](either: Either[?, A], clue: String): A =
        either match
            case Right(value) => value
            case Left(_)      => throw new AssertionError(clue)

    private val compiler: CompilerComponent[Unit] = Krueger.compiler[Unit]

    private val simpleSource =
        """module M exposing (..)
          |
          |x = 1
          |""".stripMargin

    private val malformedSource = "module M exposing (..)\n\nx ="

    private val simpleQuery    = "(CstValueDeclaration) @v"
    private val malformedQuery = "(unbalanced"
    private val zeroMatchQuery = "(nonexistent_node_type) @x"

    private val expectedMalformedSourceMessage =
        List(
            "(line 3, column 4):",
            "  unexpected end of input",
            "  expected \"\"\", \"'\", \"+\", \"-\", -, ., \\, case, digit, identifier, if, let, open brace, open parenthesis, or open square bracket",
            "  >",
            "  >x =",
            "      ^"
        ).mkString("\n")

    private val expectedEmptySourceMessage =
        List(
            "(line 1, column 1):",
            "  unexpected end of input",
            "  expected effect, module, or port",
            "  >",
            "   ^"
        ).mkString("\n")

    private val expectedEmptyQueryMessage =
        List(
            "Query parse failed: (line 1, column 1):",
            "  unexpected end of input",
            "  expected \"(\", \";;\", \"[\", \"_\", or at least one query pattern",
            "  >",
            "   ^"
        ).mkString("\n")

    def spec = suite("CompilerComponent")(
        suite("cross-platform fixture snapshots")(
            test("happy path: valid source and query produce the expected MatchView list") {
                val cst   = requireRight(run(compiler.parseCst(simpleSource)).value, "expected parseCst(simpleSource) to succeed")
                val query = requireRight(run(compiler.parseQuery(simpleQuery)).value, "expected parseQuery(simpleQuery) to succeed")
                val actual =
                    snapshot(run(compiler.runQuery[CstNode](query, cst)))

                val expected = Snapshot(
                    logs = Vector.empty,
                    errors = Vector.empty,
                    value = Right(
                        List(
                            MatchView(
                                rootNodeType = "CstValueDeclaration",
                                rootText = None,
                                captures = Map(
                                    "v" -> CapturedNode(
                                        nodeType = "CstValueDeclaration",
                                        text = None,
                                        childCount = 2
                                    )
                                )
                            )
                        )
                    )
                )

                assertTrue(actual == expected)
            },
            test("negative path: malformed source produces the expected ParseError envelope") {
                val actual = snapshot(run(compiler.parseCst(malformedSource)))
                val expectedError = CompileError.ParseError(
                    phase = "cst",
                    message = expectedMalformedSourceMessage
                )
                val expected = Snapshot(
                    logs = Vector.empty,
                    errors = Vector(expectedError),
                    value = Left(Vector(expectedError))
                )

                assertTrue(actual == expected)
            },
            test("edge path: zero-match query returns an empty match list with no errors") {
                val cst   = requireRight(run(compiler.parseCst(simpleSource)).value, "expected parseCst(simpleSource) to succeed")
                val query = requireRight(run(compiler.parseQuery(zeroMatchQuery)).value, "expected parseQuery(zeroMatchQuery) to succeed")
                val actual =
                    snapshot(run(compiler.runQuery[CstNode](query, cst)))
                val expected = Snapshot(
                    logs = Vector.empty,
                    errors = Vector.empty,
                    value = Right(List.empty[MatchView])
                )

                assertTrue(actual == expected)
            },
            test("edge path: empty source returns the expected ParseError envelope") {
                val actual = snapshot(run(compiler.parseCst("")))
                val expectedError = CompileError.ParseError(
                    phase = "cst",
                    message = expectedEmptySourceMessage
                )
                val expected = Snapshot(
                    logs = Vector.empty,
                    errors = Vector(expectedError),
                    value = Left(Vector(expectedError))
                )

                assertTrue(actual == expected)
            },
            test("edge path: empty query returns the expected QueryError envelope") {
                val actual = snapshot(run(compiler.parseQuery("")))
                val expectedError = CompileError.QueryError(
                    message = expectedEmptyQueryMessage
                )
                val expected = Snapshot(
                    logs = Vector.empty,
                    errors = Vector(expectedError),
                    value = Left(Vector(expectedError))
                )

                assertTrue(actual == expected)
            },
            test("determinism: repeated runQuery calls produce identical snapshots") {
                val cst   = requireRight(run(compiler.parseCst(simpleSource)).value, "expected parseCst(simpleSource) to succeed")
                val query = requireRight(run(compiler.parseQuery(simpleQuery)).value, "expected parseQuery(simpleQuery) to succeed")
                val first = snapshot(run(compiler.runQuery[CstNode](query, cst)))
                val second = snapshot(run(compiler.runQuery[CstNode](query, cst)))

                assertTrue(first == second)
            }
        ),
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
                val queryResult = run(compiler.parseQuery(zeroMatchQuery))
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
