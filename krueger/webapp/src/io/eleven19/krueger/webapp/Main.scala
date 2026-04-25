package io.eleven19.krueger.webapp

import com.raquo.laminar.api.L.*
import org.scalajs.dom

import scala.scalajs.js.annotation.JSExportTopLevel

import io.eleven19.krueger.webapp.components.ActivityBar
import io.eleven19.krueger.webapp.components.MonacoEditor
import io.eleven19.krueger.webapp.components.ResultsPanel
import io.eleven19.krueger.webapp.monaco.ElmLanguage
import io.eleven19.krueger.webapp.state.AppState

/** Browser entrypoint for the Try Krueger Laminar playground.
  *
  * Exposed as an ES-module top-level export so the Astro `/try/` page can import and call `mount` against a mount-point
  * selector after the page loads. Vite resolves `monaco-editor`; we only touch it once the page is live, via
  * [[ElmLanguage.register]] and the [[MonacoEditor]] components below.
  */
object Main:

    @JSExportTopLevel("mount")
    def mount(selector: String): Unit =
        val container = dom.document.querySelector(selector)
        if container != null then
            ElmLanguage.register()
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
                    div(
                        cls := "krueger-editor-field",
                        span(cls := "krueger-editor-label", "Elm source"),
                        MonacoEditor(state.sourceVar, ElmLanguage.id)
                    ),
                    div(
                        cls := "krueger-editor-field",
                        span(cls := "krueger-editor-label", "Query"),
                        MonacoEditor(state.queryVar, "plaintext")
                    )
                ),
                ResultsPanel(state)
            )
        )
