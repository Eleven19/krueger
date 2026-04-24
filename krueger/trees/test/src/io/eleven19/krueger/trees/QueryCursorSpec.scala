package io.eleven19.krueger.trees

import zio.test.*

import io.eleven19.krueger.trees.query.*

object QueryCursorSpec extends ZIOSpecDefault:

    private val rootType: NodeTypeName = NodeTypeName.make("Root").toOption.get
    private val leafType: NodeTypeName = NodeTypeName.make("Leaf").toOption.get

    private val query = Query(
        NodePattern(
            rootType,
            Nil,
            List(
                NodePattern(leafType, Nil, Nil, CaptureName.make("a").toOption),
                NodePattern(leafType, Nil, Nil, CaptureName.make("b").toOption)
            ),
            None
        ),
        List(EqPredicate(StringArg("x"), StringArg("x")))
    )

    private def tag(cursor: QueryCursor): String = cursor.node match
        case QueryNode.Root(_)                                => "root"
        case QueryNode.PatternNode(NodePattern(_, _, _, _, _, _, _)) => "node-pattern"
        case QueryNode.PatternNode(WildcardPattern(_))        => "wildcard"
        case QueryNode.PatternNode(MultiPattern(_))           => "multi"
        case QueryNode.PatternNode(AlternationPattern(_, _))  => "alt"
        case QueryNode.FieldPatternNode(_)                    => "field"
        case QueryNode.PredicateNode(_)                       => "predicate"
        case QueryNode.PredicateArgNode(_)                    => "arg"

    def spec = suite("QueryCursor")(
        test("firstChild and nextSibling navigate direct descendants in order") {
            val cursor = QueryCursor(query)
            val first = cursor.firstChild.getOrElse(throw new AssertionError("missing first child"))
            val second = first.nextSibling.getOrElse(throw new AssertionError("missing next sibling"))
            assertTrue(tag(first) == "node-pattern", tag(second) == "predicate")
        },
        test("parent returns to the parent cursor") {
            val cursor = QueryCursor(query)
            val child = cursor.firstChild.getOrElse(throw new AssertionError("missing child"))
            val parent = child.parent.getOrElse(throw new AssertionError("missing parent"))
            assertTrue(parent.node == QueryNode.Root(query), parent.isRoot)
        },
        test("depth increments as traversal goes down") {
            val cursor = QueryCursor(query)
            val depth2 = for
                pattern <- cursor.firstChild
                childA <- pattern.firstChild
            yield childA.depth
            assertTrue(depth2.contains(2))
        },
        test("preOrder is deterministic") {
            val order = QueryCursor(query).preOrder.map(tag).toList
            assertTrue(order == List("root", "node-pattern", "node-pattern", "node-pattern", "predicate", "arg", "arg"))
        }
    )
