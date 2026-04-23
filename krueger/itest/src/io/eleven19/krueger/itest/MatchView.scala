package io.eleven19.krueger.itest

import io.eleven19.krueger.trees.QueryableTree
import io.eleven19.krueger.trees.query.Match

/** Type-erased view over a query match, usable by BDD steps regardless of whether the tree under test is a CST or an
  * AST.
  */
final case class MatchView(
    rootNodeType: String,
    rootText: Option[String],
    captures: Map[String, CapturedNode]
)

object MatchView:

    def from[T](m: Match[T])(using qt: QueryableTree[T]): MatchView =
        MatchView(
            rootNodeType = qt.nodeType(m.root),
            rootText = qt.text(m.root),
            captures = m.captures.map((name, node) => name -> CapturedNode(qt.nodeType(node), qt.text(node)))
        )

/** A captured node reduced to its nodeType plus optional text. Enough to write all v1 capture assertions. */
final case class CapturedNode(nodeType: String, text: Option[String])
