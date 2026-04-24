package io.eleven19.krueger.webapp

import com.raquo.laminar.api.L.*
import org.scalajs.dom

import scala.scalajs.js.annotation.JSExportTopLevel

import io.eleven19.krueger.webapp.components.ActivityBar
import io.eleven19.krueger.webapp.components.ResultsPanel
import io.eleven19.krueger.webapp.state.AppState

/** Browser entrypoint for the Try Krueger Laminar playground.
  *
  * Exposed as an ES-module top-level export so the Astro `/try/` page can import and call `mount` against a
  * mount-point selector after the page loads. The Monaco editor bootstrap lives in the Astro page itself (Vite has to
  * be the one resolving `monaco-editor`) and writes into [[AppState.sourceVar]] / [[AppState.queryVar]] via the
  * globals this module exposes for now — the Monaco-in-Laminar integration lands with its own issue.
  */
object Main:

    @JSExportTopLevel("mount")
    def mount(selector: String): Unit =
        val container = dom.document.querySelector(selector)
        if container != null then
            val state = new AppState()
            val _     = render(container, app(state))

    private def app(state: AppState): HtmlElement =
        div(
            cls := "krueger-app-shell",
            headerTag(cls := "krueger-app-header", s"${AppInfo.name} ${AppInfo.version}"),
            div(
                cls := "krueger-app-body",
                ActivityBar(state.selectedPanel),
                div(
                    cls := "krueger-editor-group",
                    label(
                        cls := "krueger-editor-field",
                        span(cls := "krueger-editor-label", "Elm source"),
                        textArea(
                            cls := "krueger-editor-area",
                            controlled(
                                value <-- state.sourceVar.signal,
                                onInput.mapToValue --> state.sourceVar
                            )
                        )
                    ),
                    label(
                        cls := "krueger-editor-field",
                        span(cls := "krueger-editor-label", "Query"),
                        textArea(
                            cls := "krueger-editor-area",
                            controlled(
                                value <-- state.queryVar.signal,
                                onInput.mapToValue --> state.queryVar
                            )
                        )
                    )
                ),
                ResultsPanel(state)
            )
        )
