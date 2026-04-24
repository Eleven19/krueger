package io.eleven19.krueger.webapp

import com.raquo.laminar.api.L.*
import org.scalajs.dom

import scala.scalajs.js.annotation.JSExportTopLevel

/** Browser entrypoint for the Try Krueger Laminar playground.
  *
  * Exposed as an ES-module top-level export so the Astro /try/ page can import and call `mount` against a mount-point
  * selector after the page loads. The body here is intentionally minimal — AppState, Monaco, and the full component
  * tree land in follow-up issues.
  */
object Main:

    @JSExportTopLevel("mount")
    def mount(selector: String): Unit =
        val container = dom.document.querySelector(selector)
        if container != null then
            val _ = render(container, appElement())

    private def appElement(): Element =
        div(
            cls := "krueger-app-shell",
            h1(s"${AppInfo.name} ${AppInfo.version}"),
            p("Try Krueger playground loaded.")
        )
