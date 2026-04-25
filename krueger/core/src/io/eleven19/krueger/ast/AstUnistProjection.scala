package io.eleven19.krueger.ast

import io.eleven19.krueger.ast.AstQueryableTree.given
import io.eleven19.krueger.trees.unist.UnistProjection
import io.eleven19.krueger.trees.unist.UnistSpan

object AstUnistProjection:
    given projection: UnistProjection[AstNode] with
        def span(t: AstNode): Option[UnistSpan] =
            Some(UnistSpan.fromOffsetLength(t.span.offset, t.span.length))

    export AstQueryableTree.given
