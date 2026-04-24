package io.eleven19.krueger.compiler

import io.eleven19.krueger.Krueger as CoreKrueger
import io.eleven19.krueger.ast.Module as AstModule
import io.eleven19.krueger.cst.CstModule
import io.eleven19.krueger.trees.QueryableTree
import io.eleven19.krueger.trees.query.Matcher
import io.eleven19.krueger.trees.query.Query
import io.eleven19.krueger.trees.query.QueryLogic
import io.eleven19.krueger.trees.query.QueryParser
import io.eleven19.krueger.trees.query.QueryPretty

/** Default implementation of [[CompilerComponent]] that wraps the existing pure Krueger APIs inside PureLogic effects.
  * Every parse/query failure becomes a structured [[CompileError]] surfaced through the result envelope via
  * [[QueryLogic.failFast]], never an exception.
  */
object Krueger:

    lazy val defaultCompiler: CompilerComponent[Unit] = compiler[Unit]

    /** A [[CompilerComponent]] for any caller-chosen context. The default implementation does not read or write the
      * context itself — it is threaded through for composition with the caller's own stateful effects.
      */
    def compiler[Ctx]: CompilerComponent[Ctx] = new CompilerComponent[Ctx]:
        import CompilerComponent.CompileEff

        def parseCst(source: String): CompileEff[Ctx, CstModule] =
            CoreKrueger.parseCst(source) match
                case parsley.Success(m) => m
                case parsley.Failure(msg) =>
                    QueryLogic.failFast[Ctx, String, CompileError](
                        CompileError.ParseError(phase = "cst", message = msg.toString)
                    )

        def parseAst(source: String): CompileEff[Ctx, AstModule] =
            CoreKrueger.parseAst(source) match
                case parsley.Success(m) => m
                case parsley.Failure(msg) =>
                    QueryLogic.failFast[Ctx, String, CompileError](
                        CompileError.ParseError(phase = "ast", message = msg.toString)
                    )

        def parseQuery(q: String): CompileEff[Ctx, Query] =
            QueryParser.parse(q) match
                case parsley.Success(query) => query
                case parsley.Failure(msg) =>
                    QueryLogic.failFast[Ctx, String, CompileError](
                        CompileError.QueryError(message = msg.toString)
                    )

        def runQuery[T](q: Query, root: T)(using QueryableTree[T]): CompileEff[Ctx, List[MatchView]] =
            Matcher.matches(q, root).map(MatchView.from(_)).toList

        def prettyQuery(q: Query): String = QueryPretty.render(q)
