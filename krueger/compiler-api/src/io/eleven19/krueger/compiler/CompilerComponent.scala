package io.eleven19.krueger.compiler

import io.eleven19.krueger.ast.Module as AstModule
import io.eleven19.krueger.cst.CstModule
import io.eleven19.krueger.trees.QueryableTree
import io.eleven19.krueger.trees.query.Query
import io.eleven19.krueger.trees.query.QueryLogic

/** A framework- and platform-agnostic compiler surface for Krueger.
  *
  * All effectful operations return a PureLogic-backed [[QueryLogic.QueryEffect]] so consumers can run them at the edge
  * of a UI or FFI boundary and receive a plain [[QueryLogic.Result]] envelope (context, logs, errors, value). The
  * [[QueryLogic.Logic]] type itself never crosses the JS / WASM FFI boundary.
  */
trait CompilerComponent:
    import CompilerComponent.CompileEff

    def parseCst(source: String): CompileEff[CstModule]
    def parseAst(source: String): CompileEff[AstModule]
    def parseQuery(q: String): CompileEff[Query]
    def runQuery[T](q: Query, root: T)(using QueryableTree[T]): CompileEff[List[MatchView]]
    def prettyQuery(q: Query): String

object CompilerComponent:

    /** Default v1 context type — consumers that need richer context can refine. */
    type CompileCtx = Unit

    /** Default log entry type — string for v1, can move to a structured ADT later. */
    type CompileLog = String

    /** Structured error type shared by every phase. */
    type CompileErr = CompileError

    /** Convenience alias so UI code reads as `CompileEff[Query]` rather than a long generic application. */
    type CompileEff[A] = QueryLogic.QueryEffect[CompileCtx, CompileLog, CompileErr, A]

    /** Result envelope returned by [[run]]. */
    type CompileResult[A] = QueryLogic.Result[CompileCtx, CompileLog, CompileErr, A]

    /** Run an effect with the default unit context. Call at the edge of a UI or FFI boundary. */
    inline def run[A](eff: CompileEff[A]): CompileResult[A] =
        QueryLogic.run[CompileCtx, CompileLog, CompileErr, A](())(eff)
