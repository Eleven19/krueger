package io.eleven19.krueger.webapp.components

import com.raquo.laminar.api.L.*

import io.eleven19.krueger.ast.AstNode
import io.eleven19.krueger.ast.AstQueryableTree.given
import io.eleven19.krueger.ast.Module as AstModule
import io.eleven19.krueger.compiler.CompilerComponent
import io.eleven19.krueger.cst.CstModule
import io.eleven19.krueger.cst.CstNode
import io.eleven19.krueger.cst.CstQueryableTree.given
import io.eleven19.krueger.trees.QueryableTree

/** Generic collapsible tree view over any [[QueryableTree]]-backed root.
  *
  * Expansion state is per-instance (a `Var[Set[Path]]`) so switching panels keeps the last-seen expansion state for
  * that panel until the outline structurally changes. Errors from upstream compile results surface as a readable
  * banner, never a blank box (REQ-webapp-components-002).
  */
object TreeView:

    private type Path = List[Int]

    def forCst(
        cstResult: Signal[CompilerComponent.CompileResult[Unit, CstModule]]
    ): HtmlElement =
        fromResult[CstModule, CstNode](cstResult, (m: CstModule) => m: CstNode)

    def forAst(
        astResult: Signal[CompilerComponent.CompileResult[Unit, AstModule]]
    ): HtmlElement =
        fromResult[AstModule, AstNode](astResult, (m: AstModule) => m: AstNode)

    private def fromResult[A, T](
        signal: Signal[CompilerComponent.CompileResult[Unit, A]],
        toRoot: A => T
    )(using qt: QueryableTree[T]): HtmlElement =
        val collapsedVar: Var[Set[Path]] = Var(Set.empty)
        sectionTag(
            cls := "krueger-tree-view",
            child <-- signal.map { r =>
                ResultsPanel.viewOutcome(r) match
                    case ViewOutcome.Ok(a)       => renderOutline(TreeOutline.from[T](toRoot(a)), collapsedVar)
                    case ViewOutcome.Error(msgs) => errorBanner(msgs)
            }
        )

    private def errorBanner(messages: List[String]): HtmlElement =
        div(
            cls := "krueger-tree-error",
            span(cls := "krueger-tree-error-title", "Parse errors:"),
            ul(messages.map(m => li(cls := "krueger-tree-error-item", m)))
        )

    private def renderOutline(root: TreeOutline, collapsed: Var[Set[Path]]): HtmlElement =
        ul(
            cls := "krueger-tree-outline",
            renderNode(root, Nil, collapsed)
        )

    private def renderNode(node: TreeOutline, path: Path, collapsed: Var[Set[Path]]): HtmlElement =
        val isLeaf = node.children.isEmpty
        li(
            cls := "krueger-tree-node",
            div(
                cls := "krueger-tree-row",
                if isLeaf then span(cls := "krueger-tree-bullet", "\u2022")
                else
                    button(
                        cls := "krueger-tree-toggle",
                        tpe := "button",
                        child.text <-- collapsed.signal.map(s => if s.contains(path) then "\u25b8" else "\u25be"),
                        onClick --> (_ => collapsed.update(s => if s.contains(path) then s - path else s + path))
                    )
                ,
                span(cls := "krueger-tree-type", node.nodeType),
                node.text.map(t => span(cls := "krueger-tree-text", s" \u201c$t\u201d"))
            ),
            if isLeaf then emptyNode
            else
                ul(
                    cls := "krueger-tree-children",
                    hidden <-- collapsed.signal.map(_.contains(path)),
                    node.children.zipWithIndex.map { (child, idx) =>
                        renderNode(child, path :+ idx, collapsed)
                    }
                )
        )
