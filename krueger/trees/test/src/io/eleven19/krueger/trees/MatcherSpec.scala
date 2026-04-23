package io.eleven19.krueger.trees

import zio.test.*

import io.eleven19.krueger.trees.ToyTree.*
import io.eleven19.krueger.trees.query.*

object MatcherSpec extends ZIOSpecDefault:

    private def q(src: String): Query = QueryParser.parse(src) match
        case parsley.Success(x) => x
        case parsley.Failure(m) => throw new AssertionError(s"bad fixture: $m")

    // A small forest used across tests.
    private val leafHi: ToyTree  = Leaf("hi")
    private val leafBye: ToyTree = Leaf("bye")
    private val leafYo: ToyTree  = Leaf("yo")
    private val named: ToyTree   = Named(name = leafHi, body = leafBye)
    private val branch: ToyTree  = Branch(Seq(leafHi, named, leafYo))

    def spec = suite("Matcher")(
        suite("structural matching")(
            test("a bare node pattern matches every node of that type") {
                val ms = Matcher.matches(q("(Leaf)"), branch).toList
                assertTrue(ms.size == 4) // leafHi, leafHi inside named, leafBye inside named, leafYo
            },
            test("a wildcard matches every node") {
                val ms = Matcher.matches(q("_"), named).toList
                assertTrue(ms.size == 3) // named, leafHi, leafBye
            },
            test("a wildcard can be captured") {
                val ms = Matcher.matches(q("_ @x"), leafHi).toList
                assertTrue(ms.size == 1, ms.head.captures.get("x").contains(leafHi))
            },
            test("no match yields an empty lazy list") {
                val ms = Matcher.matches(q("(Branch)"), leafHi)
                assertTrue(ms.isEmpty)
            }
        ),
        suite("fields")(
            test("field pattern constrains a named child") {
                val ms = Matcher.matches(q("(Named name: (Leaf) @n)"), branch).toList
                assertTrue(ms.size == 1, ms.head.captures.get("n").contains(leafHi))
            },
            test("field pattern with multiple fields binds each capture") {
                val ms = Matcher.matches(q("(Named name: (Leaf) @n body: (Leaf) @b)"), branch).toList
                assertTrue(
                    ms.size == 1,
                    ms.head.captures.get("n").contains(leafHi),
                    ms.head.captures.get("b").contains(leafBye)
                )
            },
            test("field pattern fails when sub-pattern does not match") {
                val ms = Matcher.matches(q("(Named name: (Branch))"), named).toList
                assertTrue(ms.isEmpty)
            }
        ),
        suite("predicates")(
            test("#eq? on text succeeds when both captures have identical text") {
                val root: ToyTree = Branch(Seq(Leaf("same"), Leaf("same")))
                val pairQuery     = q("(Leaf) @l (#eq? @l \"same\")")
                val pairMs        = Matcher.matches(pairQuery, root).toList
                assertTrue(pairMs.size == 2)
            },
            test("#eq? fails when the capture text does not match the literal") {
                val ms = Matcher.matches(q("(Leaf) @l (#eq? @l \"other\")"), leafHi).toList
                assertTrue(ms.isEmpty)
            },
            test("#match? passes captures whose text matches the regex") {
                val ms = Matcher.matches(q("(Leaf) @l (#match? @l \"^h\")"), branch).toList
                // Only leaves whose text starts with 'h' — that's leafHi (twice: top-level + inside named)
                val leafValues = ms.map(_.captures("l")).collect { case Leaf(v) => v }
                assertTrue(ms.size == 2, leafValues.forall(_.startsWith("h")))
            },
            test("#match? filters out captures whose text does not match") {
                val ms = Matcher.matches(q("(Leaf) @l (#match? @l \"^z\")"), branch).toList
                assertTrue(ms.isEmpty)
            }
        ),
        suite("custom predicates")(
            test("a user-registered predicate is evaluated like the built-ins") {
                val customRegistry = PredicateRegistry.default.withPredicate(
                    "#eq?",
                    new PredicateImpl:
                        def evaluate[T](args: List[PredicateArg], captures: Map[String, T])(using
                            qt: QueryableTree[T]
                        ): Boolean = false // flip #eq? to always-false for this registry
                )
                val ms = Matcher
                    .matches(
                        q("(Leaf) @l (#eq? @l \"hi\")"),
                        leafHi,
                        customRegistry
                    )
                    .toList
                assertTrue(ms.isEmpty) // default would match; the override forces false
            }
        )
    )
