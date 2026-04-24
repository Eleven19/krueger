package io.eleven19.krueger.webappwasm

import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.annotation.JSExport
import scala.scalajs.js.annotation.JSExportTopLevel

import io.eleven19.krueger.compiler.CapturedNode
import io.eleven19.krueger.compiler.CompileError
import io.eleven19.krueger.compiler.CompilerComponent
import io.eleven19.krueger.compiler.MatchView
import io.eleven19.krueger.compiler.Span
import io.eleven19.krueger.cst.CstModule
import io.eleven19.krueger.cst.CstNode
import io.eleven19.krueger.trees.query.Query

/** Plain-JS FFI facade over the shared [[CompilerComponent]]. Exposed as the
  * top-level `Krueger` binding in the emitted ES module so a framework-agnostic
  * front-end (in particular the SvelteKit `sites/try-wasm/` playground) can
  * drive the compiler from TypeScript without touching Scala types.
  *
  * Every entry point runs the PureLogic effect at the boundary (via
  * [[CompilerComponent.runUnit]]) and serializes the [[QueryLogic.Result]] into
  * a stable envelope:
  *
  * {{{
  *   { ok: boolean, value: T | null, logs: string[], errors: ErrorPojo[] }
  * }}}
  *
  * The `Logic` type itself never crosses the JS boundary. Callers receive a
  * plain object whose shape is pinned by [[KruegerJsSpec]] per
  * REQ-webappwasm-001..003.
  *
  * `runQuery` is intentionally specialised to [[CstModule]] roots for MVP —
  * all current consumers drive the playground through `parseCst -> runQuery`.
  * An AST-root variant can be added alongside once the SvelteKit UI needs it.
  */
@JSExportTopLevel("Krueger")
object KruegerJs:

    import CompilerComponent.CompileResult

    @JSExport
    def parseCst(src: String): js.Object =
        envelopeWithOpaqueValue(BackendLoader.current().parseCst(src))

    @JSExport
    def parseAst(src: String): js.Object =
        envelopeWithOpaqueValue(BackendLoader.current().parseAst(src))

    @JSExport
    def parseQuery(q: String): js.Object =
        envelopeWithOpaqueValue(BackendLoader.current().parseQuery(q))

    /** Execute a previously-parsed [[Query]] against a previously-parsed
      * [[CstModule]]. Both handles must come from earlier `parseQuery` /
      * `parseCst` calls — the wrapping Scala values are passed back through
      * the JS boundary as opaque references, not serialized JSON.
      */
    @JSExport
    def runQuery(q: js.Any, root: js.Any): js.Object =
        val qScala    = q.asInstanceOf[Query]
        val rootScala = root.asInstanceOf[CstNode]
        val result    = BackendLoader.current().runQuery(qScala, rootScala)
        envelopeWithMatches(result)

    /** Canonical echo of a parsed query. Pure — no envelope. */
    @JSExport
    def prettyQuery(q: js.Any): String = BackendLoader.current().prettyQuery(q.asInstanceOf[Query])

    // ------------------------------------------------------------------
    // Envelope helpers
    // ------------------------------------------------------------------

    /** Used by parseCst/parseAst/parseQuery: the success value is a Scala
      * handle (CstModule / AstModule / Query) and we pass it back through the
      * FFI as an opaque reference. The SvelteKit side never inspects the
      * internals — it only round-trips the handle into `runQuery` /
      * `prettyQuery`.
      */
    private def envelopeWithOpaqueValue[A <: AnyRef](r: CompileResult[Unit, A]): js.Object =
        val env = js.Dynamic.literal()
        r.value match
            case Right(v) =>
                env.updateDynamic("ok")(true)
                env.updateDynamic("value")(v.asInstanceOf[js.Any])
            case Left(_) =>
                env.updateDynamic("ok")(false)
                env.updateDynamic("value")(null)
        attachLogsAndErrors(env, r)
        env.asInstanceOf[js.Object]

    /** Used by runQuery: the success value is a List[MatchView] and we
      * serialize it to a plain JS array of POJOs so consumers don't need to
      * import any Scala.js runtime helpers.
      */
    private def envelopeWithMatches(r: CompileResult[Unit, List[MatchView]]): js.Object =
        val env = js.Dynamic.literal()
        r.value match
            case Right(matches) =>
                env.updateDynamic("ok")(true)
                env.updateDynamic("value")(matches.map(matchPojo).toJSArray.asInstanceOf[js.Any])
            case Left(_) =>
                env.updateDynamic("ok")(false)
                env.updateDynamic("value")(null)
        attachLogsAndErrors(env, r)
        env.asInstanceOf[js.Object]

    private def attachLogsAndErrors[A](env: js.Dynamic, r: CompileResult[Unit, A]): Unit =
        env.updateDynamic("logs")(r.logs.toJSArray.asInstanceOf[js.Any])
        env.updateDynamic("errors")(r.errors.map(errorPojo).toJSArray.asInstanceOf[js.Any])

    private def errorPojo(e: CompileError): js.Object =
        import CompileError.*
        e match
            case ParseError(phase, message, spanOpt) =>
                val o = js.Dynamic.literal(phase = phase, message = message)
                spanOpt.foreach(s => o.updateDynamic("span")(spanPojo(s)))
                o.asInstanceOf[js.Object]
            case QueryError(message, spanOpt) =>
                val o = js.Dynamic.literal(phase = "query", message = message)
                spanOpt.foreach(s => o.updateDynamic("span")(spanPojo(s)))
                o.asInstanceOf[js.Object]
            case InternalError(message) =>
                js.Dynamic.literal(phase = "internal", message = message).asInstanceOf[js.Object]

    private def spanPojo(s: Span): js.Object =
        js.Dynamic.literal(start = s.start, end = s.end).asInstanceOf[js.Object]

    private def matchPojo(m: MatchView): js.Object =
        val captures = js.Dynamic.literal()
        m.captures.foreach((name, node) => captures.updateDynamic(name)(capturedNodePojo(node)))
        val o = js.Dynamic.literal(rootNodeType = m.rootNodeType, captures = captures)
        m.rootText.foreach(t => o.updateDynamic("rootText")(t))
        o.asInstanceOf[js.Object]

    private def capturedNodePojo(n: CapturedNode): js.Object =
        val o = js.Dynamic.literal(nodeType = n.nodeType, childCount = n.childCount)
        n.text.foreach(t => o.updateDynamic("text")(t))
        o.asInstanceOf[js.Object]
