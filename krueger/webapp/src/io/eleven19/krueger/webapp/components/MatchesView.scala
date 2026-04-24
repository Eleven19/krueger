package io.eleven19.krueger.webapp.components

import com.raquo.laminar.api.L.*

import io.eleven19.krueger.compiler.MatchView

/** Renders the match list with capture tags, or a "no matches" placeholder when the list is empty. */
object MatchesView:

    /** Pure classifier — lets tests pin the placeholder behaviour without a DOM. */
    def describe(matches: List[MatchView]): MatchesOutcome =
        if matches.isEmpty then MatchesOutcome.Empty else MatchesOutcome.Matched(matches)

    def apply(matchesSignal: Signal[List[MatchView]]): HtmlElement =
        sectionTag(
            cls := "krueger-matches-view",
            child <-- matchesSignal.map {
                case Nil => placeholder()
                case ms  => list(ms)
            }
        )

    private def placeholder(): HtmlElement =
        div(cls := "krueger-matches-empty", "No matches.")

    private def list(matches: List[MatchView]): HtmlElement =
        ul(
            cls := "krueger-matches-list",
            matches.map(renderOne)
        )

    private def renderOne(m: MatchView): HtmlElement =
        li(
            cls := "krueger-match-entry",
            span(cls := "krueger-match-root", s"(${m.rootNodeType})"),
            m.rootText.map(t => span(cls := "krueger-match-text", s" — $t")),
            captures(m)
        )

    private def captures(m: MatchView): HtmlElement =
        ul(
            cls := "krueger-match-captures",
            m.captures.toList.sortBy(_._1).map { (name, node) =>
                li(
                    cls := "krueger-match-capture",
                    span(cls := "krueger-capture-name", s"@$name"),
                    span(cls := "krueger-capture-kind", s" ${node.nodeType}"),
                    node.text.map(t => span(cls := "krueger-capture-text", s" = $t"))
                )
            }
        )
