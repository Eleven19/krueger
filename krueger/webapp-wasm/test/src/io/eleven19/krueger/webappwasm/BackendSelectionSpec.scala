package io.eleven19.krueger.webappwasm

import zio.test.*

/** Contract spec for [[BackendLoader]] / [[KruegerJs.setBackend]] /
  * [[KruegerJs.currentBackend]].
  *
  * Pins:
  *   - `currentBackend()` defaults to `"webgc"` on a fresh load.
  *   - `setBackend("webgc")` and `setBackend("js")` both succeed and flip the
  *     active backend so subsequent compile calls go through the right
  *     [[CompilerBackend]].
  *   - Unknown ids leave the active backend unchanged and return `false`.
  *
  * The tests use [[BackendLoader.resetForTesting]] to keep ordering between
  * cases deterministic — this trait state would otherwise carry across test
  * runs in the same process.
  */
object BackendSelectionSpec extends ZIOSpecDefault:

    private val validSource =
        """module M exposing (..)
          |
          |x = 1
          |""".stripMargin

    private val validQuery = "(CstValueDeclaration) @v"

    def spec = suite("Backend selection (REQ-playground-consolidate-002)")(
        test("currentBackend defaults to webgc on a fresh load") {
            BackendLoader.resetForTesting()
            assertTrue(
                KruegerJs.currentBackend() == "webgc",
                BackendLoader.current().id == "webgc"
            )
        },
        test("setBackend('js') flips the active backend and returns true") {
            BackendLoader.resetForTesting()
            val accepted = KruegerJs.setBackend("js")
            assertTrue(
                accepted,
                KruegerJs.currentBackend() == "js",
                BackendLoader.current().id == "js"
            )
        },
        test("setBackend('webgc') flips the active backend and returns true") {
            BackendLoader.resetForTesting()
            // First switch to js so the assertion exercises the round-trip.
            val _ = KruegerJs.setBackend("js")
            val accepted = KruegerJs.setBackend("webgc")
            assertTrue(
                accepted,
                KruegerJs.currentBackend() == "webgc",
                BackendLoader.current().id == "webgc"
            )
        },
        test("setBackend with an unknown id returns false and leaves state intact") {
            BackendLoader.resetForTesting()
            val before   = KruegerJs.currentBackend()
            val accepted = KruegerJs.setBackend("nonsense")
            assertTrue(
                !accepted,
                KruegerJs.currentBackend() == before
            )
        },
        test("compile calls under the JS backend still produce a well-formed envelope") {
            BackendLoader.resetForTesting()
            val _ = KruegerJs.setBackend("js")
            val env = KruegerJs.parseCst(validSource)
            val d   = env.asInstanceOf[scala.scalajs.js.Dynamic]
            assertTrue(
                BackendLoader.current().id == "js",
                d.ok.asInstanceOf[Boolean] == true
            )
        },
        test("compile calls under the WebGC backend still produce a well-formed envelope") {
            BackendLoader.resetForTesting()
            val _ = KruegerJs.setBackend("webgc")
            val cstEnv = KruegerJs.parseCst(validSource).asInstanceOf[scala.scalajs.js.Dynamic]
            val qEnv   = KruegerJs.parseQuery(validQuery).asInstanceOf[scala.scalajs.js.Dynamic]
            val matchEnv =
                KruegerJs.runQuery(qEnv.value, cstEnv.value).asInstanceOf[scala.scalajs.js.Dynamic]
            assertTrue(
                BackendLoader.current().id == "webgc",
                cstEnv.ok.asInstanceOf[Boolean] == true,
                qEnv.ok.asInstanceOf[Boolean] == true,
                matchEnv.ok.asInstanceOf[Boolean] == true
            )
        }
    )
