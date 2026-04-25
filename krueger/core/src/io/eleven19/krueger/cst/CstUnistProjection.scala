package io.eleven19.krueger.cst

import io.eleven19.krueger.cst.CstQueryableTree.given
import io.eleven19.krueger.trees.unist.UnistProjection
import io.eleven19.krueger.trees.unist.UnistSpan

object CstUnistProjection:
    given projection: UnistProjection[CstNode] with
        def span(t: CstNode): Option[UnistSpan] =
            Some(UnistSpan.fromOffsetLength(t.span.offset, t.span.length))

    export CstQueryableTree.given
