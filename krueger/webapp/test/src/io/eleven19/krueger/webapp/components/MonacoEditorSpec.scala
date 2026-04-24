package io.eleven19.krueger.webapp.components

import zio.test.*

/** Covers REQ-webapp-monaco-editor-003: the plan builder runs on Node without resolving `monaco-editor`, producing a
  * [[MonacoPlan]] carrying the requested language, initial value, and shared editor ergonomics (`automaticLayout`,
  * minimap off, default font size).
  *
  * Keeping the plan pure — and in its own source file with no MonacoFacade dependency — is what lets the rest of the
  * Laminar component stay minimal: everything that isn't a DOM mount is test-pinned here, and the test bundle never
  * pulls in the `monaco-editor` npm package.
  */
object MonacoEditorSpec extends ZIOSpecDefault:

    def spec = suite("MonacoPlan.default")(
        test("happy: returns a plan with the requested language and initial value") {
            val p = MonacoPlan.default("elm", "module M exposing (..)\n")
            assertTrue(
                p.language == "elm",
                p.initialValue == "module M exposing (..)\n"
            )
        },
        test("automaticLayout is enabled so the editor tracks container resizes without manual wiring") {
            val p = MonacoPlan.default("plaintext", "")
            assertTrue(p.automaticLayout)
        },
        test("minimap is disabled so the panel stays dense for side-by-side editors") {
            val p = MonacoPlan.default("elm", "")
            assertTrue(!p.minimapEnabled)
        },
        test("edge: empty initial value still yields a well-formed plan") {
            val p = MonacoPlan.default("plaintext", "")
            assertTrue(
                p.initialValue == "",
                p.language == "plaintext",
                p.fontSize > 0
            )
        },
        test("determinism: same inputs produce equal plans") {
            val a = MonacoPlan.default("elm", "x = 1\n")
            val b = MonacoPlan.default("elm", "x = 1\n")
            assertTrue(a == b)
        }
    )
