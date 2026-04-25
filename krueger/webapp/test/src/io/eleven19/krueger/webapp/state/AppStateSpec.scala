package io.eleven19.krueger.webapp.state

import zio.test.*

import com.raquo.airstream.core.Signal
import com.raquo.airstream.core.Observer
import com.raquo.airstream.ownership.ManualOwner
import com.raquo.airstream.ownership.Owner

import io.eleven19.krueger.compiler.CompileError

object AppStateSpec extends ZIOSpecDefault:

    private val simpleSource    = "module M exposing (..)\n\nx = 1\n"
    private val malformedSource = "module M exposing (..)\n\nx ="
    private val simpleQuery     = "(CstValueDeclaration) @v"
    private val zeroMatchQuery  = "(nonexistent_node_type) @x"
    private val malformedQuery  = "(unbalanced"

    private def withOwner[A](f: Owner ?=> A): A =
        val owner = new ManualOwner()
        try f(using owner)
        finally owner.killSubscriptions()

    private def latest[A](signal: Signal[A])(using owner: Owner): A =
        var captured: Option[A] = None
        val sub                 = signal.foreach(a => captured = Some(a))
        sub.kill()
        captured.get

    def spec = suite("AppState")(
        test("happy path: valid source and query produce non-empty matches with success CST/AST") {
            withOwner {
                val s = new AppState()
                s.sourceVar.set(simpleSource)
                s.queryVar.set(simpleQuery)
                val cst     = latest(s.cstResult)
                val ast     = latest(s.astResult)
                val matches = latest(s.matchResult)
                assertTrue(
                    cst.value.isRight,
                    ast.value.isRight,
                    matches.nonEmpty
                )
            }
        },
        test("negative: malformed source surfaces CompileError in cstResult and astResult; matchResult is empty") {
            withOwner {
                val s = new AppState()
                s.sourceVar.set(malformedSource)
                s.queryVar.set(simpleQuery)
                val cst     = latest(s.cstResult)
                val ast     = latest(s.astResult)
                val matches = latest(s.matchResult)
                assertTrue(
                    cst.value.isLeft,
                    ast.value.isLeft,
                    cst.errors.nonEmpty,
                    matches.isEmpty
                )
            }
        },
        test("negative: valid source with malformed query yields empty matches, no crash") {
            withOwner {
                val s = new AppState()
                s.sourceVar.set(simpleSource)
                s.queryVar.set(malformedQuery)
                val cst     = latest(s.cstResult)
                val matches = latest(s.matchResult)
                assertTrue(
                    cst.value.isRight,
                    matches.isEmpty
                )
            }
        },
        test("edge: empty source and empty query do not crash downstream signals") {
            withOwner {
                val s       = new AppState()
                val cst     = latest(s.cstResult)
                val ast     = latest(s.astResult)
                val matches = latest(s.matchResult)
                assertTrue(
                    matches.isEmpty,
                    cst != null,
                    ast != null
                )
            }
        },
        test("edge: query that matches zero nodes yields empty matches, no errors") {
            withOwner {
                val s = new AppState()
                s.sourceVar.set(simpleSource)
                s.queryVar.set(zeroMatchQuery)
                val matches = latest(s.matchResult)
                assertTrue(matches.isEmpty)
            }
        },
        test("regression: changing source then query produces expected final state with no stale signal") {
            withOwner {
                val s = new AppState()
                s.sourceVar.set(malformedSource)
                s.queryVar.set(simpleQuery)
                val firstMatches = latest(s.matchResult)
                s.sourceVar.set(simpleSource)
                val afterSrc = latest(s.matchResult)
                s.queryVar.set(zeroMatchQuery)
                val afterQuery = latest(s.matchResult)
                assertTrue(
                    firstMatches.isEmpty,
                    afterSrc.nonEmpty,
                    afterQuery.isEmpty
                )
            }
        },
        test("determinism: matchResult is stable across repeated reads") {
            withOwner {
                val s = new AppState()
                s.sourceVar.set(simpleSource)
                s.queryVar.set(simpleQuery)
                val m1 = latest(s.matchResult)
                val m2 = latest(s.matchResult)
                assertTrue(m1 == m2)
            }
        },
        test("selectedPanel defaults to Matches so the first paint shows user-primary output") {
            withOwner {
                val s        = new AppState()
                val selected = latest(s.selectedPanel.signal)
                assertTrue(selected == Panel.Matches)
            }
        },
        test("selectedPanel round-trips synchronously when the activity bar sets it") {
            withOwner {
                val s = new AppState()
                s.selectedPanel.set(Panel.Cst)
                val afterCst = latest(s.selectedPanel.signal)
                s.selectedPanel.set(Panel.PrettyQuery)
                val afterPretty = latest(s.selectedPanel.signal)
                assertTrue(
                    afterCst == Panel.Cst,
                    afterPretty == Panel.PrettyQuery
                )
            }
        }
    )
