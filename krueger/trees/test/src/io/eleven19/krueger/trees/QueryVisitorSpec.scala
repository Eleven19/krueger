package io.eleven19.krueger.trees

import zio.test.*

import io.eleven19.krueger.trees.query.*

object QueryVisitorSpec extends ZIOSpecDefault:

    private val namedType: NodeTypeName = NodeTypeName.make("Named").toOption.get
    private val leafType: NodeTypeName  = NodeTypeName.make("Leaf").toOption.get
    private val n: CaptureName          = CaptureName.make("n").toOption.get
    private val b: CaptureName          = CaptureName.make("b").toOption.get
    private val nameField: FieldName    = FieldName.make("name").toOption.get

    private val sampleQuery: Query =
        Query(
            NodePattern(
                namedType,
                List(FieldPattern(nameField, NodePattern(leafType, Nil, Nil, Some(n)))),
                List(WildcardPattern(Some(b))),
                None,
                childQuantifiers = Map(0 -> QuantifierKind.Optional)
            ),
            List(EqPredicate(CaptureRef(n), StringArg("main")))
        )

    private def tag(node: QueryNode): String = node match
        case QueryNode.Root(_)             => "query"
        case QueryNode.PatternNode(_)      => "pattern"
        case QueryNode.FieldPatternNode(_) => "field"
        case QueryNode.PredicateNode(_)    => "predicate"
        case QueryNode.PredicateArgNode(_) => "arg"

    def spec = suite("QueryVisitor")(
        test("children of root includes root pattern then predicates in order") {
            val kinds = QueryVisitor.children(QueryNode.Root(sampleQuery)).map(tag)
            assertTrue(kinds == List("pattern", "predicate"))
        },
        test("foldLeft is deterministic pre-order over query nodes") {
            val seen = QueryVisitor.foldLeft(sampleQuery, Vector.empty[String]) { (acc, node) =>
                acc :+ tag(node)
            }
            assertTrue(seen == Vector("query", "pattern", "field", "pattern", "pattern", "predicate", "arg", "arg"))
        },
        test("collect can extract captures from pattern and predicate args") {
            val captures = QueryVisitor.collect(sampleQuery) {
                case QueryNode.PatternNode(NodePattern(_, _, _, Some(capture), _, _, _)) => CaptureName.unwrap(capture)
                case QueryNode.PatternNode(WildcardPattern(Some(capture)))                => CaptureName.unwrap(capture)
                case QueryNode.PredicateArgNode(CaptureRef(name))                         => CaptureName.unwrap(name)
            }
            assertTrue(captures == List("n", "b", "n"))
        },
        test("traverse executes effects in pre-order with context and logs") {
            val result = QueryLogic.run[Int, String, String, Int](initialContext = 0) {
                QueryVisitor.traverse(sampleQuery) { node =>
                    QueryLogic.log[Int, String, String](tag(node))
                    QueryLogic.updateContext[Int, String, String](_ + 1)
                }
                QueryLogic.readContext[Int, String, String]
            }
            assertTrue(
                result.value == Right(8),
                result.context == 8,
                result.logs == Vector("query", "pattern", "field", "pattern", "pattern", "predicate", "arg", "arg")
            )
        }
    )
