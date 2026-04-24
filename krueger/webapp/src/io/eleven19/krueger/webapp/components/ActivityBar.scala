package io.eleven19.krueger.webapp.components

import com.raquo.laminar.api.L.*

import io.eleven19.krueger.webapp.state.Panel

/** VS Code-style icon rail. Each button writes its [[Panel]] to the shared `selectedPanel` Var; the currently-selected
  * panel is highlighted by an `is-active` class so CSS can theme it via Starlight tokens.
  */
object ActivityBar:

    def apply(selectedPanel: Var[Panel]): HtmlElement =
        navTag(
            cls  := "krueger-activity-bar",
            role := "tablist",
            Panel.all.map(panelButton(_, selectedPanel))
        )

    private def panelButton(panel: Panel, selectedPanel: Var[Panel]): HtmlElement =
        button(
            tpe  := "button",
            role := "tab",
            cls  := "krueger-activity-button",
            cls("is-active") <-- selectedPanel.signal.map(_ == panel),
            title      := Panel.label(panel),
            aria.label := Panel.label(panel),
            span(cls := "krueger-activity-icon", Panel.icon(panel)),
            onClick --> (_ => selectedPanel.set(panel))
        )
