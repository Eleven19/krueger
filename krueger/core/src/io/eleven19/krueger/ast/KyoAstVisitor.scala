package io.eleven19.krueger.ast

import io.eleven19.krueger.ast.AstQueryableTree.given
import io.eleven19.krueger.trees.KyoQueryableTree
import kyo.*

object KyoAstVisitor:
    def visit[S](root: AstNode)(f: AstNode => Unit < S): Unit < S =
        KyoQueryableTree.traverseKyo(root)(f)

    def fold[A, S](root: AstNode, zero: A)(f: (A, AstNode) => A < S): A < S =
        KyoQueryableTree.foldKyo(root, zero)(f)
