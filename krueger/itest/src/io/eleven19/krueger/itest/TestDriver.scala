package io.eleven19.krueger.itest

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

import parsley.{Failure, Result, Success}

import scala.io.Source

import io.eleven19.krueger.Krueger
import io.eleven19.krueger.ast.AstNode
import io.eleven19.krueger.ast.AstQueryableTree.given
import io.eleven19.krueger.ast.Module
import io.eleven19.krueger.compiler.abi.InvokeCompiler
import io.eleven19.krueger.cst.CstModule
import io.eleven19.krueger.cst.CstNode
import io.eleven19.krueger.cst.CstQueryableTree.given
import io.eleven19.krueger.trees.QueryableTree
import io.eleven19.krueger.trees.query.*

object TestDriver:

    private val scalaJsNodeBackend = "scalajs-node"
    private val supportedBackends  = Set("jvm", "chicory", scalaJsNodeBackend)

    def requireSupportedBackend(backend: String): Unit =
        if !supportedBackends.contains(backend) then
            throw AssertionError(
                s"unsupported compiler backend [$backend]; expected ${supportedBackends.toVector.sorted.mkString(", ")}"
            )

    def invoke(backend: String, op: String, inputJson: String): String =
        requireSupportedBackend(backend)
        backend match
            case "jvm"               => InvokeCompiler.invoke(op, inputJson)
            case "chicory"           => ChicorySupportedCompilerHarness.invoke(op, inputJson)
            case `scalaJsNodeBackend` => invokeScalaJsNode(op, inputJson)

    private def invokeScalaJsNode(op: String, inputJson: String): String =
        val artifactDir = Path.of(
            Option(System.getProperty("krueger.webapp-wasm.facade.dir")).getOrElse(
                throw AssertionError(
                    "missing system property krueger.webapp-wasm.facade.dir; run this suite through Mill so it can inject the linked Scala.js facade path"
                )
            )
        )
        val entrypoint = artifactDir.resolve("main.js")
        if !Files.isRegularFile(entrypoint) then
            throw AssertionError(s"missing Scala.js facade entrypoint: $entrypoint")

        val script = Files.createTempFile("krueger-scalajs-node-driver-", ".mjs")
        try
            Files.writeString(script, scalaJsNodeScript, StandardCharsets.UTF_8)
            val process = ProcessBuilder(
                "node",
                script.toString,
                entrypoint.toString,
                op,
                inputJson
            ).redirectErrorStream(true).start()
            val output = String(process.getInputStream.readAllBytes(), StandardCharsets.UTF_8)
            val exit   = process.waitFor()
            if exit != 0 then
                throw AssertionError(s"Scala.js Node compiler backend failed with exit $exit:\n$output")
            output.trim
        finally
            val _ = Files.deleteIfExists(script)

    private val scalaJsNodeScript =
        """
          |import { pathToFileURL } from "node:url";
          |
          |const [, , entrypoint, op, inputJson] = process.argv;
          |const mod = await import(pathToFileURL(entrypoint).href);
          |const krueger = mod.Krueger ?? globalThis.Krueger;
          |
          |function errorEnvelope(phase, message) {
          |  return JSON.stringify({ ok: false, logs: [], errors: [{ phase, message }] });
          |}
          |
          |function normalizeErrors(errors) {
          |  return Array.from(errors ?? []).map((error) => ({
          |    phase: String(error.phase ?? "internal"),
          |    message: String(error.message ?? error),
          |    ...(error.span === undefined ? {} : { span: error.span }),
          |  }));
          |}
          |
          |function toInvokeResponse(env) {
          |  const ok = Boolean(env.ok);
          |  return JSON.stringify({
          |    ok,
          |    ...(ok && env.value != null ? { value: String(env.value) } : {}),
          |    logs: Array.from(env.logs ?? []).map(String),
          |    errors: normalizeErrors(env.errors),
          |  });
          |}
          |
          |if (!krueger) {
          |  console.log(errorEnvelope("internal", "Scala.js Krueger facade was not exported"));
          |  process.exit(0);
          |}
          |
          |try {
          |  switch (op) {
          |    case "parseCst": {
          |      const request = JSON.parse(inputJson);
          |      console.log(toInvokeResponse(krueger.parseCst(String(request.source ?? ""))));
          |      break;
          |    }
          |    default:
          |      console.log(errorEnvelope("internal", `unknown operation: ${op}`));
          |  }
          |} catch (error) {
          |  console.log(errorEnvelope("internal", error?.stack ?? String(error)));
          |}
          |""".stripMargin

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

    /** Store a raw query string for later canonicalization via [[canonicalizeQuerySource]]. */
    def setQuerySource(queryText: String): Unit =
        querySource = Some(queryText)
        canonicalQueryText = None

    /** Parse the stored query source and compute its canonical S-expression form via [[QueryPretty]].
      *
      * Throws an `AssertionError` if the query fails to parse. The canonical text is then available via
      * [[canonicalQuery]].
      */
    def canonicalizeQuerySource(): Unit =
        val raw = querySource.getOrElse(throw new AssertionError("query source not set — missing Given step?"))
        val query = QueryParser.parse(raw) match
            case Success(q) => q
            case Failure(msg) =>
                throw new AssertionError(s"query parse failed: $msg\nQuery: $raw")
        canonicalQueryText = Some(QueryPretty.render(query))

    /** The canonical S-expression text produced by the most recent [[canonicalizeQuerySource]] call. */
    def canonicalQuery: String =
        canonicalQueryText.getOrElse(throw new AssertionError("canonical query not set — missing When step?"))

    /** Assert that [[canonicalQuery]] can itself be parsed successfully.
      *
      * Throws an `AssertionError` if the canonical form does not round-trip.
      */
    def canonicalQueryReparses: Unit =
        val canonical = canonicalQuery
        QueryParser.parse(canonical) match
            case Success(_) => ()
            case Failure(msg) =>
                throw new AssertionError(s"canonical query failed to parse: $msg\nQuery:\n$canonical")

    private def runQuery[T](queryText: String, root: T)(using qt: QueryableTree[T]): Vector[MatchView] =
        val query = QueryParser.parse(queryText) match
            case Success(q) => q
            case Failure(msg) =>
                throw new AssertionError(s"query parse failed: $msg\nQuery: $queryText")
        Matcher.matches(query, root).map(MatchView.from(_)).toVector
