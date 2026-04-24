package io.eleven19.krueger.webapp.monaco

import zio.test.*

import io.eleven19.krueger.lexer.ElmLexer

/** Alignment contract: the Monarch tokenizer's keyword/operator list must stay in lock-step with the parsley-driven
  * Krueger lexer. Any divergence here means the editor highlights (or fails to highlight) a token that the real parser
  * treats differently — surface that at test time, not in production.
  */
object ElmLanguageSpec extends ZIOSpecDefault:

    def spec = suite("ElmLanguage")(
        test("keywords set matches ElmLexer.keywords exactly") {
            assertTrue(ElmLanguage.keywords == ElmLexer.keywords)
        },
        test("operators set matches ElmLexer.operators exactly") {
            assertTrue(ElmLanguage.operators == ElmLexer.operators)
        },
        test("spot-check: essential Elm keywords are present") {
            assertTrue(
                ElmLanguage.keywords.contains("module"),
                ElmLanguage.keywords.contains("exposing"),
                ElmLanguage.keywords.contains("import"),
                ElmLanguage.keywords.contains("case"),
                ElmLanguage.keywords.contains("of"),
                ElmLanguage.keywords.contains("let"),
                ElmLanguage.keywords.contains("in"),
                ElmLanguage.keywords.contains("type")
            )
        },
        test("spot-check: core operators are present") {
            assertTrue(
                ElmLanguage.operators.contains("->"),
                ElmLanguage.operators.contains("=="),
                ElmLanguage.operators.contains("|>"),
                ElmLanguage.operators.contains("<|"),
                ElmLanguage.operators.contains("::")
            )
        },
        test("language id is the canonical Elm dialect identifier") {
            assertTrue(ElmLanguage.id == "elm")
        }
    )
