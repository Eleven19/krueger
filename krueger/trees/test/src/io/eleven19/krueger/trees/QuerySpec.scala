package io.eleven19.krueger.trees

import zio.test.*

import io.eleven19.krueger.trees.query.*

object QuerySpec extends ZIOSpecDefault:

    private val leafType: NodeTypeName  = NodeTypeName.make("Leaf").toOption.get
    private val namedType: NodeTypeName = NodeTypeName.make("Named").toOption.get

    def spec = suite("Query AST")(
        suite("Pattern")(
            test("NodePattern exposes nodeType, fields, capture") {
                val p = NodePattern(leafType, Nil, Some("l"))
                assertTrue(p.nodeType == leafType, p.fieldPatterns == Nil, p.capture.contains("l"))
            },
            test("WildcardPattern captures optional name") {
                val p: Pattern = WildcardPattern(Some("x"))
                assertTrue(p.capture.contains("x"))
            },
            test("FieldPattern pairs name and sub-pattern") {
                val inner = NodePattern(leafType, Nil, None)
                val fp    = FieldPattern("name", inner)
                assertTrue(fp.name == "name", fp.pattern == inner)
            },
            test("patterns compare structurally") {
                val a = NodePattern(namedType, List(FieldPattern("name", WildcardPattern(None))), None)
                val b = NodePattern(namedType, List(FieldPattern("name", WildcardPattern(None))), None)
                assertTrue(a == b)
            }
        ),
        suite("Predicate")(
            test("EqPredicate compares two capture refs") {
                val p: Predicate = EqPredicate(CaptureRef("a"), CaptureRef("b"))
                assertTrue(p == EqPredicate(CaptureRef("a"), CaptureRef("b")))
            },
            test("MatchPredicate pairs capture ref with regex") {
                val p = MatchPredicate(CaptureRef("x"), "^foo")
                assertTrue(p.arg == CaptureRef("x"), p.regex == "^foo")
            },
            test("PredicateArg has CaptureRef and StringArg forms") {
                val c: PredicateArg = CaptureRef("n")
                val s: PredicateArg = StringArg("literal")
                assertTrue(c != s)
            }
        ),
        suite("Query")(
            test("pairs a root pattern with predicates") {
                val q = Query(NodePattern(leafType, Nil, Some("l")), List(MatchPredicate(CaptureRef("l"), "^hi")))
                assertTrue(
                    q.root == NodePattern(leafType, Nil, Some("l")),
                    q.predicates.size == 1
                )
            },
            test("captureNames collects every bound name in patterns") {
                val q = Query(
                    NodePattern(
                        namedType,
                        List(
                            FieldPattern("name", NodePattern(leafType, Nil, Some("n"))),
                            FieldPattern("body", WildcardPattern(Some("b")))
                        ),
                        Some("outer")
                    ),
                    Nil
                )
                assertTrue(q.captureNames == Set("outer", "n", "b"))
            },
            test("captureNames includes names only referenced by predicates when they also exist on patterns") {
                val q = Query(
                    NodePattern(leafType, Nil, Some("l")),
                    List(EqPredicate(CaptureRef("l"), StringArg("x")))
                )
                assertTrue(q.captureNames == Set("l"))
            }
        ),
        suite("Match")(
            test("carries root and capture map") {
                val m = Match[String]("root", Map("a" -> "alpha", "b" -> "beta"))
                assertTrue(m.root == "root", m.captures.size == 2, m.captures("a") == "alpha")
            },
            test("two matches with identical data compare equal") {
                val a = Match("r", Map("k" -> "v"))
                val b = Match("r", Map("k" -> "v"))
                assertTrue(a == b)
            }
        )
    )
