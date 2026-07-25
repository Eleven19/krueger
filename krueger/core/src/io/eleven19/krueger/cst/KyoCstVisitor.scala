package io.eleven19.krueger.cst

import io.eleven19.krueger.cst.CstQueryableTree.given
import io.eleven19.krueger.trees.KyoQueryableTree
import kyo.*

object KyoCstVisitor:

    def visit[S](root: CstNode)(f: CstNode => Unit < S)(using frame: Frame): Unit < S =
        KyoQueryableTree.traverseKyo(root)(f)

    def fold[A, S](root: CstNode, zero: A)(f: (A, CstNode) => A < S)(using frame: Frame): A < S =
        KyoQueryableTree.foldKyo(root, zero)(f)
