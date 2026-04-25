package io.eleven19.krueger.webappwasm

import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.annotation.JSExport
import scala.scalajs.js.annotation.JSExportTopLevel

import io.eleven19.krueger.ast.AstNode
import io.eleven19.krueger.ast.AstUnistProjection.given
import io.eleven19.krueger.compiler.CapturedNode
import io.eleven19.krueger.compiler.CompileError
import io.eleven19.krueger.compiler.CompilerComponent
import io.eleven19.krueger.compiler.MatchView
import io.eleven19.krueger.compiler.Span
import io.eleven19.krueger.cst.CstModule
import io.eleven19.krueger.cst.CstNode
import io.eleven19.krueger.cst.CstUnistProjection.given
import io.eleven19.krueger.lexer.ElmToken
import io.eleven19.krueger.lexer.ElmTokenizer
import io.eleven19.krueger.trees.query.Query
import io.eleven19.krueger.trees.query.QueryLogic
import io.eleven19.krueger.trees.unist.UnistNode
import io.eleven19.krueger.trees.unist.UnistPoint
import io.eleven19.krueger.trees.unist.UnistPosition
import io.eleven19.krueger.trees.unist.UnistProjection

/** Plain-JS FFI facade over the shared [[CompilerComponent]]. Exposed as the top-level `Krueger` binding in the emitted
  * ES module so a framework-agnostic front-end (in particular the SvelteKit `sites/try-wasm/` playground) can drive the
  * compiler from TypeScript without touching Scala types.
  *
  * Every entry point runs the PureLogic effect at the boundary (via [[CompilerComponent.runUnit]]) and serializes the
  * [[QueryLogic.Result]] into a stable envelope:
  *
  * {{{
  *   { ok: boolean, value: T | null, logs: string[], errors: ErrorPojo[] }
  * }}}
  *
  * The `Logic` type itself never crosses the JS boundary. Callers receive a plain object whose shape is pinned by
  * [[KruegerJsSpec]] per REQ-webappwasm-001..003.
  *
  * `runQuery` is intentionally specialised to [[CstModule]] roots for MVP — all current consumers drive the playground
  * through `parseCst -> runQuery`. An AST-root variant can be added alongside once the SvelteKit UI needs it.
  */
@JSExportTopLevel("Krueger")
object KruegerJs:

    import CompilerComponent.CompileResult

    @JSExport
    def parseCst(src: String): js.Object =
        envelopeWithOpaqueValue(LinkedCompilerBackend.parseCst(src))

    @JSExport
    def parseAst(src: String): js.Object =
        envelopeWithOpaqueValue(LinkedCompilerBackend.parseAst(src))

    @JSExport
    def parseCstUnist(src: String): js.Object =
        envelopeWithCstUnist(LinkedCompilerBackend.parseCst(src), src)

    @JSExport
    def parseAstUnist(src: String): js.Object =
        envelopeWithAstUnist(LinkedCompilerBackend.parseAst(src), src)

    @JSExport
    def parseQuery(q: String): js.Object =
        envelopeWithOpaqueValue(LinkedCompilerBackend.parseQuery(q))

    /** Execute a previously-parsed [[Query]] against a previously-parsed [[CstModule]]. Both handles must come from
      * earlier `parseQuery` / `parseCst` calls — the wrapping Scala values are passed back through the JS boundary as
      * opaque references, not serialized JSON.
      */
    @JSExport
    def runQuery(q: js.Any, root: js.Any): js.Object =
        val qScala    = q.asInstanceOf[Query]
        val rootScala = root.asInstanceOf[CstNode]
        val result    = LinkedCompilerBackend.runQuery(qScala, rootScala)
        envelopeWithMatches(result)

    /** Canonical echo of a parsed query. Pure — no envelope. */
    @JSExport
    def prettyQuery(q: js.Any): String = LinkedCompilerBackend.prettyQuery(q.asInstanceOf[Query])

    @JSExport
    def tokenize(src: String): js.Object =
        envelopeWithTokens(ElmTokenizer.run(src))

    // ------------------------------------------------------------------
    // Envelope helpers
    // ------------------------------------------------------------------

    /** Used by parseCst/parseAst/parseQuery: the success value is a Scala handle (CstModule / AstModule / Query) and we
      * pass it back through the FFI as an opaque reference. The SvelteKit side never inspects the internals — it only
      * round-trips the handle into `runQuery` / `prettyQuery`.
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

    private def envelopeWithCstUnist(r: CompileResult[Unit, CstModule], src: String): js.Object =
        val env = js.Dynamic.literal()
        r.value match
            case Right(v) =>
                env.updateDynamic("ok")(true)
                env.updateDynamic("value")(unistNodePojo(UnistProjection.project(v: CstNode, Some(src))))
            case Left(_) =>
                env.updateDynamic("ok")(false)
                env.updateDynamic("value")(null)
        attachLogsAndErrors(env, r)
        env.asInstanceOf[js.Object]

    private def envelopeWithAstUnist(r: CompileResult[Unit, io.eleven19.krueger.ast.Module], src: String): js.Object =
        val env = js.Dynamic.literal()
        r.value match
            case Right(v) =>
                env.updateDynamic("ok")(true)
                env.updateDynamic("value")(unistNodePojo(UnistProjection.project(v: AstNode, Some(src))))
            case Left(_) =>
                env.updateDynamic("ok")(false)
                env.updateDynamic("value")(null)
        attachLogsAndErrors(env, r)
        env.asInstanceOf[js.Object]

    /** Used by runQuery: the success value is a List[MatchView] and we serialize it to a plain JS array of POJOs so
      * consumers don't need to import any Scala.js runtime helpers.
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

    private def envelopeWithTokens(r: ElmTokenizer.TokenizeResult[Vector[ElmToken]]): js.Object =
        val env = js.Dynamic.literal()
        r.value match
            case Right(tokens) =>
                env.updateDynamic("ok")(true)
                env.updateDynamic("value")(tokens.map(tokenPojo).toJSArray.asInstanceOf[js.Any])
            case Left(_) =>
                env.updateDynamic("ok")(false)
                env.updateDynamic("value")(null)
        attachLogsAndErrors(env, r)
        env.asInstanceOf[js.Object]

    private def attachLogsAndErrors[Ctx, A](
        env: js.Dynamic,
        r: QueryLogic.Result[Ctx, String, CompileError, A]
    ): Unit =
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

    private def tokenPojo(t: ElmToken): js.Object =
        js.Dynamic
            .literal(
                kind = t.kind.toString,
                lexeme = t.lexeme,
                start = t.start,
                end = t.end
            )
            .asInstanceOf[js.Object]

    private def capturedNodePojo(n: CapturedNode): js.Object =
        val o = js.Dynamic.literal(nodeType = n.nodeType, childCount = n.childCount)
        n.text.foreach(t => o.updateDynamic("text")(t))
        o.asInstanceOf[js.Object]

    private def unistNodePojo(node: UnistNode): js.Object =
        val o = js.Dynamic.literal(
            `type` = node.`type`,
            data = unistDataPojo(node.data),
            children = node.children.map(unistNodePojo).toJSArray.asInstanceOf[js.Any]
        )
        node.value.foreach(value => o.updateDynamic("value")(value))
        node.position.foreach(position => o.updateDynamic("position")(positionPojo(position)))
        o.asInstanceOf[js.Object]

    private def unistDataPojo(data: io.eleven19.krueger.trees.unist.UnistData): js.Object =
        val fields = js.Dynamic.literal()
        data.fields.foreach((name, indexes) => fields.updateDynamic(name)(indexes.toJSArray.asInstanceOf[js.Any]))
        js.Dynamic.literal(
            childCount = data.childCount,
            fields = fields.asInstanceOf[js.Object]
        ).asInstanceOf[js.Object]

    private def positionPojo(position: UnistPosition): js.Object =
        js.Dynamic.literal(
            start = pointPojo(position.start),
            end = pointPojo(position.end)
        ).asInstanceOf[js.Object]

    private def pointPojo(point: UnistPoint): js.Object =
        val o = js.Dynamic.literal(
            line = point.line,
            column = point.column
        )
        point.offset.foreach(offset => o.updateDynamic("offset")(offset))
        o.asInstanceOf[js.Object]
