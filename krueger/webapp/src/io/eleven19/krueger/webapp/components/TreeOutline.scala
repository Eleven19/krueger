package io.eleven19.krueger.webapp.components

import io.eleven19.krueger.trees.NodeTypeName
import io.eleven19.krueger.trees.QueryableTree

/** Pure, platform-agnostic tree shape the [[TreeView]] Laminar component renders.
  *
  * Built from any `T` with a [[QueryableTree]] instance — the exact typeclass the Krueger query engine uses — so the
  * on-screen tree can never drift from the tree the matcher walks. Using the typeclass here (rather than a hand-rolled
  * traversal) is what REQ-webapp-components-003 pins down.
  *
  * The shape is intentionally primitive (strings + child `Seq`) so it serializes across the JS/WASM FFI later without
  * rework.
  */
final case class TreeOutline(nodeType: String, text: Option[String], children: Seq[TreeOutline]) derives CanEqual:

    /** Depth-first linear enumeration — handy for tests that want to scan the full tree for any node matching a
      * predicate.
      */
    def flatten: List[TreeOutline] =
        this :: children.toList.flatMap(_.flatten)

object TreeOutline:

    /** Build a [[TreeOutline]] from a [[QueryableTree]] root. Stable and deterministic: ordering is whatever the
      * typeclass' `children` exposes.
      */
    def from[T](root: T)(using qt: QueryableTree[T]): TreeOutline =
        TreeOutline(
            nodeType = NodeTypeName.unwrap(qt.nodeType(root)),
            text = qt.text(root),
            children = qt.children(root).map(from[T])
        )
