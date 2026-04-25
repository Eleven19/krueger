package io.eleven19.krueger.webapp.state

import zio.test.*

/** Pinning contract for the [[Panel]] enum: four output slots with distinct labels and icons so the activity bar can
  * render all panels without ambiguity.
  */
object PanelSpec extends ZIOSpecDefault:

    def spec = suite("Panel")(
        test("enumerates exactly four panels in Matches → CST → AST → Canonical order") {
            assertTrue(
                Panel.values.toList == List(Panel.Matches, Panel.Cst, Panel.Ast, Panel.PrettyQuery),
                Panel.all == Panel.values.toList
            )
        },
        test("labels are non-empty and unique") {
            val labels = Panel.values.toList.map(Panel.label)
            assertTrue(
                labels.forall(_.nonEmpty),
                labels.toSet.size == labels.size
            )
        },
        test("icons are non-empty and unique") {
            val icons = Panel.values.toList.map(Panel.icon)
            assertTrue(
                icons.forall(_.nonEmpty),
                icons.toSet.size == icons.size
            )
        },
        test("default panel is Matches so the first paint shows user-primary output") {
            assertTrue(Panel.default == Panel.Matches)
        }
    )
