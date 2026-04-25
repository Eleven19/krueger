package io.eleven19.krueger.webappwasm

import scala.scalajs.js

import zio.test.*

/** Contract spec for the `@JSExportTopLevel("Krueger")` facade.
  *
  * The spec intentionally pins the plain-JS envelope shape that SvelteKit
  * consumers depend on per REQ-webappwasm-001..003:
  *
  *   `{ ok: boolean, value: any | null, logs: string[], errors: ErrorPojo[] }`
  *
  * All assertions are made through `js.Dynamic` so the facade is exercised
  * exactly as JavaScript callers would see it. No access to Scala-typed
  * internals is allowed in the assertions — that is the whole point of
  * the envelope.
  */
object KruegerJsSpec extends ZIOSpecDefault:

    private val validSource =
        """module M exposing (..)
          |
          |x = 1
          |""".stripMargin

    private val malformedSource = "module M exposing (..)\n\nx ="

    private val validQuery = "(CstValueDeclaration) @v"

    private def dyn(o: js.Object): js.Dynamic = o.asInstanceOf[js.Dynamic]

    private def arrayLen(arr: js.Any): Int = arr.asInstanceOf[js.Array[js.Any]].length

    private def hasEnvelopeShape(env: js.Object): Boolean =
        val d = dyn(env)
        val hasOk     = js.typeOf(d.ok) == "boolean"
        val hasLogs   = d.logs.asInstanceOf[js.Any] match
            case arr if js.Array.isArray(arr) => true
            case _                            => false
        val hasErrors = d.errors.asInstanceOf[js.Any] match
            case arr if js.Array.isArray(arr) => true
            case _                            => false
        // `value` may be absent, null, or any value — we only require the
        // OTHER three fields to be present with the right types.
        hasOk && hasLogs && hasErrors

    def spec = suite("KruegerJs (WASM FFI facade)")(
        suite("envelope shape (REQ-webappwasm-001)")(
            test("parseCst returns { ok, value?, logs, errors } with ok=true on valid source") {
                val env = KruegerJs.parseCst(validSource)
                val d   = dyn(env)
                assertTrue(
                    hasEnvelopeShape(env),
                    d.ok.asInstanceOf[Boolean] == true,
                    arrayLen(d.errors) == 0,
                    // value must be defined (truthy) on success — we don't
                    // inspect its internals because parseCst's value is an
                    // opaque handle.
                    !js.isUndefined(d.value) && d.value != null
                )
            },
            test("parseQuery returns an envelope with ok=true on valid query") {
                val env = KruegerJs.parseQuery(validQuery)
                val d   = dyn(env)
                assertTrue(
                    hasEnvelopeShape(env),
                    d.ok.asInstanceOf[Boolean] == true,
                    arrayLen(d.errors) == 0
                )
            },
            test("prettyQuery returns a non-empty canonical string for a parsed query") {
                val parseEnv = KruegerJs.parseQuery(validQuery)
                val q        = dyn(parseEnv).value.asInstanceOf[js.Any]
                val pretty   = KruegerJs.prettyQuery(q)
                assertTrue(pretty.nonEmpty)
            }
        ),
        suite("error envelope (REQ-webappwasm-002)")(
            test("parseCst on malformed source returns ok=false with errors; value is null") {
                val env = KruegerJs.parseCst(malformedSource)
                val d   = dyn(env)
                assertTrue(
                    hasEnvelopeShape(env),
                    d.ok.asInstanceOf[Boolean] == false,
                    arrayLen(d.errors) >= 1,
                    d.value == null
                )
            },
            test("each serialized error carries a non-empty message field") {
                val env    = KruegerJs.parseCst(malformedSource)
                val errs   = dyn(env).errors.asInstanceOf[js.Array[js.Dynamic]]
                val hasMsg = (0 until errs.length).forall { i =>
                    val msg = errs(i).message
                    js.typeOf(msg) == "string" && msg.asInstanceOf[String].nonEmpty
                }
                assertTrue(hasMsg)
            },
            test("parseQuery on malformed query returns ok=false with at least one error") {
                val env = KruegerJs.parseQuery("(unbalanced")
                val d   = dyn(env)
                assertTrue(
                    d.ok.asInstanceOf[Boolean] == false,
                    arrayLen(d.errors) >= 1,
                    d.value == null
                )
            }
        ),
        suite("edge cases")(
            test("empty source still produces a well-formed envelope (shape is uniform)") {
                val env = KruegerJs.parseCst("")
                assertTrue(hasEnvelopeShape(env))
            },
            test("empty query still produces a well-formed envelope (shape is uniform)") {
                val env = KruegerJs.parseQuery("")
                assertTrue(hasEnvelopeShape(env))
            }
        ),
        suite("shared tokenizer facade")(
            test("tokenize returns plain JS token POJOs with spans and kinds") {
                val env    = dyn(KruegerJs.tokenize("""module Main = "hi""""))
                val tokens = env.value.asInstanceOf[js.Array[js.Dynamic]]

                assertTrue(
                    hasEnvelopeShape(env.asInstanceOf[js.Object]),
                    env.ok.asInstanceOf[Boolean],
                    tokens.length == 4,
                    tokens(0).kind.asInstanceOf[String] == "Keyword",
                    tokens(0).lexeme.asInstanceOf[String] == "module",
                    tokens(0).start.asInstanceOf[Int] == 0,
                    tokens(0).end.asInstanceOf[Int] == 6,
                    tokens(3).kind.asInstanceOf[String] == "StringLiteral"
                )
            },
            test("tokenize recovers unknown input as token value plus logs") {
                val env    = dyn(KruegerJs.tokenize("main @ value"))
                val tokens = env.value.asInstanceOf[js.Array[js.Dynamic]]

                assertTrue(
                    env.ok.asInstanceOf[Boolean],
                    tokens.exists(_.kind.asInstanceOf[String] == "Unknown"),
                    arrayLen(env.logs) >= 1
                )
            }
        ),
        suite("runQuery over parsed CST + query (REQ-webappwasm-001)")(
            test("valid source + valid query returns ok=true and value is a JS array") {
                val cstEnv = dyn(KruegerJs.parseCst(validSource))
                val qEnv   = dyn(KruegerJs.parseQuery(validQuery))
                val env    = KruegerJs.runQuery(qEnv.value, cstEnv.value)
                val d      = dyn(env)
                assertTrue(
                    hasEnvelopeShape(env),
                    d.ok.asInstanceOf[Boolean] == true,
                    js.Array.isArray(d.value),
                    // the simple source defines `x = 1`, so at least one
                    // CstValueDeclaration match is expected.
                    arrayLen(d.value) >= 1
                )
            },
            test("query that matches zero nodes returns an empty array, ok=true") {
                val cstEnv = dyn(KruegerJs.parseCst(validSource))
                val qEnv   = dyn(KruegerJs.parseQuery("(nonexistent_node) @x"))
                val env    = KruegerJs.runQuery(qEnv.value, cstEnv.value)
                val d      = dyn(env)
                assertTrue(
                    d.ok.asInstanceOf[Boolean] == true,
                    js.Array.isArray(d.value),
                    arrayLen(d.value) == 0
                )
            }
        ),
        suite("supported WebGC backend routing")(
            test("parseCst preserves the public envelope shape through the supported WebGC backend") {
                val backend = BackendLoader.current()
                val env     = KruegerJs.parseCst(validSource)
                val d       = dyn(env)
                assertTrue(
                    backend.id == "webgc",
                    d.hasOwnProperty("ok").asInstanceOf[Boolean],
                    d.hasOwnProperty("value").asInstanceOf[Boolean],
                    d.hasOwnProperty("logs").asInstanceOf[Boolean],
                    d.hasOwnProperty("errors").asInstanceOf[Boolean]
                )
            },
            test("runQuery returns results through the supported WebGC backend") {
                val backend = BackendLoader.current()
                val cstEnv  = dyn(KruegerJs.parseCst(validSource))
                val qEnv    = dyn(KruegerJs.parseQuery(validQuery))
                val env     = dyn(KruegerJs.runQuery(qEnv.value, cstEnv.value))
                assertTrue(
                    backend.id == "webgc",
                    env.ok.asInstanceOf[Boolean],
                    js.Array.isArray(env.value)
                )
            }
        ),
        suite("Scala.js compiler API acceptance parity")(
            test("valid parseCst source matches the JVM and Chicory acceptance scenario") {
                val env = dyn(KruegerJs.parseCst(CompilerApiAcceptanceCases.validParseCst.source))
                assertTrue(
                    hasEnvelopeShape(env.asInstanceOf[js.Object]),
                    env.ok.asInstanceOf[Boolean],
                    !js.isUndefined(env.value) && env.value != null,
                    jsString(env.value).contains(CompilerApiAcceptanceCases.validParseCst.expectedValueFragment)
                )
            },
            test("malformed parseCst source matches the JVM and Chicory error scenario") {
                val env    = dyn(KruegerJs.parseCst(CompilerApiAcceptanceCases.malformedParseCst.source))
                val errors = env.errors.asInstanceOf[js.Array[js.Dynamic]]
                assertTrue(
                    hasEnvelopeShape(env.asInstanceOf[js.Object]),
                    !env.ok.asInstanceOf[Boolean],
                    errors.length >= 1,
                    errors(0).phase.asInstanceOf[String] == CompilerApiAcceptanceCases.malformedParseCst.expectedPhase,
                    errors(0).message.asInstanceOf[String].contains(
                        CompilerApiAcceptanceCases.malformedParseCst.expectedMessageFragment
                    )
                )
            },
            test("repeated parseCst source matches the JVM and Chicory determinism scenario") {
                val a = dyn(KruegerJs.parseCst(CompilerApiAcceptanceCases.validParseCst.source))
                val b = dyn(KruegerJs.parseCst(CompilerApiAcceptanceCases.validParseCst.source))
                assertTrue(
                    a.ok.asInstanceOf[Boolean] == b.ok.asInstanceOf[Boolean],
                    arrayLen(a.errors) == arrayLen(b.errors),
                    arrayLen(a.logs) == arrayLen(b.logs),
                    jsString(a.value) == jsString(b.value)
                )
            }
        ),
        suite("determinism (REQ-webappwasm-001 tail)")(
            test("repeated parseCst calls return envelopes with the same ok flag and error count") {
                val a = dyn(KruegerJs.parseCst(validSource))
                val b = dyn(KruegerJs.parseCst(validSource))
                assertTrue(
                    a.ok.asInstanceOf[Boolean] == b.ok.asInstanceOf[Boolean],
                    arrayLen(a.errors) == arrayLen(b.errors),
                    arrayLen(a.logs) == arrayLen(b.logs)
                )
            }
        )
    )

    private def jsString(value: js.Any): String =
        js.Dynamic.global.String(value).asInstanceOf[String]
