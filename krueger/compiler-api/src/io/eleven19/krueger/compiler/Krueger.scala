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
  * Every parse/query failure becomes a structured [[CompileError]] accumulated in the result envelope via
  * [[QueryLogic.failFast]], never an exception.
  */
object Krueger:

    val component: CompilerComponent = new CompilerComponent:
        import CompilerComponent.CompileEff

        def parseCst(source: String): CompileEff[CstModule] =
            CoreKrueger.parseCst(source) match
                case parsley.Success(m) => m
                case parsley.Failure(msg) =>
                    QueryLogic.failFast[Unit, String, CompileError](
                        CompileError.ParseError(phase = "cst", message = msg.toString)
                    )

        def parseAst(source: String): CompileEff[AstModule] =
            CoreKrueger.parseAst(source) match
                case parsley.Success(m) => m
                case parsley.Failure(msg) =>
                    QueryLogic.failFast[Unit, String, CompileError](
                        CompileError.ParseError(phase = "ast", message = msg.toString)
                    )

        def parseQuery(q: String): CompileEff[Query] =
            QueryParser.parse(q) match
                case parsley.Success(query) => query
                case parsley.Failure(msg) =>
                    QueryLogic.failFast[Unit, String, CompileError](
                        CompileError.QueryError(message = msg.toString)
                    )

        def runQuery[T](q: Query, root: T)(using QueryableTree[T]): CompileEff[List[MatchView]] =
            Matcher.matches(q, root).map(MatchView.from(_)).toList

        def prettyQuery(q: Query): String = QueryPretty.render(q)
