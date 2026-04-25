package io.eleven19.krueger.webapp.components

import zio.test.*

import io.eleven19.krueger.compiler.CompilerComponent
import io.eleven19.krueger.compiler.Krueger as CompilerKrueger
import io.eleven19.krueger.compiler.MatchView
import io.eleven19.krueger.cst.CstModule

/** Pure classification contract used by [[ResultsPanel]] and [[MatchesView]] to decide *what* to render for a given
  * compile result. Covers REQ-webapp-components-002 (error → readable message, not blank) and the "no matches"
  * placeholder edge case without needing a DOM to render Laminar elements.
  */
object ResultsPanelSpec extends ZIOSpecDefault:

    private val sample          = "module M exposing (..)\n\nx = 1\n"
    private val malformedSource = "module M exposing (..)\n\nx ="

    private def cstResult(source: String): CompilerComponent.CompileResult[Unit, CstModule] =
        CompilerComponent.runUnit(CompilerKrueger.compiler[Unit].parseCst(source))

    def spec = suite("ResultsPanel / MatchesView classification")(
        test("viewOutcome on a valid source returns Ok with the parsed value") {
            val r = cstResult(sample)
            ResultsPanel.viewOutcome(r) match
                case ViewOutcome.Ok(cst) => assertTrue(cst.asInstanceOf[CstModule] != null)
                case other               => assertTrue(false, s"expected Ok, got $other".nonEmpty)
        },
        test("viewOutcome on a malformed source returns Error with a non-empty message list") {
            val r = cstResult(malformedSource)
            ResultsPanel.viewOutcome(r) match
                case ViewOutcome.Error(messages) =>
                    assertTrue(messages.nonEmpty, messages.forall(_.nonEmpty))
                case other => assertTrue(false, s"expected Error, got $other".nonEmpty)
        },
        test("edge: empty match list classifies as NoMatches placeholder, not a blank view") {
            assertTrue(MatchesView.describe(List.empty) == MatchesOutcome.Empty)
        },
        test("happy: a non-empty list of matches classifies as Matched with the same list in order") {
            val m = MatchView("CstValueDeclaration", Some("x"), Map.empty)
            MatchesView.describe(List(m)) match
                case MatchesOutcome.Matched(list) =>
                    assertTrue(list == List(m))
                case other => assertTrue(false, s"expected Matched, got $other".nonEmpty)
        },
        test("determinism: viewOutcome.Error messages preserve order and content across repeated runs") {
            val a = ResultsPanel.viewOutcome(cstResult(malformedSource))
            val b = ResultsPanel.viewOutcome(cstResult(malformedSource))
            assertTrue(a == b)
        }
    )
