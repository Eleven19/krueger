package io.eleven19.krueger.lexer

import io.eleven19.krueger.compiler.CompileError
import io.eleven19.krueger.compiler.Span
import io.eleven19.krueger.trees.query.QueryLogic

final case class ElmTokenizerConfig(
    includeTrivia: Boolean,
    recoverUnknown: Boolean
) derives CanEqual

final case class ElmTokenizerCtx(
    config: ElmTokenizerConfig
) derives CanEqual

enum ElmTokenKind derives CanEqual:
    case Keyword, LowerIdentifier, UpperIdentifier, Operator, Number, StringLiteral, CharLiteral, Comment, Whitespace,
        Newline, Punctuation, Unknown

final case class ElmToken(kind: ElmTokenKind, lexeme: String, start: Int, end: Int) derives CanEqual

object ElmTokenizer:
    type TokenizeCtx = ElmTokenizerCtx
    type TokenizeLog = String
    type TokenizeErr = CompileError
    type TokenizeEff[A] = QueryLogic.QueryEffect[TokenizeCtx, TokenizeLog, TokenizeErr, A]
    type TokenizeResult[A] = QueryLogic.Result[TokenizeCtx, TokenizeLog, TokenizeErr, A]

    val defaultConfig: ElmTokenizerConfig = ElmTokenizerConfig(
        includeTrivia = false,
        recoverUnknown = true
    )

    val defaultContext: ElmTokenizerCtx = ElmTokenizerCtx(defaultConfig)

    private val hardOperators: Vector[String] = ElmLexer.operators.toVector.sortBy(op => (-op.length, op))
    private val punctuation: Set[Char] = Set('(', ')', '[', ']', '{', '}', ',', ';')
    private val operatorChars: Set[Char] = "+-*/<>=&|^!~%?:.\\".toSet

    def tokenize(source: String): TokenizeEff[Vector[ElmToken]] =
        val ctx = QueryLogic.readContext[TokenizeCtx, TokenizeLog, TokenizeErr]
        scan(source, ctx.config)

    def run(source: String): TokenizeResult[Vector[ElmToken]] =
        run(source, defaultContext)

    def run(source: String, config: ElmTokenizerConfig): TokenizeResult[Vector[ElmToken]] =
        run(source, ElmTokenizerCtx(config))

    def run(source: String, ctx: ElmTokenizerCtx): TokenizeResult[Vector[ElmToken]] =
        QueryLogic.run[TokenizeCtx, TokenizeLog, TokenizeErr, Vector[ElmToken]](ctx)(tokenize(source))

    private def scan(source: String, config: ElmTokenizerConfig): TokenizeEff[Vector[ElmToken]] =
        val tokens = Vector.newBuilder[ElmToken]
        var index = 0

        def add(kind: ElmTokenKind, start: Int, end: Int): Unit =
            if config.includeTrivia || !isTrivia(kind) then tokens += ElmToken(kind, source.substring(start, end), start, end)

        def recoverUnknown(start: Int): Unit =
            val lexeme = source.substring(start, start + 1)
            val err = CompileError.ParseError(
                phase = "tokenize",
                message = s"Unexpected character '$lexeme'",
                span = Some(Span(start, start + 1))
            )
            if config.recoverUnknown then
                QueryLogic.log[TokenizeCtx, TokenizeLog, TokenizeErr](s"Recovered unknown token '$lexeme' at $start")
                tokens += ElmToken(ElmTokenKind.Unknown, lexeme, start, start + 1)
            else QueryLogic.failFast[TokenizeCtx, TokenizeLog, TokenizeErr](err)

        while index < source.length do
            val start = index
            val ch = source.charAt(index)

            if source.startsWith("\r\n", index) then
                index += 2
                add(ElmTokenKind.Newline, start, index)
            else if ch == '\n' || ch == '\r' then
                index += 1
                add(ElmTokenKind.Newline, start, index)
            else if ch == ' ' || ch == '\t' then
                index = consumeWhile(source, index)(c => c == ' ' || c == '\t')
                add(ElmTokenKind.Whitespace, start, index)
            else if source.startsWith("--", index) then
                index = consumeLineComment(source, index)
                add(ElmTokenKind.Comment, start, index)
            else if source.startsWith("{-", index) then
                index = consumeBlockComment(source, index)
                add(ElmTokenKind.Comment, start, index)
            else if ch == '"' then
                index = consumeQuoted(source, index, '"')
                add(ElmTokenKind.StringLiteral, start, index)
            else if ch == '\'' then
                index = consumeQuoted(source, index, '\'')
                add(ElmTokenKind.CharLiteral, start, index)
            else if ch.isDigit then
                index = consumeNumber(source, index)
                add(ElmTokenKind.Number, start, index)
            else if isIdentifierStart(ch) then
                index = consumeWhile(source, index)(isIdentifierPart)
                val lexeme = source.substring(start, index)
                val kind =
                    if ElmLexer.keywords.contains(lexeme) then ElmTokenKind.Keyword
                    else if lexeme.head.isUpper then ElmTokenKind.UpperIdentifier
                    else ElmTokenKind.LowerIdentifier
                add(kind, start, index)
            else
                hardOperators.find(source.startsWith(_, index)) match
                    case Some(op) =>
                        index += op.length
                        add(ElmTokenKind.Operator, start, index)
                    case None if operatorChars.contains(ch) =>
                        index = consumeWhile(source, index)(operatorChars.contains)
                        add(ElmTokenKind.Operator, start, index)
                    case None if punctuation.contains(ch) =>
                        index += 1
                        add(ElmTokenKind.Punctuation, start, index)
                    case None =>
                        recoverUnknown(start)
                        index += 1

        tokens.result()

    private def isTrivia(kind: ElmTokenKind): Boolean =
        kind == ElmTokenKind.Whitespace || kind == ElmTokenKind.Newline || kind == ElmTokenKind.Comment

    private def isIdentifierStart(ch: Char): Boolean =
        ch.isLetter || ch == '_'

    private def isIdentifierPart(ch: Char): Boolean =
        ch.isLetterOrDigit || ch == '_'

    private def consumeWhile(source: String, index: Int)(p: Char => Boolean): Int =
        var cursor = index
        while cursor < source.length && p(source.charAt(cursor)) do cursor += 1
        cursor

    private def consumeLineComment(source: String, index: Int): Int =
        var cursor = index + 2
        while cursor < source.length && source.charAt(cursor) != '\n' && source.charAt(cursor) != '\r' do cursor += 1
        cursor

    private def consumeBlockComment(source: String, index: Int): Int =
        var cursor = index + 2
        var depth = 1
        while cursor < source.length && depth > 0 do
            if source.startsWith("{-", cursor) then
                depth += 1
                cursor += 2
            else if source.startsWith("-}", cursor) then
                depth -= 1
                cursor += 2
            else cursor += 1
        cursor

    private def consumeQuoted(source: String, index: Int, quote: Char): Int =
        var cursor = index + 1
        var escaped = false
        while cursor < source.length do
            val ch = source.charAt(cursor)
            cursor += 1
            if escaped then escaped = false
            else if ch == '\\' then escaped = true
            else if ch == quote then return cursor
        cursor

    private def consumeNumber(source: String, index: Int): Int =
        var cursor = consumeWhile(source, index)(_.isDigit)
        if cursor + 1 < source.length && source.charAt(cursor) == '.' && source.charAt(cursor + 1).isDigit then
            cursor = consumeWhile(source, cursor + 1)(_.isDigit)
        cursor
