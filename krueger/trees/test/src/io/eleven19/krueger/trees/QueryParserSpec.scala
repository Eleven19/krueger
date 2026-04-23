package io.eleven19.krueger.trees

import parsley.{Failure, Success}
import zio.test.*

import io.eleven19.krueger.trees.query.*

object QueryParserSpec extends ZIOSpecDefault:

    private val leafType: NodeTypeName  = NodeTypeName.make("Leaf").toOption.get
    private val namedType: NodeTypeName = NodeTypeName.make("Named").toOption.get

    private val n: CaptureName     = CaptureName.make("n").toOption.get
    private val b: CaptureName     = CaptureName.make("b").toOption.get
    private val l: CaptureName     = CaptureName.make("l").toOption.get
    private val x: CaptureName     = CaptureName.make("x").toOption.get
    private val outer: CaptureName = CaptureName.make("outer").toOption.get

    private val nameField: FieldName = FieldName.make("name").toOption.get
    private val bodyField: FieldName = FieldName.make("body").toOption.get

    private def rx(s: String): RegexPattern = RegexPattern.make(s).toOption.get

    private def parseOrFail(source: String): Query =
        QueryParser.parse(source) match
            case Success(q)   => q
            case Failure(msg) => throw new AssertionError(s"parse failed: $msg\nSource:\n$source")

    def spec = suite("QueryParser")(
        suite("node patterns")(
            test("bare node") {
                assertTrue(parseOrFail("(Leaf)") == Query(NodePattern(leafType, Nil, Nil, None), Nil))
            },
            test("node with capture") {
                assertTrue(parseOrFail("(Leaf) @l") == Query(NodePattern(leafType, Nil, Nil, Some(l)), Nil))
            },
            test("node with one field") {
                val expected = Query(
                    NodePattern(namedType, List(FieldPattern(nameField, NodePattern(leafType, Nil, Nil, None))), Nil, None),
                    Nil
                )
                assertTrue(parseOrFail("(Named name: (Leaf))") == expected)
            },
            test("node with multiple fields") {
                val expected = Query(
                    NodePattern(
                        namedType,
                        List(
                            FieldPattern(nameField, NodePattern(leafType, Nil, Nil, Some(n))),
                            FieldPattern(bodyField, NodePattern(leafType, Nil, Nil, Some(b)))
                        ),
                        Nil,
                        Some(outer)
                    ),
                    Nil
                )
                assertTrue(parseOrFail("(Named name: (Leaf) @n body: (Leaf) @b) @outer") == expected)
            },
            test("node with unfielded child patterns") {
                val expected = Query(
                    NodePattern(
                        namedType,
                        Nil,
                        List(NodePattern(leafType, Nil, Nil, Some(n)), NodePattern(leafType, Nil, Nil, Some(b))),
                        None
                    ),
                    Nil
                )
                assertTrue(parseOrFail("(Named (Leaf) @n (Leaf) @b)") == expected)
            },
            test("node with mixed field and unfielded child patterns") {
                val expected = Query(
                    NodePattern(
                        namedType,
                        List(FieldPattern(nameField, NodePattern(leafType, Nil, Nil, Some(n)))),
                        List(NodePattern(leafType, Nil, Nil, Some(b))),
                        None
                    ),
                    Nil
                )
                assertTrue(parseOrFail("(Named name: (Leaf) @n (Leaf) @b)") == expected)
            },
            test("multiple top-level patterns in one query are accepted") {
                val res = QueryParser.parse("(Leaf) (Named)")
                assertTrue(res.isSuccess)
            },
            test("multiple top-level patterns can still include predicates") {
                val res = QueryParser.parse("(Leaf) @l (Named) (#eq? @l \"hi\")")
                assertTrue(res.isSuccess)
            }
        ),
        suite("wildcards")(
            test("bare wildcard") {
                assertTrue(parseOrFail("_") == Query(WildcardPattern(None), Nil))
            },
            test("parenthesised wildcard with capture") {
                assertTrue(parseOrFail("(_) @x") == Query(WildcardPattern(Some(x)), Nil))
            },
            test("wildcard as field sub-pattern") {
                val expected = Query(
                    NodePattern(namedType, List(FieldPattern(bodyField, WildcardPattern(Some(b)))), Nil, None),
                    Nil
                )
                assertTrue(parseOrFail("(Named body: _ @b)") == expected)
            }
        ),
        suite("predicates")(
            test("eq? with two capture refs") {
                val q = parseOrFail("(Leaf) @l (#eq? @l @l)")
                assertTrue(q.predicates == List(EqPredicate(CaptureRef(l), CaptureRef(l))))
            },
            test("eq? accepts a string literal on the right") {
                val q = parseOrFail("(Leaf) @l (#eq? @l \"hello\")")
                assertTrue(q.predicates == List(EqPredicate(CaptureRef(l), StringArg("hello"))))
            },
            test("match? pairs capture ref and regex") {
                val q = parseOrFail("(Leaf) @l (#match? @l \"^hi\")")
                assertTrue(q.predicates == List(MatchPredicate(CaptureRef(l), rx("^hi"))))
            },
            test("multiple predicates accumulate in order") {
                val q = parseOrFail("(Leaf) @l (#eq? @l @l) (#match? @l \"x\")")
                assertTrue(q.predicates.size == 2)
            },
            test("#eq? with too few args fails cleanly") {
                assertTrue(QueryParser.parse("(Leaf) @l (#eq? @l)").isFailure)
            },
            test("#eq? with too many args fails cleanly") {
                assertTrue(QueryParser.parse("(Leaf) @l (#eq? @l @l @l)").isFailure)
            },
            test("#match? with too few args fails cleanly") {
                assertTrue(QueryParser.parse("(Leaf) @l (#match? @l)").isFailure)
            },
            test("#match? with too many args fails cleanly") {
                assertTrue(QueryParser.parse("(Leaf) @l (#match? @l \"x\" \"y\")").isFailure)
            },
            test("#eq? rejects string left-hand side argument kind") {
                assertTrue(QueryParser.parse("(Leaf) @l (#eq? \"x\" @l)").isFailure)
            },
            test("#match? rejects string left-hand side argument kind") {
                assertTrue(QueryParser.parse("(Leaf) @l (#match? \"x\" \"^x\")").isFailure)
            }
        ),
        suite("trivia")(
            test("line comments are ignored") {
                val q = parseOrFail(";; top level\n(Leaf) ;; trailing\n")
                assertTrue(q == Query(NodePattern(leafType, Nil, Nil, None), Nil))
            },
            test("whitespace is flexible") {
                val q = parseOrFail("  \n  ( Leaf   )  \n  @l  ")
                assertTrue(q == Query(NodePattern(leafType, Nil, Nil, Some(l)), Nil))
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
            },
            test("rejects unknown node types appearing in a later top-level pattern") {
                val res: parsley.Result[String, Query] = QueryParser.parse("(Leaf) (Unknown)", Set("Leaf"))
                val msg: String                        = res.toEither.left.getOrElse("")
                assertTrue(res.isFailure, msg.contains("Unknown"))
            }
        ),
        suite("predicate capture validation")(
            test("rejects predicate references to captures that are never bound") {
                val res: parsley.Result[String, Query] = QueryParser.parse("(Leaf) @l (#eq? @missing \"x\")")
                val msg: String                        = res.toEither.left.getOrElse("")
                assertTrue(res.isFailure, msg.contains("unknown capture"), msg.contains("@missing"))
            },
            test("accepts predicates that reference bound captures") {
                val res = QueryParser.parse("(Leaf) @l (#eq? @l \"x\")")
                assertTrue(res.isSuccess)
            },
            test("rejects duplicate capture names in a single pattern tree") {
                val res: parsley.Result[String, Query] = QueryParser.parse("(Branch (Leaf) @x (Leaf) @x)")
                val msg: String                        = res.toEither.left.getOrElse("")
                assertTrue(res.isFailure, msg.contains("duplicate capture"), msg.contains("@x"))
            },
            test("rejects duplicate capture names across multi-pattern roots") {
                val res: parsley.Result[String, Query] = QueryParser.parse("(Leaf) @x (Named) @x")
                val msg: String                        = res.toEither.left.getOrElse("")
                assertTrue(res.isFailure, msg.contains("duplicate capture"), msg.contains("@x"))
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
            },
            test("invalid regex in #match? fails cleanly") {
                assertTrue(QueryParser.parse("(Leaf) @l (#match? @l \"[unclosed\")").isFailure)
            },
            test("malformed input matrix returns stable parse-failure prefix") {
                val malformed = List(
                    "(Leaf",
                    "(Leaf) @",
                    "(Named name (Leaf))",
                    "(Leaf) @l (#eq? @l)",
                    "(Leaf) @l (#match? @l \"[unterminated\")"
                )

                val messages = malformed.map { source =>
                    QueryParser.parse(source).toEither.left.getOrElse("")
                }

                assertTrue(messages.forall(_.startsWith("Query parse failed:")))
            },
            test("unknown predicate fails with predicate token in message") {
                val res = QueryParser.parse("(Leaf) @l (#foo? @l \"x\")")
                val msg = res.toEither.left.getOrElse("")
                assertTrue(res.isFailure, msg.contains("#foo?"))
            },
            test("unknown predicate has explicit unknown-predicate wording") {
                val res = QueryParser.parse("(Leaf) @l (#foo? @l \"x\")")
                val msg = res.toEither.left.getOrElse("")
                assertTrue(res.isFailure, msg.toLowerCase.contains("unknown predicate"))
            }
        )
    )
