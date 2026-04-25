package io.eleven19.krueger.webapp.monaco

import scala.scalajs.js

import io.eleven19.krueger.lexer.ElmTokenKind
import io.eleven19.krueger.lexer.ElmTokenizer

/** Monaco language descriptor for the Elm dialect Krueger parses.
  *
  * Highlighting is sourced from the shared core tokenizer so parser-facing and UI-facing token categories stay aligned.
  *
  * All Monaco touch-points live behind [[register]], keeping the rest of this object callable from Node (where
  * `monaco-editor` is not resolvable) so tests can assert on the pure token adapter.
  */
object ElmLanguage:

    val id: String = "elm"

    final case class TokenSpan(startIndex: Int, scope: String) derives CanEqual

    def scopeFor(kind: ElmTokenKind): String = kind match
        case ElmTokenKind.Keyword         => "keyword"
        case ElmTokenKind.LowerIdentifier => "identifier"
        case ElmTokenKind.UpperIdentifier => "type.identifier"
        case ElmTokenKind.Operator        => "operator"
        case ElmTokenKind.Number          => "number"
        case ElmTokenKind.StringLiteral   => "string"
        case ElmTokenKind.CharLiteral     => "string"
        case ElmTokenKind.Comment         => "comment"
        case ElmTokenKind.Whitespace      => "white"
        case ElmTokenKind.Newline         => "white"
        case ElmTokenKind.Punctuation     => "delimiter"
        case ElmTokenKind.Unknown         => "invalid"

    def tokenSpans(line: String): Vector[TokenSpan] =
        val result = ElmTokenizer.run(line)
        result.value.fold(
            _ => Vector(TokenSpan(0, "invalid")),
            tokens => tokens.filterNot(_.kind == ElmTokenKind.Newline).map(t => TokenSpan(t.start, scopeFor(t.kind)))
        )

    lazy val tokensProvider: MonacoFacade.TokensProvider = new MonacoFacade.TokensProvider:
        private lazy val state: MonacoFacade.IState =
            js.Dynamic.literal(
                clone = () => state,
                equals = (_: MonacoFacade.IState) => true
            ).asInstanceOf[MonacoFacade.IState]

        def getInitialState(): MonacoFacade.IState = state

        def tokenize(line: String, previousState: MonacoFacade.IState): MonacoFacade.ILineTokens =
            js.Dynamic.literal(
                endState = state,
                tokens = js.Array(tokenSpans(line).map(span =>
                    js.Dynamic.literal(startIndex = span.startIndex, scopes = span.scope).asInstanceOf[MonacoFacade.IToken]
                )*)
            ).asInstanceOf[MonacoFacade.ILineTokens]

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
            monaco.languages.setTokensProvider(id, tokensProvider)
