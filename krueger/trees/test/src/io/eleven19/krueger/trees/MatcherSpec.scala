package io.eleven19.krueger.trees

import zio.test.*

import io.eleven19.krueger.trees.ToyTree.*
import io.eleven19.krueger.trees.query.*

object MatcherSpec extends ZIOSpecDefault:

    private def q(src: String): Query = QueryParser.parse(src) match
        case parsley.Success(x) => x
        case parsley.Failure(m) => throw new AssertionError(s"bad fixture: $m")

    private val xCap: CaptureName = CaptureName.make("x").toOption.get
    private val nCap: CaptureName = CaptureName.make("n").toOption.get
    private val bCap: CaptureName = CaptureName.make("b").toOption.get
    private val lCap: CaptureName = CaptureName.make("l").toOption.get

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
                assertTrue(ms.size == 1, ms.head.captures.get(xCap).contains(leafHi))
            },
            test("no match yields an empty lazy list") {
                val ms = Matcher.matches(q("(Branch)"), leafHi)
                assertTrue(ms.isEmpty)
            },
            test("multiple top-level patterns return combined matches") {
                val ms = Matcher.matches(q("(Named) (Leaf)"), branch).toList
                assertTrue(ms.size == 5)
            },
            test("multiple top-level patterns preserve pattern-order grouping") {
                val ms = Matcher.matches(q("(Named) @n (Leaf) @l"), branch).toList
                val firstIsNamed = ms.head.captures.contains(nCap)
                val namedCount   = ms.count(_.captures.contains(nCap))
                val leafCount    = ms.count(_.captures.contains(lCap))
                val splitAt      = namedCount
                val grouped = ms.take(splitAt).forall(_.captures.contains(nCap)) &&
                    ms.drop(splitAt).forall(_.captures.contains(lCap))
                assertTrue(firstIsNamed, namedCount == 1, leafCount == 4, grouped)
            }
        ),
        suite("fields")(
            test("field pattern constrains a named child") {
                val ms = Matcher.matches(q("(Named name: (Leaf) @n)"), branch).toList
                assertTrue(ms.size == 1, ms.head.captures.get(nCap).contains(leafHi))
            },
            test("field pattern with multiple fields binds each capture") {
                val ms = Matcher.matches(q("(Named name: (Leaf) @n body: (Leaf) @b)"), branch).toList
                assertTrue(
                    ms.size == 1,
                    ms.head.captures.get(nCap).contains(leafHi),
                    ms.head.captures.get(bCap).contains(leafBye)
                )
            },
            test("field pattern fails when sub-pattern does not match") {
                val ms = Matcher.matches(q("(Named name: (Branch))"), named).toList
                assertTrue(ms.isEmpty)
            }
        ),
        suite("ordered child matching")(
            test("unfielded child patterns match children in order") {
                val ms = Matcher.matches(q("(Branch (Leaf) @n (Named) @b)"), branch).toList
                assertTrue(
                    ms.size == 1,
                    ms.head.captures.get(nCap).contains(leafHi),
                    ms.head.captures.get(bCap).contains(named)
                )
            },
            test("unfielded child patterns fail when ordered sequence cannot be found") {
                val ms = Matcher.matches(q("(Branch (Named) @b (Named) @n)"), branch).toList
                assertTrue(ms.isEmpty)
            },
            test("field and unfielded child patterns can be mixed") {
                val ms = Matcher.matches(q("(Named name: (Leaf) @n (Leaf) @b)"), named).toList
                assertTrue(
                    ms.size == 1,
                    ms.head.captures.get(nCap).contains(leafHi),
                    ms.head.captures.get(bCap).contains(leafBye)
                )
            },
            test("ordered child matching is deterministic when multiple alignments are possible") {
                val root: ToyTree = Branch(Seq(Leaf("a"), Leaf("b"), Leaf("c")))
                val ms   = Matcher.matches(q("(Branch (Leaf) @n (Leaf) @b)"), root).toList
                val capN = ms.head.captures(nCap)
                val capB = ms.head.captures(bCap)
                assertTrue(ms.size == 1, capN == Leaf("a"), capB == Leaf("b"))
            },
            test("anchored child patterns require immediate sibling adjacency") {
                val ms = Matcher.matches(q("(Branch (Leaf) @n . (Named) @b)"), branch).toList
                assertTrue(
                    ms.size == 1,
                    ms.head.captures.get(nCap).contains(leafHi),
                    ms.head.captures.get(bCap).contains(named)
                )
            },
            test("anchored child patterns fail when only non-adjacent match exists") {
                val ms = Matcher.matches(q("(Branch (Leaf) @n . (Leaf) @b)"), branch).toList
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
                val leafValues = ms.map(_.captures(lCap)).collect { case Leaf(v) => v }
                assertTrue(ms.size == 2, leafValues.forall(_.startsWith("h")))
            },
            test("#match? filters out captures whose text does not match") {
                val ms = Matcher.matches(q("(Leaf) @l (#match? @l \"^z\")"), branch).toList
                assertTrue(ms.isEmpty)
            },
            test("#not-eq? keeps captures whose text differs from the literal") {
                val ms = Matcher.matches(q("(Leaf) @l (#not-eq? @l \"hi\")"), branch).toList
                val values = ms.map(_.captures(lCap)).collect { case Leaf(v) => v }
                assertTrue(ms.size == 2, values.forall(_ != "hi"))
            },
            test("#not-match? keeps captures whose text does not match regex") {
                val ms = Matcher.matches(q("(Leaf) @l (#not-match? @l \"^h\")"), branch).toList
                val values = ms.map(_.captures(lCap)).collect { case Leaf(v) => v }
                assertTrue(ms.size == 2, values.forall(v => !v.startsWith("h")))
            },
            test("#eq? deterministically fails when capture has no text") {
                val ms = Matcher.matches(q("(Named) @n (#eq? @n \"hi\")"), branch).toList
                assertTrue(ms.isEmpty)
            },
            test("#match? deterministically fails when capture has no text") {
                val ms = Matcher.matches(q("(Named) @n (#match? @n \"^h\")"), branch).toList
                assertTrue(ms.isEmpty)
            },
            test("multi-pattern predicate does not produce hidden success for non-captured matches") {
                val ms = Matcher.matches(q("(Named) (Leaf) @l (#eq? @l \"bye\")"), branch).toList
                val onlyLeafCapture = ms.forall(_.captures.keySet == Set(lCap))
                val leafValues = ms.map(_.captures(lCap)).collect { case Leaf(v) => v }
                assertTrue(ms.size == 1, onlyLeafCapture, leafValues == List("bye"))
            }
        ),
        suite("custom predicates")(
            test("a user-registered predicate is evaluated like the built-ins") {
                val customRegistry = PredicateRegistry.default.withPredicate(
                    PredicateName.Eq,
                    new PredicateImpl:
                        def evaluate[T](args: PredicateArgs, captures: Map[CaptureName, T])(using
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
