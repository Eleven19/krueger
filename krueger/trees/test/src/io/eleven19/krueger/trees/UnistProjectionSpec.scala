package io.eleven19.krueger.trees

import zio.test.*

import io.eleven19.krueger.trees.unist.*

object UnistProjectionSpec extends ZIOSpecDefault:

    private enum SExpr derives CanEqual:
        case Atom(value: String)
        case ListExpr(head: SExpr, arguments: IndexedSeq[SExpr])
        case Pair(name: SExpr, value: SExpr)

    private object SExpr:
        val HeadField: FieldName      = FieldName.unsafeMake("head")
        val ArgumentsField: FieldName = FieldName.unsafeMake("arguments")
        val NameField: FieldName      = FieldName.unsafeMake("name")
        val ValueField: FieldName     = FieldName.unsafeMake("value")

        given QueryableTree[SExpr] with
            def nodeType(t: SExpr): NodeTypeName = t match
                case _: Atom     => NodeTypeName.unsafeMake("Atom")
                case _: ListExpr => NodeTypeName.unsafeMake("List")
                case _: Pair     => NodeTypeName.unsafeMake("Pair")

            def children(t: SExpr): Seq[SExpr] = t match
                case _: Atom                   => Seq.empty
                case ListExpr(head, arguments) => head +: arguments
                case Pair(name, value)         => Seq(name, value)

            def fields(t: SExpr): Map[FieldName, Seq[SExpr]] = t match
                case _: Atom                   => Map.empty
                case ListExpr(head, arguments) => Map(HeadField -> Seq(head), ArgumentsField -> arguments)
                case Pair(name, value)         => Map(NameField -> Seq(name), ValueField -> Seq(value))

            def text(t: SExpr): Option[String] = t match
                case Atom(value) => Some(value)
                case _           => None

    import SExpr.*

    private val defAtom = Atom("def")
    private val nameKey = Atom("name")
    private val main    = Atom("main")
    private val namePair = Pair(nameKey, main)
    private val body = ListExpr(
        Atom("+"),
        IndexedSeq(Atom("1"), Atom("2"))
    )
    private val root = ListExpr(defAtom, IndexedSeq(namePair, body))

    private val zeroSpan: UnistSpan = UnistSpan(0, 0)
    private val rootSpan: UnistSpan = UnistSpan(0, 22)

    private given UnistProjection[SExpr] with
        def span(t: SExpr): Option[UnistSpan] = t match
            case `root`    => Some(rootSpan)
            case `defAtom` => Some(zeroSpan)
            case _         => None

    def spec = suite("UnistProjection")(
        test("projects node type, text, ordered children, and child count") {
            val node = UnistProjection.project(root)
            assertTrue(
                node.`type` == "List",
                node.children.map(_.`type`) == IndexedSeq("Atom", "Pair", "List"),
                node.children.head.value.contains("def"),
                node.children(1).children.map(_.value) == IndexedSeq(Some("name"), Some("main")),
                node.data.childCount == 3
            )
        },
        test("maps named fields to direct child indexes deterministically") {
            val node = UnistProjection.project(root)
            val pairNode = node.children(1)
            assertTrue(
                node.data.fields == Map("head" -> IndexedSeq(0), "arguments" -> IndexedSeq(1, 2)),
                pairNode.data.fields == Map("name" -> IndexedSeq(0), "value" -> IndexedSeq(1))
            )
        },
        test("maps duplicate equal field children to sequential direct child indexes") {
            val duplicateRoot = ListExpr(Atom("call"), IndexedSeq(Atom("x"), Atom("x")))
            val node = UnistProjection.project(duplicateRoot)
            assertTrue(
                node.children.map(_.value) == IndexedSeq(Some("call"), Some("x"), Some("x")),
                node.data.fields == Map("head" -> IndexedSeq(0), "arguments" -> IndexedSeq(1, 2))
            )
        },
        test("omits position when source text is absent") {
            val node = UnistProjection.project(root)
            assertTrue(node.position.isEmpty)
        },
        test("converts zero-length and non-empty spans to one-based unist points when source text is present") {
            val node = UnistProjection.project(root, source = Some("(def (name main) (+ 1 2))"))
            val leafNode = node.children.head
            assertTrue(
                node.position.exists(_.start == UnistPoint(line = 1, column = 1, offset = Some(0))),
                node.position.exists(_.end == UnistPoint(line = 1, column = 23, offset = Some(22))),
                leafNode.position.exists(_.start == UnistPoint(line = 1, column = 1, offset = Some(0))),
                leafNode.position.exists(_.end == UnistPoint(line = 1, column = 1, offset = Some(0)))
            )
        }
    )
