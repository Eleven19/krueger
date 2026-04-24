package io.eleven19.krueger.compiler

import io.eleven19.krueger.trees.CaptureName
import io.eleven19.krueger.trees.NodeTypeName
import io.eleven19.krueger.trees.QueryableTree
import io.eleven19.krueger.trees.query.Match

/** Type-erased view over a query match, usable by any consumer regardless of whether the tree under test is a CST, an
  * AST, or another QueryableTree. Intentionally free of platform-specific types so it can cross the JS / WASM FFI
  * boundary as a plain data shape.
  */
final case class MatchView(
    rootNodeType: String,
    rootText: Option[String],
    captures: Map[String, CapturedNode]
) derives CanEqual

object MatchView:

    def from[T](m: Match[T])(using qt: QueryableTree[T]): MatchView =
        def view(node: T): CapturedNode =
            CapturedNode(NodeTypeName.unwrap(qt.nodeType(node)), qt.text(node), qt.children(node).size)
        MatchView(
            rootNodeType = NodeTypeName.unwrap(qt.nodeType(m.root)),
            rootText = qt.text(m.root),
            captures = m.captures.map((name, node) => CaptureName.unwrap(name) -> view(node))
        )

/** A captured node reduced to its nodeType, optional text, and direct child count. */
final case class CapturedNode(nodeType: String, text: Option[String], childCount: Int) derives CanEqual
