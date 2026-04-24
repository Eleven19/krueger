package io.eleven19.krueger.itest

import parsley.{Failure, Result, Success}

import scala.io.Source

import io.eleven19.krueger.Krueger
import io.eleven19.krueger.ast.AstNode
import io.eleven19.krueger.ast.AstQueryableTree.given
import io.eleven19.krueger.ast.Module
import io.eleven19.krueger.cst.CstModule
import io.eleven19.krueger.cst.CstNode
import io.eleven19.krueger.cst.CstQueryableTree.given
import io.eleven19.krueger.trees.QueryableTree
import io.eleven19.krueger.trees.query.*

/** Scenario-scoped mutable state shared across step-definition classes via the cucumber-scala DI container. */
final class TestDriver:
    private var source: String                               = ""
    private var cstResult: Option[Result[String, CstModule]] = None
    private var astResult: Option[Result[String, Module]]    = None
    private var lastMatchesBuf: Vector[MatchView]            = Vector.empty
    private var querySource: Option[String]                  = None
    private var canonicalQueryText: Option[String]           = None

    def setSource(raw: String): Unit =
        source = raw
        cstResult = None
        astResult = None
        lastMatchesBuf = Vector.empty
        querySource = None
        canonicalQueryText = None

    def setQuerySource(queryText: String): Unit =
        querySource = Some(queryText)
        canonicalQueryText = None

    def canonicalizeQuerySource(): Unit =
        val raw = querySource.getOrElse(throw new AssertionError("query source not set — missing Given step?"))
        val query = QueryParser.parse(raw) match
            case Success(q) => q
            case Failure(msg) =>
                throw new AssertionError(s"query parse failed: $msg\nQuery: $raw")
        canonicalQueryText = Some(QueryPretty.render(query))

    def canonicalQuery: String =
        canonicalQueryText.getOrElse(throw new AssertionError("canonical query not set — missing When step?"))

    def canonicalQueryReparses: Unit =
        val canonical = canonicalQuery
        QueryParser.parse(canonical) match
            case Success(_) => ()
            case Failure(msg) =>
                throw new AssertionError(s"canonical query failed to parse: $msg\nQuery:\n$canonical")

    def setSourceFromResource(resourcePath: String): Unit =
        val stream = Option(getClass.getClassLoader.getResourceAsStream(resourcePath)) match
            case Some(stream) => stream
            case None         => throw new AssertionError(s"fixture resource not found: $resourcePath")

        try setSource(Source.fromInputStream(stream, "UTF-8").mkString)
        finally stream.close()

    def parseCst(): Unit = cstResult = Some(Krueger.parseCst(source))
    def parseAst(): Unit = astResult = Some(Krueger.parseAst(source))

    def cst: CstModule = cstResult match
        case Some(Success(m))   => m
        case Some(Failure(msg)) => throw new AssertionError(s"CST parse failed: $msg\nSource:\n$source")
        case None               => throw new AssertionError("CST not parsed — missing When step?")

    def ast: Module = astResult match
        case Some(Success(m))   => m
        case Some(Failure(msg)) => throw new AssertionError(s"AST parse failed: $msg\nSource:\n$source")
        case None               => throw new AssertionError("AST not parsed — missing When step?")

    /** Parse the CST (if not already parsed), run `queryText` against it, and store the matches. */
    def queryCst(queryText: String): Unit =
        if cstResult.isEmpty then parseCst()
        lastMatchesBuf = runQuery[CstNode](queryText, cst)

    /** Parse the AST (if not already parsed), run `queryText` against it, and store the matches. */
    def queryAst(queryText: String): Unit =
        if astResult.isEmpty then parseAst()
        lastMatchesBuf = runQuery[AstNode](queryText, ast)

    /** Matches collected by the most recent `queryCst` / `queryAst`. */
    def lastMatches: Vector[MatchView] = lastMatchesBuf

    private def runQuery[T](queryText: String, root: T)(using qt: QueryableTree[T]): Vector[MatchView] =
        val query = QueryParser.parse(queryText) match
            case Success(q) => q
            case Failure(msg) =>
                throw new AssertionError(s"query parse failed: $msg\nQuery: $queryText")
        Matcher.matches(query, root).map(MatchView.from(_)).toVector
