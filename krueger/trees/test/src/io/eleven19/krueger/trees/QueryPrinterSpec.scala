package io.eleven19.krueger.trees

import parsley.{Failure, Success}
import zio.test.*

import io.eleven19.krueger.trees.query.*

/** Verifies that [[QueryPrinter]] produces canonical S-expression output that round-trips through [[QueryParser]]. */
object QueryPrinterSpec extends ZIOSpecDefault:

    private val leafType: NodeTypeName  = NodeTypeName.make("Leaf").toOption.get
    private val namedType: NodeTypeName = NodeTypeName.make("Named").toOption.get

    private val n: CaptureName     = CaptureName.make("n").toOption.get
    private val b: CaptureName     = CaptureName.make("b").toOption.get
    private val l: CaptureName     = CaptureName.make("l").toOption.get
    private val outer: CaptureName = CaptureName.make("outer").toOption.get
    private val x: CaptureName     = CaptureName.make("x").toOption.get

    private val nameField: FieldName = FieldName.make("name").toOption.get
    private val bodyField: FieldName = FieldName.make("body").toOption.get

    private def rx(s: String): RegexPattern = RegexPattern.make(s).toOption.get

    private def parse(source: String): Query = QueryParser.parse(source) match
        case Success(q)   => q
        case Failure(msg) => throw new AssertionError(s"parse failed: $msg\nSource:\n$source")

    private def roundTrips(source: String): Boolean =
        val canonical = QueryPrinter.print(parse(source))
        QueryParser.parse(canonical).isSuccess

    def spec = suite("QueryPrinter")(
        suite("node patterns")(
            test("bare node pattern") {
                val q   = Query(NodePattern(leafType, Nil, Nil, None), Nil)
                val out = QueryPrinter.print(q)
                assertTrue(out == "(Leaf)")
            },
            test("node with capture") {
                val q   = Query(NodePattern(leafType, Nil, Nil, Some(l)), Nil)
                val out = QueryPrinter.print(q)
                assertTrue(out == "(Leaf) @l")
            },
            test("node with field pattern") {
                val q = Query(
                    NodePattern(
                        namedType,
                        List(FieldPattern(nameField, NodePattern(leafType, Nil, Nil, None))),
                        Nil,
                        None
                    ),
                    Nil
                )
                val out = QueryPrinter.print(q)
                assertTrue(out == "(Named name: (Leaf))")
            },
            test("node with multiple fields and outer capture") {
                val q = Query(
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
                val out = QueryPrinter.print(q)
                assertTrue(out == "(Named name: (Leaf) @n body: (Leaf) @b) @outer")
            },
            test("node with unfielded child patterns") {
                val q = Query(
                    NodePattern(
                        namedType,
                        Nil,
                        List(NodePattern(leafType, Nil, Nil, Some(n)), NodePattern(leafType, Nil, Nil, Some(b))),
                        None
                    ),
                    Nil
                )
                val out = QueryPrinter.print(q)
                assertTrue(out == "(Named (Leaf) @n (Leaf) @b)")
            },
            test("node with anchored adjacent child patterns") {
                val q = Query(
                    NodePattern(
                        namedType,
                        Nil,
                        List(NodePattern(leafType, Nil, Nil, Some(n)), NodePattern(leafType, Nil, Nil, Some(b))),
                        None,
                        Set(0)
                    ),
                    Nil
                )
                val out = QueryPrinter.print(q)
                assertTrue(out == "(Named (Leaf) @n . (Leaf) @b)")
            },
            test("node with negated field constraint") {
                val q = Query(
                    NodePattern(namedType, Nil, Nil, None, Set.empty, Set(nameField)),
                    Nil
                )
                val out = QueryPrinter.print(q)
                assertTrue(out == "(Named !name)")
            },
            test("node with optional child quantifier") {
                val q = Query(
                    NodePattern(
                        namedType,
                        Nil,
                        List(NodePattern(leafType, Nil, Nil, Some(n))),
                        None,
                        Set.empty,
                        Set.empty,
                        Map(0 -> QuantifierKind.Optional)
                    ),
                    Nil
                )
                val out = QueryPrinter.print(q)
                assertTrue(out == "(Named (Leaf) @n?)")
            },
            test("node with zero-or-more child quantifier") {
                val q = Query(
                    NodePattern(
                        namedType,
                        Nil,
                        List(NodePattern(leafType, Nil, Nil, None)),
                        None,
                        Set.empty,
                        Set.empty,
                        Map(0 -> QuantifierKind.ZeroOrMore)
                    ),
                    Nil
                )
                val out = QueryPrinter.print(q)
                assertTrue(out == "(Named (Leaf)*)")
            },
            test("node with one-or-more child quantifier") {
                val q = Query(
                    NodePattern(
                        namedType,
                        Nil,
                        List(NodePattern(leafType, Nil, Nil, None)),
                        None,
                        Set.empty,
                        Set.empty,
                        Map(0 -> QuantifierKind.OneOrMore)
                    ),
                    Nil
                )
                val out = QueryPrinter.print(q)
                assertTrue(out == "(Named (Leaf)+)")
            }
        ),
        suite("wildcards")(
            test("bare wildcard") {
                assertTrue(QueryPrinter.print(Query(WildcardPattern(None), Nil)) == "_")
            },
            test("wildcard with capture") {
                assertTrue(QueryPrinter.print(Query(WildcardPattern(Some(x)), Nil)) == "_ @x")
            }
        ),
        suite("alternation")(
            test("alternation without capture") {
                val q = Query(
                    AlternationPattern(
                        List(NodePattern(leafType, Nil, Nil, Some(n)), NodePattern(namedType, Nil, Nil, Some(b))),
                        None
                    ),
                    Nil
                )
                val out = QueryPrinter.print(q)
                assertTrue(out == "[(Leaf) @n (Named) @b]")
            },
            test("alternation with outer capture") {
                val q = Query(
                    AlternationPattern(
                        List(NodePattern(leafType, Nil, Nil, None), WildcardPattern(None)),
                        Some(x)
                    ),
                    Nil
                )
                val out = QueryPrinter.print(q)
                assertTrue(out == "[(Leaf) _] @x")
            }
        ),
        suite("predicates")(
            test("#eq? with two capture refs") {
                val q   = Query(NodePattern(leafType, Nil, Nil, Some(l)), List(EqPredicate(CaptureRef(l), CaptureRef(n))))
                val out = QueryPrinter.print(q)
                assertTrue(out == "(Leaf) @l (#eq? @l @n)")
            },
            test("#eq? with string literal on the right") {
                val q   = Query(NodePattern(leafType, Nil, Nil, Some(l)), List(EqPredicate(CaptureRef(l), StringArg("hello"))))
                val out = QueryPrinter.print(q)
                assertTrue(out == "(Leaf) @l (#eq? @l \"hello\")")
            },
            test("#match? with capture ref and regex") {
                val q   = Query(NodePattern(leafType, Nil, Nil, Some(l)), List(MatchPredicate(CaptureRef(l), rx("^hi"))))
                val out = QueryPrinter.print(q)
                assertTrue(out == "(Leaf) @l (#match? @l \"^hi\")")
            },
            test("#not-eq? predicate") {
                val q   = Query(NodePattern(leafType, Nil, Nil, Some(l)), List(NotEqPredicate(CaptureRef(l), StringArg("bye"))))
                val out = QueryPrinter.print(q)
                assertTrue(out == "(Leaf) @l (#not-eq? @l \"bye\")")
            },
            test("#not-match? predicate") {
                val q   = Query(NodePattern(leafType, Nil, Nil, Some(l)), List(NotMatchPredicate(CaptureRef(l), rx("^z"))))
                val out = QueryPrinter.print(q)
                assertTrue(out == "(Leaf) @l (#not-match? @l \"^z\")")
            },
            test("multiple predicates are appended in order") {
                val q = Query(
                    NodePattern(leafType, Nil, Nil, Some(l)),
                    List(EqPredicate(CaptureRef(l), StringArg("x")), MatchPredicate(CaptureRef(l), rx("y")))
                )
                val out = QueryPrinter.print(q)
                assertTrue(out == "(Leaf) @l (#eq? @l \"x\") (#match? @l \"y\")")
            }
        ),
        suite("multi-pattern")(
            test("two top-level patterns") {
                val q = Query(
                    MultiPattern(
                        List(
                            NodePattern(leafType, Nil, Nil, Some(l)),
                            NodePattern(namedType, Nil, Nil, Some(n))
                        )
                    ),
                    Nil
                )
                val out = QueryPrinter.print(q)
                assertTrue(out == "(Leaf) @l (Named) @n")
            }
        ),
        suite("round-trip")(
            test("bare node round-trips") {
                assertTrue(roundTrips("(Leaf)"))
            },
            test("node with capture round-trips") {
                assertTrue(roundTrips("(Leaf) @l"))
            },
            test("node with field round-trips") {
                assertTrue(roundTrips("(Named name: (Leaf) @n)"))
            },
            test("node with multiple fields round-trips") {
                assertTrue(roundTrips("(Named name: (Leaf) @n body: (Leaf) @b) @outer"))
            },
            test("node with child patterns round-trips") {
                assertTrue(roundTrips("(Named (Leaf) @n (Leaf) @b)"))
            },
            test("node with anchor round-trips") {
                assertTrue(roundTrips("(Named (Leaf) @n . (Leaf) @b)"))
            },
            test("node with negated field round-trips") {
                assertTrue(roundTrips("(Named !name)"))
            },
            test("node with optional quantifier round-trips") {
                assertTrue(roundTrips("(Named (Leaf) @n?)"))
            },
            test("wildcard round-trips") {
                assertTrue(roundTrips("_"))
            },
            test("wildcard with capture round-trips") {
                assertTrue(roundTrips("(_ ) @x"))
            },
            test("alternation round-trips") {
                assertTrue(roundTrips("[(Leaf) @n (Named) @b]"))
            },
            test("alternation with capture round-trips") {
                assertTrue(roundTrips("[(Leaf) _] @x"))
            },
            test("#eq? predicate round-trips") {
                assertTrue(roundTrips("(Leaf) @l (#eq? @l \"hello\")"))
            },
            test("#match? predicate round-trips") {
                assertTrue(roundTrips("(Leaf) @l (#match? @l \"^hi\")"))
            },
            test("multi-pattern round-trips") {
                assertTrue(roundTrips("(Leaf) @l (Named) @n"))
            },
            test("query with comment is canonical without comment") {
                val out = QueryPrinter.print(parse(";; comment\n(Leaf) @l"))
                assertTrue(QueryParser.parse(out).isSuccess)
            }
        )
    )
