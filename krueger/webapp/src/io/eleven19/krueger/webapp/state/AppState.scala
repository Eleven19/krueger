package io.eleven19.krueger.webapp.state

import com.raquo.laminar.api.L.*

import io.eleven19.krueger.ast.Module as AstModule
import io.eleven19.krueger.compiler.CompilerComponent
import io.eleven19.krueger.compiler.Krueger
import io.eleven19.krueger.compiler.MatchView
import io.eleven19.krueger.cst.CstModule
import io.eleven19.krueger.cst.CstNode
import io.eleven19.krueger.cst.CstQueryableTree.given
import io.eleven19.krueger.trees.query.Query

/** Reactive state for the Try Krueger Laminar playground.
  *
  * All derivations flow through [[CompilerComponent]] so UI code never reaches into `Krueger.parseCst` or
  * `QueryParser.parse` directly. Failures surface in the PureLogic `Result` envelope (`.value` is `Either`, `.errors`
  * is a list), never as thrown exceptions — downstream signals keep emitting.
  *
  * @note
  *   [[matchResult]] collapses the compile envelope to a bare `List[MatchView]` so components can render a list without
  *   switching on `Either`. Errors in source or query produce an empty list; callers that need the error reason observe
  *   [[cstResult]] / [[queryResult]] directly.
  */
final class AppState:

    private val compiler: CompilerComponent[Unit] = Krueger.compiler[Unit]

    val sourceVar: Var[String] = Var("")
    val queryVar: Var[String]  = Var("")

    val cstResult: Signal[CompilerComponent.CompileResult[Unit, CstModule]] =
        sourceVar.signal.map(s => CompilerComponent.runUnit(compiler.parseCst(s)))

    val astResult: Signal[CompilerComponent.CompileResult[Unit, AstModule]] =
        sourceVar.signal.map(s => CompilerComponent.runUnit(compiler.parseAst(s)))

    val queryResult: Signal[CompilerComponent.CompileResult[Unit, Query]] =
        queryVar.signal.map(q => CompilerComponent.runUnit(compiler.parseQuery(q)))

    val matchResult: Signal[List[MatchView]] =
        cstResult.combineWith(queryResult).map { (cstR, qR) =>
            (cstR.value, qR.value) match
                case (Right(cst), Right(q)) =>
                    CompilerComponent
                        .runUnit(compiler.runQuery[CstNode](q, cst))
                        .value
                        .getOrElse(List.empty)
                case _ =>
                    List.empty
        }
