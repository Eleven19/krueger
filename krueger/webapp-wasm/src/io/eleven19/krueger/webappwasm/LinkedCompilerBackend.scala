package io.eleven19.krueger.webappwasm

import io.eleven19.krueger.compiler.CompilerComponent
import io.eleven19.krueger.compiler.Krueger
import io.eleven19.krueger.compiler.MatchView
import io.eleven19.krueger.cst.CstNode
import io.eleven19.krueger.trees.query.Query

/** Single compile backend backed by whichever Scala.js compiler the surrounding link target produced.
  *
  * Two link targets share this source via `package.mill`:
  *   - `webapp-wasm` (CommonScalaJSModule) — emits a JavaScript ES module; calls go through Scala.js's JS output.
  *   - `webapp-wasm.wasm` (CommonScalaJSWasmModule) — emits a WebAssembly module; calls go through Scala.js's
  *     experimental Wasm-GC backend.
  *
  * The Scala source is identical between the two targets — the only runtime difference is which compiled artifact the
  * SvelteKit playground dynamic-imports at the URL boundary (see `sites/try-wasm/src/lib/krueger.ts`). That means the
  * "backend selector" lives entirely in TypeScript: there is no in-Scala registry of multiple compilers.
  */
object LinkedCompilerBackend:

    import io.eleven19.krueger.cst.CstQueryableTree.given

    private val compiler: CompilerComponent[Unit] = Krueger.compiler[Unit]

    def parseCst(src: String): CompilerComponent.CompileResult[Unit, io.eleven19.krueger.cst.CstModule] =
        CompilerComponent.runUnit(compiler.parseCst(src))

    def parseAst(src: String): CompilerComponent.CompileResult[Unit, io.eleven19.krueger.ast.Module] =
        CompilerComponent.runUnit(compiler.parseAst(src))

    def parseQuery(src: String): CompilerComponent.CompileResult[Unit, Query] =
        CompilerComponent.runUnit(compiler.parseQuery(src))

    def runQuery(query: Query, root: CstNode): CompilerComponent.CompileResult[Unit, List[MatchView]] =
        CompilerComponent.runUnit(compiler.runQuery[CstNode](query, root))

    def prettyQuery(query: Query): String =
        compiler.prettyQuery(query)
