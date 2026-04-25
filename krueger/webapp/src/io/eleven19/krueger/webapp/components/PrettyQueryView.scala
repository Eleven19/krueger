package io.eleven19.krueger.webapp.components

import com.raquo.laminar.api.L.*

import io.eleven19.krueger.compiler.Krueger
import io.eleven19.krueger.webapp.state.AppState

/** Renders the canonical (pretty-printed) form of the current query, or a readable error banner if the query doesn't
  * parse.
  */
object PrettyQueryView:

    def apply(state: AppState): HtmlElement =
        val compiler = Krueger.compiler[Unit]
        sectionTag(
            cls := "krueger-pretty-query",
            child <-- state.queryResult.map { r =>
                ResultsPanel.viewOutcome(r) match
                    case ViewOutcome.Ok(q) => pre(cls := "krueger-pretty-body", compiler.prettyQuery(q))
                    case ViewOutcome.Error(msgs) =>
                        div(
                            cls := "krueger-pretty-error",
                            span(cls := "krueger-pretty-error-title", "Query parse errors:"),
                            ul(msgs.map(m => li(m)))
                        )
            }
        )
