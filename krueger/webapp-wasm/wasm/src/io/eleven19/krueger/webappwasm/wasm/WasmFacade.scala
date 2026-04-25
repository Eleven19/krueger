package io.eleven19.krueger.webappwasm.wasm

import scala.scalajs.js
import scala.scalajs.js.annotation.JSExportTopLevel

import io.eleven19.krueger.webappwasm.KruegerJs

/** Top-level `KruegerWasm` export for the WASM-linked variant of the facade.
  *
  * The Scala.js Wasm linker honors `@JSExportTopLevel` on top-level **vals** (the import callback receives the val's
  * value, which the linker then publishes as the named ES-module export) but drops `@JSExport` on `object` members. So
  * the JS-linked `KruegerJs object @JSExportTopLevel("Krueger")` pattern emits an empty `Krueger` namespace under the
  * Wasm linker. We work around that by building the namespace as a `js.Dynamic.literal` of closures and exporting the
  * literal under a distinct name (`KruegerWasm`); the SvelteKit playground reads the right export per backend.
  *
  * Each closure delegates to [[KruegerJs]] so envelope shape stays identical between the two link targets — the only
  * runtime difference is which artifact is actually executing the bytes.
  */
object WasmFacade:

    @JSExportTopLevel("KruegerWasm")
    val krueger: js.Object = js.Dynamic
        .literal(
            parseCst = ((src: String) => KruegerJs.parseCst(src)): js.Function1[String, js.Object],
            parseAst = ((src: String) => KruegerJs.parseAst(src)): js.Function1[String, js.Object],
            parseCstUnist = ((src: String) => KruegerJs.parseCstUnist(src)): js.Function1[String, js.Object],
            parseAstUnist = ((src: String) => KruegerJs.parseAstUnist(src)): js.Function1[String, js.Object],
            parseQuery = ((q: String) => KruegerJs.parseQuery(q)): js.Function1[String, js.Object],
            runQuery =
                ((q: js.Any, root: js.Any) => KruegerJs.runQuery(q, root)): js.Function2[js.Any, js.Any, js.Object],
            prettyQuery = ((q: js.Any) => KruegerJs.prettyQuery(q)): js.Function1[js.Any, String],
            tokenize = ((src: String) => KruegerJs.tokenize(src)): js.Function1[String, js.Object]
        )
        .asInstanceOf[js.Object]
