package io.eleven19.krueger.itest

import parsley.{Failure, Result, Success}

import scala.io.Source

import io.eleven19.krueger.Krueger
import io.eleven19.krueger.ast.Module
import io.eleven19.krueger.cst.CstModule

/** Scenario-scoped mutable state shared across step-definition classes via the cucumber-scala DI container. */
final class TestDriver:
    private var source: String                               = ""
    private var cstResult: Option[Result[String, CstModule]] = None
    private var astResult: Option[Result[String, Module]]    = None

    def setSource(raw: String): Unit =
        source = raw
        cstResult = None
        astResult = None

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
