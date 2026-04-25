package io.eleven19.krueger.webapp.components

import com.raquo.laminar.api.L.*

import io.eleven19.krueger.compiler.CompileError
import io.eleven19.krueger.compiler.CompilerComponent
import io.eleven19.krueger.webapp.state.AppState
import io.eleven19.krueger.webapp.state.Panel

/** The right-hand results area. Picks a child view based on [[AppState.selectedPanel]].
  *
  * The [[viewOutcome]] classifier stays public so the choice between "render value" and "render error" is pinned by
  * [[ResultsPanelSpec]] at test time rather than relying on DOM assertions.
  */
object ResultsPanel:

    /** Collapse a compile envelope into a simple sum so consumers (and tests) don't re-derive the error branch by hand.
      *
      * Preference: if the Either value is `Right`, we surface `Ok` regardless of soft logs. If it's `Left`, we
      * aggregate the fail-fast error message plus any accumulated diagnostics.
      */
    def viewOutcome[Ctx, A](result: CompilerComponent.CompileResult[Ctx, A]): ViewOutcome[A] =
        result.value match
            case Right(a)   => ViewOutcome.Ok(a)
            case Left(errs) => ViewOutcome.Error(errs.map(_.message).distinct.toList)

    /** The Laminar element. Keeps component state per-instance: TreeView remembers which nodes are collapsed per panel
      * selection.
      */
    def apply(state: AppState): HtmlElement =
        sectionTag(
            cls := "krueger-results-panel",
            child <-- state.selectedPanel.signal.map {
                case Panel.Matches     => MatchesView(state.matchResult)
                case Panel.Cst         => TreeView.forCst(state.cstResult)
                case Panel.Ast         => TreeView.forAst(state.astResult)
                case Panel.PrettyQuery => PrettyQueryView(state)
            }
        )
