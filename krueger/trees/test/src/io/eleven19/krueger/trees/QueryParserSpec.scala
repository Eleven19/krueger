package io.eleven19.krueger.trees

import parsley.{Failure, Success}
import zio.test.*

import io.eleven19.krueger.trees.query.*

object QueryParserSpec extends ZIOSpecDefault:

    private val leafType: NodeTypeName  = NodeTypeName.make("Leaf").toOption.get
    private val namedType: NodeTypeName = NodeTypeName.make("Named").toOption.get

    private def parseOrFail(source: String): Query =
        QueryParser.parse(source) match
            case Success(q)   => q
            case Failure(msg) => throw new AssertionError(s"parse failed: $msg\nSource:\n$source")

    def spec = suite("QueryParser")(
        suite("node patterns")(
            test("bare node") {
                assertTrue(parseOrFail("(Leaf)") == Query(NodePattern(leafType, Nil, None), Nil))
            },
            test("node with capture") {
                assertTrue(parseOrFail("(Leaf) @l") == Query(NodePattern(leafType, Nil, Some("l")), Nil))
            },
            test("node with one field") {
                val expected = Query(
                    NodePattern(namedType, List(FieldPattern("name", NodePattern(leafType, Nil, None))), None),
                    Nil
                )
                assertTrue(parseOrFail("(Named name: (Leaf))") == expected)
            },
            test("node with multiple fields") {
                val expected = Query(
                    NodePattern(
                        namedType,
                        List(
                            FieldPattern("name", NodePattern(leafType, Nil, Some("n"))),
                            FieldPattern("body", NodePattern(leafType, Nil, Some("b")))
                        ),
                        Some("outer")
                    ),
                    Nil
                )
                assertTrue(parseOrFail("(Named name: (Leaf) @n body: (Leaf) @b) @outer") == expected)
            }
        ),
        suite("wildcards")(
            test("bare wildcard") {
                assertTrue(parseOrFail("_") == Query(WildcardPattern(None), Nil))
            },
            test("parenthesised wildcard with capture") {
                assertTrue(parseOrFail("(_) @x") == Query(WildcardPattern(Some("x")), Nil))
            },
            test("wildcard as field sub-pattern") {
                val expected = Query(
                    NodePattern(namedType, List(FieldPattern("body", WildcardPattern(Some("b")))), None),
                    Nil
                )
                assertTrue(parseOrFail("(Named body: _ @b)") == expected)
            }
        ),
        suite("predicates")(
            test("eq? with two capture refs") {
                val q = parseOrFail("(Leaf) @l (#eq? @l @l)")
                assertTrue(q.predicates == List(EqPredicate(CaptureRef("l"), CaptureRef("l"))))
            },
            test("eq? accepts a string literal on the right") {
                val q = parseOrFail("(Leaf) @l (#eq? @l \"hello\")")
                assertTrue(q.predicates == List(EqPredicate(CaptureRef("l"), StringArg("hello"))))
            },
            test("match? pairs capture ref and regex") {
                val q = parseOrFail("(Leaf) @l (#match? @l \"^hi\")")
                assertTrue(q.predicates == List(MatchPredicate(CaptureRef("l"), "^hi")))
            },
            test("multiple predicates accumulate in order") {
                val q = parseOrFail("(Leaf) @l (#eq? @l @l) (#match? @l \"x\")")
                assertTrue(q.predicates.size == 2)
            }
        ),
        suite("trivia")(
            test("line comments are ignored") {
                val q = parseOrFail(";; top level\n(Leaf) ;; trailing\n")
                assertTrue(q == Query(NodePattern(leafType, Nil, None), Nil))
            },
            test("whitespace is flexible") {
                val q = parseOrFail("  \n  ( Leaf   )  \n  @l  ")
                assertTrue(q == Query(NodePattern(leafType, Nil, Some("l")), Nil))
            }
        ),
        suite("known-type validation")(
            test("accepts queries whose node types are all in knownTypes") {
                val res = QueryParser.parse("(Leaf)", Set("Leaf"))
                assertTrue(res.isSuccess)
            },
            test("rejects an unknown node type with a helpful message") {
                val res: parsley.Result[String, Query] = QueryParser.parse("(Unknown)", Set("Leaf"))
                val msg: String                        = res.toEither.left.getOrElse("")
                assertTrue(res.isFailure, msg.contains("Unknown"))
            }
        ),
        suite("errors")(
            test("empty input fails cleanly") {
                assertTrue(QueryParser.parse("").isFailure)
            },
            test("unmatched paren fails cleanly") {
                assertTrue(QueryParser.parse("(Leaf").isFailure)
            },
            test("missing colon after field name fails cleanly") {
                assertTrue(QueryParser.parse("(Named name (Leaf))").isFailure)
            }
        )
    )
