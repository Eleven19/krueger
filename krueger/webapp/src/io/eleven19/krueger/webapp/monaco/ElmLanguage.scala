package io.eleven19.krueger.webapp.monaco

import scala.scalajs.js

import io.eleven19.krueger.lexer.ElmLexer

/** Monaco language descriptor for the Elm dialect Krueger parses.
  *
  * The keyword and operator sets are sourced directly from [[ElmLexer]] so a parser change automatically updates the
  * editor highlighting. [[ElmLanguageSpec]] pins that equality as a test-time contract — any drift fails CI rather than
  * shipping mismatched highlights.
  *
  * All Monaco touch-points live behind [[register]], keeping the rest of this object callable from Node (where
  * `monaco-editor` is not resolvable) so tests can assert on the pure data.
  */
object ElmLanguage:

    val id: String = "elm"

    val keywords: Set[String]  = ElmLexer.keywords
    val operators: Set[String] = ElmLexer.operators

    /** Monarch tokenizer definition. Built lazily as a plain JS object so tests can inspect shape without running
      * Monaco. Rules are intentionally simple — we want sensible highlighting, not a full parser.
      */
    lazy val monarchDefinition: js.Object =
        val operatorChars = "+-*/<>=&|^!~%?:.\\\\"
        val defaultCases = js.Dictionary[js.Any](
            "@keywords" -> "keyword",
            "@default"  -> "identifier"
        )
        val ops = js.Array(operators.toSeq.sortBy(op => -op.length)*)
        val kws = js.Array(keywords.toSeq.sorted*)

        val tokenizerRoot = js.Array[js.Any](
            js.Array[js.Any](js.RegExp("^[a-z][\\w]*"), js.Dictionary("cases" -> defaultCases)),
            js.Array[js.Any](js.RegExp("^[A-Z][\\w]*"), "type"),
            js.Array[js.Any](js.RegExp("^\\d+(\\.\\d+)?"), "number"),
            js.Array[js.Any](js.RegExp("^\"([^\"\\\\]|\\\\.)*\""), "string"),
            js.Array[js.Any](js.RegExp("^--.*$"), "comment"),
            js.Array[js.Any](js.RegExp(s"^[$operatorChars]+"), "operator")
        )

        js.Dynamic.literal(
            keywords = kws,
            operators = ops,
            tokenizer = js.Dynamic.literal(root = tokenizerRoot)
        )

    /** Idempotently register Elm with Monaco. Safe to call twice — the second call is a no-op because Monaco's
      * `getLanguages` will already list the id.
      *
      * @note
      *   Must only be called in a browser context. Unit tests must not invoke this method; they assert on the pure data
      *   above.
      */
    def register(): Unit =
        val monaco       = MonacoFacade.Monaco
        val alreadyThere = monaco.languages.getLanguages().exists(_.id == id)
        if !alreadyThere then
            val ext = js.Dynamic.literal(id = id).asInstanceOf[MonacoFacade.LanguageExtensionPoint]
            monaco.languages.register(ext)
            monaco.languages.setMonarchTokensProvider(id, monarchDefinition)
