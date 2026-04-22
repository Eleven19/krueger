package io.eleven19.krueger.lexer

import parsley.Parsley
import parsley.Parsley.{atomic, eof, many, some}
import parsley.character.{char, digit, letter, noneOf, satisfy, string, stringOfSome}
import parsley.combinator.option
import parsley.errors.combinator.ErrorMethods
import parsley.token.Lexer
import parsley.token.descriptions.{LexicalDesc, NameDesc, SpaceDesc, SymbolDesc}
import parsley.token.descriptions.text.{EscapeDesc, TextDesc}
import parsley.token.predicate

/** Provides tokenisation primitives for the Elm language dialect.
  *
  * Elm is indentation-sensitive, so newlines are structurally significant. The lexer handles identifiers (lower and
  * upper), operators, keywords, numeric and string literals, and whitespace management.
  */
object ElmLexer:

    // -----------------------------------------------------------------------
    // Token Lexer configuration
    // -----------------------------------------------------------------------

    private val elmKeywords: Set[String] = Set(
        "module", "exposing", "import", "as", "port", "effect",
        "type", "alias",
        "let", "in",
        "if", "then", "else",
        "case", "of",
        "infix", "left", "right", "non",
        "where"
    )

    private val elmOperators: Set[String] = Set(
        "->", "<-", "::", "=", "|", "\\", ".", "..",
        "+", "-", "*", "/", "//", "^",
        "==", "/=", "<", ">", "<=", ">=",
        "&&", "||",
        "++", "<|", "|>", ">>", "<<"
    )

    private val desc: LexicalDesc = LexicalDesc.plain.copy(
        nameDesc = NameDesc.plain.copy(
            identifierStart = predicate.Basic(c => c.isLetter || c == '_'),
            identifierLetter = predicate.Basic(c => c.isLetterOrDigit || c == '_'),
            operatorStart = predicate.Basic(c => "+-*/<>=&|^!~%?:.\\".contains(c)),
            operatorLetter = predicate.Basic(c => "+-*/<>=&|^!~%?:.\\".contains(c))
        ),
        symbolDesc = SymbolDesc.plain.copy(
            hardKeywords = elmKeywords,
            hardOperators = elmOperators
        ),
        spaceDesc = SpaceDesc.plain.copy(
            lineCommentStart = "--",
            multiLineCommentStart = "{-",
            multiLineCommentEnd = "-}",
            multiLineNestedComments = true
        )
    )

    private val lexer: Lexer = new Lexer(desc)

    // -----------------------------------------------------------------------
    // Identifiers
    // -----------------------------------------------------------------------

    /** A lower-case identifier: starts with a lowercase letter or underscore. */
    val lowerIdentifier: Parsley[String] =
        lexer.lexeme.names.identifier.filter(s => s.head.isLower || s.head == '_')

    /** An upper-case identifier: starts with an uppercase letter. */
    val upperIdentifier: Parsley[String] =
        lexer.lexeme.names.identifier.filter(_.head.isUpper)

    /** Any identifier (lower or upper). */
    val identifier: Parsley[String] = lexer.lexeme.names.identifier

    /** A user-defined operator. */
    val operator: Parsley[String] = lexer.lexeme.names.userDefinedOperator

    // -----------------------------------------------------------------------
    // Keywords and symbols
    // -----------------------------------------------------------------------

    /** Parse a specific keyword. */
    def keyword(kw: String): Parsley[Unit] = lexer.lexeme.symbol(kw)

    /** Parse a specific symbol/operator. */
    def symbol(sym: String): Parsley[Unit] = lexer.lexeme.symbol(sym)

    // -----------------------------------------------------------------------
    // Literals
    // -----------------------------------------------------------------------

    /** An integer literal. */
    val intLiteral: Parsley[Long] = lexer.lexeme.numeric.integer.decimal64

    /** A floating-point literal. */
    val floatLiteral: Parsley[Double] = lexer.lexeme.numeric.floating.doubleDecimal

    // -----------------------------------------------------------------------
    // Whitespace and structure
    // -----------------------------------------------------------------------

    /** Fully wraps a parser: skips leading whitespace and asserts end-of-input. */
    def fully[A](p: Parsley[A]): Parsley[A] = lexer.fully(p)

    /** Matches a single space or tab (horizontal whitespace). */
    val hspace: Parsley[Char] = satisfy(c => c == ' ' || c == '\t')

    /** Skips zero or more horizontal whitespace characters. */
    val hspaces: Parsley[Unit] = many(hspace).void

    /** Matches end-of-line or end-of-input. */
    val eolOrEof: Parsley[Unit] = (char('\n').void | (string("\r\n").void) | eof).label("end of line")

    /** Matches a newline character. */
    val newline: Parsley[Char] = char('\n')

    /** Parse content wrapped in parentheses. */
    def parens[A](p: Parsley[A]): Parsley[A] = lexer.lexeme.enclosing.parens(p)

    /** Parse content wrapped in square brackets. */
    def brackets[A](p: Parsley[A]): Parsley[A] = lexer.lexeme.enclosing.brackets(p)

    /** Parse content wrapped in curly braces. */
    def braces[A](p: Parsley[A]): Parsley[A] = lexer.lexeme.enclosing.braces(p)

    /** Parse a comma-separated list. */
    def commaSep[A](p: Parsley[A]): Parsley[List[A]] =
        lexer.lexeme.separators.commaSep(p)

    /** Parse a comma-separated list with at least one element. */
    def commaSep1[A](p: Parsley[A]): Parsley[List[A]] =
        lexer.lexeme.separators.commaSep1(p)
