package io.eleven19.krueger.trees

import zio.test.*

import io.eleven19.krueger.trees.ToyTree.*

object QueryableTreeSpec extends ZIOSpecDefault:

    private val qt: QueryableTree[ToyTree] = summon[QueryableTree[ToyTree]]

    private val leaf: ToyTree              = Leaf("hello")
    private val anotherLeaf: ToyTree       = Leaf("world")
    private val branch: ToyTree            = Branch(Seq(leaf, anotherLeaf))
    private val named: ToyTree             = Named(name = leaf, body = anotherLeaf)
    private val allVariants: List[ToyTree] = List(leaf, anotherLeaf, branch, named)

    def spec = suite("QueryableTree[ToyTree]")(
        suite("nodeType")(
            test("is non-empty for every variant") {
                assertTrue(allVariants.forall(t => qt.nodeType(t).nonEmpty))
            },
            test("uses the simple class name") {
                assertTrue(
                    qt.nodeType(leaf) == "Leaf",
                    qt.nodeType(branch) == "Branch",
                    qt.nodeType(named) == "Named"
                )
            }
        ),
        suite("children")(
            test("Leaf has no children") {
                assertTrue(qt.children(leaf).isEmpty)
            },
            test("Branch enumerates its items in order") {
                assertTrue(qt.children(branch) == Seq(leaf, anotherLeaf))
            },
            test("Named exposes name then body as children") {
                assertTrue(qt.children(named) == Seq(leaf, anotherLeaf))
            },
            test("children is stable under repeated invocation") {
                val first  = qt.children(branch)
                val second = qt.children(branch)
                assertTrue(first == second)
            }
        ),
        suite("fields")(
            test("Leaf and Branch expose no fields") {
                assertTrue(
                    qt.fields(leaf).isEmpty,
                    qt.fields(branch).isEmpty
                )
            },
            test("Named exposes name and body keys") {
                val fs = qt.fields(named)
                assertTrue(
                    fs.keySet == Set("name", "body"),
                    fs("name") == Seq(leaf),
                    fs("body") == Seq(anotherLeaf)
                )
            },
            test("all field values appear among children") {
                val fieldValues = qt.fields(named).values.flatten.toSet
                val kids        = qt.children(named).toSet
                assertTrue(fieldValues.subsetOf(kids))
            }
        ),
        suite("text")(
            test("Leaf returns Some(value)") {
                assertTrue(qt.text(leaf).contains("hello"))
            },
            test("compound nodes return None") {
                assertTrue(qt.text(branch).isEmpty, qt.text(named).isEmpty)
            }
        )
    )
