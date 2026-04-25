package io.eleven19.krueger.webapp.monaco

import zio.test.*

import io.eleven19.krueger.lexer.ElmTokenKind

object ElmLanguageSpec extends ZIOSpecDefault:

    def spec = suite("ElmLanguage")(
        test("token spans are sourced from the shared core tokenizer") {
            val spans = ElmLanguage.tokenSpans("""module Main exposing (main = "hi")""")

            assertTrue(
                spans.map(span => (span.startIndex, span.scope)) == Vector(
                    (0, "keyword"),
                    (7, "type.identifier"),
                    (12, "keyword"),
                    (21, "delimiter"),
                    (22, "identifier"),
                    (27, "operator"),
                    (29, "string"),
                    (33, "delimiter")
                )
            )
        },
        test("unknown recovered tokens become invalid scopes for Monaco") {
            val spans = ElmLanguage.tokenSpans("main @ value")

            assertTrue(spans.exists(span => span.startIndex == 5 && span.scope == "invalid"))
        },
        test("scope mapping covers every shared token kind") {
            assertTrue(
                ElmTokenKind.values.forall(kind => ElmLanguage.scopeFor(kind).nonEmpty)
            )
        },
        test("language id is the canonical Elm dialect identifier") {
            assertTrue(ElmLanguage.id == "elm")
        }
    )
