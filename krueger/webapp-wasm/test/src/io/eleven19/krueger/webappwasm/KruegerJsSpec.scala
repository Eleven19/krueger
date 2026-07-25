package io.eleven19.krueger.webappwasm

import scala.scalajs.js as sjs

import kyo.test.*

/** Contract spec for the `@JSExportTopLevel("Krueger")` facade.
  *
  * The spec intentionally pins the plain-JS envelope shape that SvelteKit consumers depend on per
  * REQ-webappwasm-001..003:
  *
  * `{ ok: boolean, value: any | null, logs: string[], errors: ErrorPojo[] }`
  *
  * All assertions are made through `js.Dynamic` so the facade is exercised exactly as JavaScript callers would see it.
  * No access to Scala-typed internals is allowed in the assertions — that is the whole point of the envelope.
  */
class KruegerJsSpec extends Test[Any]:

    private val validSource =
        """module M exposing (..)
          |
          |x = 1
          |""".stripMargin

    private val malformedSource = "module M exposing (..)\n\nx ="

    private val validQuery = "(CstValueDeclaration) @v"

    private def dyn(o: sjs.Object): sjs.Dynamic = o.asInstanceOf[sjs.Dynamic]

    private def arrayLen(arr: sjs.Any): Int = arr.asInstanceOf[sjs.Array[sjs.Any]].length

    private def hasEnvelopeShape(env: sjs.Object): Boolean =
        val d     = dyn(env)
        val hasOk = sjs.typeOf(d.ok) == "boolean"
        val hasLogs = d.logs.asInstanceOf[sjs.Any] match
            case arr if sjs.Array.isArray(arr) => true
            case _                             => false
        val hasErrors = d.errors.asInstanceOf[sjs.Any] match
            case arr if sjs.Array.isArray(arr) => true
            case _                             => false
        // `value` may be absent, null, or any value — we only require the
        // OTHER three fields to be present with the right types.
        hasOk && hasLogs && hasErrors

    "KruegerJs (WASM FFI facade)" - {
        "envelope shape (REQ-webappwasm-001)" - {
            "parseCst returns { ok, value?, logs, errors } with ok=true on valid source" in {
                val env = KruegerJs.parseCst(validSource)
                val d   = dyn(env)
                assert(hasEnvelopeShape(env))
                assert(d.ok.asInstanceOf[Boolean])
                assert(arrayLen(d.errors) == 0)
                // value must be defined (truthy) on success — we don't
                // inspect its internals because parseCst's value is an
                // opaque handle.
                assert(!sjs.isUndefined(d.value) && d.value != null)
            }
            "parseQuery returns an envelope with ok=true on valid query" in {
                val env = KruegerJs.parseQuery(validQuery)
                val d   = dyn(env)
                assert(hasEnvelopeShape(env))
                assert(d.ok.asInstanceOf[Boolean])
                assert(arrayLen(d.errors) == 0)
            }
            "parseCstUnist returns ok=true with a plain JS unist root and childCount matching children.length" in {
                val env      = KruegerJs.parseCstUnist(validSource)
                val d        = dyn(env)
                val root     = d.value.asInstanceOf[sjs.Dynamic]
                val children = root.children.asInstanceOf[sjs.Any]
                assert(hasEnvelopeShape(env))
                assert(d.ok.asInstanceOf[Boolean])
                assert(arrayLen(d.errors) == 0)
                assert(root.`type`.asInstanceOf[String] == "CstModule")
                assert(sjs.Array.isArray(children))
                assert(root.data.childCount.asInstanceOf[Int] == arrayLen(children))
            }
            "parseAstUnist returns ok=true with a plain JS unist Module root and JS-array children" in {
                val env      = KruegerJs.parseAstUnist(validSource)
                val d        = dyn(env)
                val root     = d.value.asInstanceOf[sjs.Dynamic]
                val children = root.children.asInstanceOf[sjs.Any]
                assert(hasEnvelopeShape(env))
                assert(d.ok.asInstanceOf[Boolean])
                assert(arrayLen(d.errors) == 0)
                assert(root.`type`.asInstanceOf[String] == "Module")
                assert(sjs.Array.isArray(children))
            }
            "prettyQuery returns a non-empty canonical string for a parsed query" in {
                val parseEnv = KruegerJs.parseQuery(validQuery)
                val q        = dyn(parseEnv).value.asInstanceOf[sjs.Any]
                val pretty   = KruegerJs.prettyQuery(q)
                assert(pretty.nonEmpty)
            }
        }
        "error envelope (REQ-webappwasm-002)" - {
            "parseCst on malformed source returns ok=false with errors; value is null" in {
                val env = KruegerJs.parseCst(malformedSource)
                val d   = dyn(env)
                assert(hasEnvelopeShape(env))
                assert(!d.ok.asInstanceOf[Boolean])
                assert(arrayLen(d.errors) >= 1)
                assert(d.value == null)
            }
            "parseCstUnist on malformed source preserves the error envelope with value=null" in {
                val env = KruegerJs.parseCstUnist(malformedSource)
                val d   = dyn(env)
                assert(hasEnvelopeShape(env))
                assert(!d.ok.asInstanceOf[Boolean])
                assert(arrayLen(d.errors) >= 1)
                assert(d.value == null)
            }
            "each serialized error carries a non-empty message field" in {
                val env  = KruegerJs.parseCst(malformedSource)
                val errs = dyn(env).errors.asInstanceOf[sjs.Array[sjs.Dynamic]]
                val hasMsg = (0 until errs.length).forall { i =>
                    val msg = errs(i).message
                    sjs.typeOf(msg) == "string" && msg.asInstanceOf[String].nonEmpty
                }
                assert(hasMsg)
            }
            "parseQuery on malformed query returns ok=false with at least one error" in {
                val env = KruegerJs.parseQuery("(unbalanced")
                val d   = dyn(env)
                assert(!d.ok.asInstanceOf[Boolean])
                assert(arrayLen(d.errors) >= 1)
                assert(d.value == null)
            }
        }
        "edge cases" - {
            "empty source still produces a well-formed envelope (shape is uniform)" in {
                val env = KruegerJs.parseCst("")
                assert(hasEnvelopeShape(env))
            }
            "empty query still produces a well-formed envelope (shape is uniform)" in {
                val env = KruegerJs.parseQuery("")
                assert(hasEnvelopeShape(env))
            }
        }
        "shared tokenizer facade" - {
            "tokenize returns plain JS token POJOs with spans and kinds" in {
                val env    = dyn(KruegerJs.tokenize("""module Main = "hi""""))
                val tokens = env.value.asInstanceOf[sjs.Array[sjs.Dynamic]]

                assert(hasEnvelopeShape(env.asInstanceOf[sjs.Object]))
                assert(env.ok.asInstanceOf[Boolean])
                assert(tokens.length == 4)
                assert(tokens(0).kind.asInstanceOf[String] == "Keyword")
                assert(tokens(0).lexeme.asInstanceOf[String] == "module")
                assert(tokens(0).start.asInstanceOf[Int] == 0)
                assert(tokens(0).end.asInstanceOf[Int] == 6)
                assert(tokens(3).kind.asInstanceOf[String] == "StringLiteral")
            }
            "tokenize recovers unknown input as token value plus logs" in {
                val env    = dyn(KruegerJs.tokenize("main @ value"))
                val tokens = env.value.asInstanceOf[sjs.Array[sjs.Dynamic]]

                assert(env.ok.asInstanceOf[Boolean])
                assert(tokens.exists(_.kind.asInstanceOf[String] == "Unknown"))
                assert(arrayLen(env.logs) >= 1)
            }
        }
        "runQuery over parsed CST + query (REQ-webappwasm-001)" - {
            "valid source + valid query returns ok=true and value is a JS array" in {
                val cstEnv = dyn(KruegerJs.parseCst(validSource))
                val qEnv   = dyn(KruegerJs.parseQuery(validQuery))
                val env    = KruegerJs.runQuery(qEnv.value, cstEnv.value)
                val d      = dyn(env)
                assert(hasEnvelopeShape(env))
                assert(d.ok.asInstanceOf[Boolean])
                assert(sjs.Array.isArray(d.value))
                // the simple source defines `x = 1`, so at least one
                // CstValueDeclaration match is expected.
                assert(arrayLen(d.value) >= 1)
            }
            "query that matches zero nodes returns an empty array, ok=true" in {
                val cstEnv = dyn(KruegerJs.parseCst(validSource))
                val qEnv   = dyn(KruegerJs.parseQuery("(nonexistent_node) @x"))
                val env    = KruegerJs.runQuery(qEnv.value, cstEnv.value)
                val d      = dyn(env)
                assert(d.ok.asInstanceOf[Boolean])
                assert(sjs.Array.isArray(d.value))
                assert(arrayLen(d.value) == 0)
            }
        }
        "LinkedCompilerBackend routing" - {
            "parseCst preserves the public envelope shape through the link-target compiler" in {
                val env = KruegerJs.parseCst(validSource)
                val d   = dyn(env)
                assert(d.hasOwnProperty("ok").asInstanceOf[Boolean])
                assert(d.hasOwnProperty("value").asInstanceOf[Boolean])
                assert(d.hasOwnProperty("logs").asInstanceOf[Boolean])
                assert(d.hasOwnProperty("errors").asInstanceOf[Boolean])
            }
            "runQuery returns results through the link-target compiler" in {
                val cstEnv = dyn(KruegerJs.parseCst(validSource))
                val qEnv   = dyn(KruegerJs.parseQuery(validQuery))
                val env    = dyn(KruegerJs.runQuery(qEnv.value, cstEnv.value))
                assert(env.ok.asInstanceOf[Boolean])
                assert(sjs.Array.isArray(env.value))
            }
        }
        "Scala.js compiler API acceptance parity" - {
            "valid parseCst source matches the JVM and Chicory acceptance scenario" in {
                val env = dyn(KruegerJs.parseCst(CompilerApiAcceptanceCases.validParseCst.source))
                assert(hasEnvelopeShape(env.asInstanceOf[sjs.Object]))
                assert(env.ok.asInstanceOf[Boolean])
                assert(!sjs.isUndefined(env.value) && env.value != null)
                assert(jsString(env.value).contains(CompilerApiAcceptanceCases.validParseCst.expectedValueFragment))
            }
            "malformed parseCst source matches the JVM and Chicory error scenario" in {
                val env          = dyn(KruegerJs.parseCst(CompilerApiAcceptanceCases.malformedParseCst.source))
                val errors       = env.errors.asInstanceOf[sjs.Array[sjs.Dynamic]]
                val error        = errors(0)
                val contextLines = error.contextLines.asInstanceOf[sjs.Array[sjs.Dynamic]]
                assert(hasEnvelopeShape(env.asInstanceOf[sjs.Object]))
                assert(!env.ok.asInstanceOf[Boolean])
                assert(errors.length >= 1)
                assert(
                    errors(0).phase.asInstanceOf[String] == CompilerApiAcceptanceCases.malformedParseCst.expectedPhase
                )
                assert(
                    errors(0).message
                        .asInstanceOf[String]
                        .contains(CompilerApiAcceptanceCases.malformedParseCst.expectedMessageFragment)
                )
                assert(contextLines.length >= 1)
                assert(
                    (0 until contextLines.length).exists(index => contextLines(index).isErrorLine.asInstanceOf[Boolean])
                )
            }
            "repeated parseCst source matches the JVM and Chicory determinism scenario" in {
                val a = dyn(KruegerJs.parseCst(CompilerApiAcceptanceCases.validParseCst.source))
                val b = dyn(KruegerJs.parseCst(CompilerApiAcceptanceCases.validParseCst.source))
                assert(a.ok.asInstanceOf[Boolean] == b.ok.asInstanceOf[Boolean])
                assert(arrayLen(a.errors) == arrayLen(b.errors))
                assert(arrayLen(a.logs) == arrayLen(b.logs))
                assert(jsString(a.value) == jsString(b.value))
            }
        }
        "determinism (REQ-webappwasm-001 tail)" - {
            "repeated parseCst calls return envelopes with the same ok flag and error count" in {
                val a = dyn(KruegerJs.parseCst(validSource))
                val b = dyn(KruegerJs.parseCst(validSource))
                assert(a.ok.asInstanceOf[Boolean] == b.ok.asInstanceOf[Boolean])
                assert(arrayLen(a.errors) == arrayLen(b.errors))
                assert(arrayLen(a.logs) == arrayLen(b.logs))
            }
        }
    }

    private def jsString(value: sjs.Any): String =
        sjs.Dynamic.global.String(value).asInstanceOf[String]
