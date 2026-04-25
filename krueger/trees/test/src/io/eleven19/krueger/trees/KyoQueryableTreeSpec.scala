package io.eleven19.krueger.trees

import kyo.*
import zio.test.*

object KyoQueryableTreeSpec extends ZIOSpecDefault:
    final case class TestNode(id: Int, kids: List[TestNode]) derives CanEqual

    given QueryableTree[TestNode] with
        def nodeType(t: TestNode): NodeTypeName                = NodeTypeName.unsafeMake(s"node-${t.id}")
        def children(t: TestNode): Seq[TestNode]            = t.kids
        def text(t: TestNode): Option[String]               = None
        def fields(t: TestNode): Map[FieldName, Seq[TestNode]] = Map.empty

    private val tree: TestNode =
        TestNode(1, List(TestNode(2, Nil), TestNode(3, List(TestNode(4, Nil)))))

    def spec = suite("KyoQueryableTree")(
        test("traverseKyo visits every node in pre-order"):
            val out = KyoQueryableTree.foldKyo(tree, Vector.empty[Int]) { (acc, n) =>
                (acc :+ n.id): Vector[Int] < Any
            }.eval
            assertTrue(out == Vector(1, 2, 3, 4))
        ,
        test("foldKyo accumulates left-to-right pre-order"):
            val out = KyoQueryableTree.foldKyo(tree, 0) { (acc, n) =>
                (acc + n.id): Int < Any
            }.eval
            assertTrue(out == 10)
        ,
        test("traverseKyo on a single-node tree visits exactly once"):
            val out = KyoQueryableTree.foldKyo(TestNode(99, Nil), 0) { (acc, _) =>
                (acc + 1): Int < Any
            }.eval
            assertTrue(out == 1)
        ,
        test("Abort.fail in callback short-circuits traversal"):
            val out = Abort.run[String] {
                KyoQueryableTree.foldKyo(tree, 0) { (acc, n) =>
                    if n.id == 3 then Abort.fail("stop at 3")
                    else (acc + n.id): Int < Any
                }
            }.eval
            assertTrue(out.toString.contains("stop at 3"))
    )
