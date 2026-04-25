package io.eleven19.krueger.trees.query

import kyo.*
import parsley.{Failure, Success}
import zio.test.*

object KyoQueryVisitorSpec extends ZIOSpecDefault:
    private val sampleSource: String =
        "(NodeA name: (NodeB) @child) @parent"

    private def parsedQuery: Query =
        QueryParser.parse(sampleSource) match
            case Success(q)   => q
            case Failure(msg) => sys.error(s"baseline parse failure: $msg")

    private def tag(node: QueryNode): String = node match
        case QueryNode.Root(_)             => "query"
        case QueryNode.PatternNode(_)      => "pattern"
        case QueryNode.FieldPatternNode(_) => "field"
        case QueryNode.PredicateNode(_)    => "predicate"
        case QueryNode.PredicateArgNode(_) => "arg"

    def spec = suite("KyoQueryVisitor")(
        test("visit invokes callback for every query node in pre-order"):
            val out = KyoQueryVisitor.fold(parsedQuery, 0) { (acc, _) => (acc + 1): Int < Any }.eval
            assertTrue(out > 0)
        ,
        test("visit order matches the pure QueryVisitor traversal order"):
            val pureOrder = QueryVisitor.foldLeft(parsedQuery, Vector.empty[String]) { (acc, n) =>
                acc :+ tag(n)
            }
            val kyoOrder = KyoQueryVisitor.fold(parsedQuery, Vector.empty[String]) { (acc, n) =>
                (acc :+ tag(n)): Vector[String] < Any
            }.eval
            assertTrue(kyoOrder == pureOrder)
        ,
        test("visit on the root pattern alone matches pure traversal of that subtree"):
            val rootPattern = parsedQuery.root
            val pureOrder = QueryVisitor.foldLeft(Query(rootPattern, Nil), Vector.empty[String]) { (acc, n) =>
                acc :+ tag(n)
            }.drop(1) // drop the synthetic root since pattern-only visit starts from PatternNode
            val kyoOrder = KyoQueryVisitor.fold(rootPattern, Vector.empty[String]) { (acc, n) =>
                (acc :+ tag(n)): Vector[String] < Any
            }.eval
            assertTrue(kyoOrder == pureOrder)
        ,
        test("Abort.fail in callback short-circuits visitation"):
            val out = Abort.run[String] {
                KyoQueryVisitor.visit(parsedQuery)(_ => Abort.fail("stop"))
            }.eval
            assertTrue(out.toString.contains("stop"))
    )
