package io.eleven19.krueger.trees.query

import parsley.Parsley
import parsley.Parsley.{atomic, eof, many}
import parsley.Result
import parsley.character.{char, digit, letter, satisfy, stringOfMany}
import parsley.combinator.option
import parsley.errors.combinator.ErrorMethods
import parsley.syntax.character.{charLift, stringLift}

import io.eleven19.krueger.trees.CaptureName
import io.eleven19.krueger.trees.FieldName
import io.eleven19.krueger.trees.NodeTypeName

/** Parses tree-sitter-style query strings into the v1 query AST.
  *
  * Supported v1 syntax:
  *   - Node pattern: `(NodeType)`
  *   - Field pattern: `(NodeType field_name: (ChildType))`
  *   - Capture: `@name` appended to any pattern
  *   - Wildcard: `_` or `(_)`, optionally followed by `@name`
  *   - Predicate clauses at top level: `(#eq? @a @b)` or `(#match? @x "regex")`
  *   - Line comments: `;; ...` to end of line
  *   - Flexible whitespace between tokens
  *
  * Alternation, quantifiers, anchors, and negation are deferred to the v2 epic.
  */
object QueryParser:

    /** Parse a query without validating node-type names. */
    def parse(source: String): Result[String, Query] =
        queryParser.parse(source)

    /** Parse a query and validate every node-type name against `knownTypes`.
      *
      * Unknown names produce a Failure with the offending identifiers listed alphabetically.
      */
    def parse(source: String, knownTypes: Set[String]): Result[String, Query] =
        parse(source) match
            case parsley.Success(q) =>
                val unknown = gatherNodeTypes(q.root).map(NodeTypeName.unwrap).diff(knownTypes)
                if unknown.isEmpty then parsley.Success(q)
                else parsley.Failure(s"Unknown node type(s): ${unknown.toList.sorted.mkString(", ")}")
            case f: parsley.Failure[String] => f

    private def gatherNodeTypes(p: Pattern): Set[NodeTypeName] = p match
        case NodePattern(nt, fields, _) =>
            fields.foldLeft(Set(nt))((acc, fp) => acc ++ gatherNodeTypes(fp.pattern))
        case _: WildcardPattern => Set.empty

    // --- Trivia --------------------------------------------------------------

    private val lineComment: Parsley[Unit] =
        atomic(";;") *> many(satisfy(_ != '\n')).as(())

    private val whitespaceChar: Parsley[Unit] =
        satisfy(_.isWhitespace).as(())

    private val skipTrivia: Parsley[Unit] =
        many(whitespaceChar | lineComment).as(())

    private def tok[A](p: Parsley[A]): Parsley[A] = p <* skipTrivia

    // --- Lexemes -------------------------------------------------------------

    private val identStart: Parsley[Char] = letter | char('_')
    private val identCont: Parsley[Char]  = letter | digit | char('_')

    /** Raw identifier text. The grammar guarantees non-empty, non-whitespace, starting with a letter or underscore —
      * exactly what [[NodeTypeName]], [[FieldName]], and [[CaptureName]] require — so the `unsafeMake` wrappers below
      * are safe by construction.
      */
    private val identifier: Parsley[String] =
        (identStart <~> stringOfMany(identCont)).map { case (h, t) => s"$h$t" }

    private val nodeTypeName: Parsley[NodeTypeName] = identifier.map(NodeTypeName.unsafeMake)
    private val fieldName: Parsley[FieldName]       = identifier.map(FieldName.unsafeMake)
    private val captureName: Parsley[CaptureName]   = identifier.map(CaptureName.unsafeMake)

    private val stringLit: Parsley[String] =
        char('"') *> stringOfMany(satisfy(_ != '"')) <* char('"')

    private val captureTail: Parsley[CaptureName] = char('@') *> captureName

    // --- Patterns ------------------------------------------------------------

    // Lazy forward reference so nested patterns work.
    private lazy val pattern: Parsley[Pattern] =
        wildcardPattern | nodePattern

    private lazy val wildcardPattern: Parsley[Pattern] =
        val parenthesised = atomic(tok('(' <~ skipTrivia) *> tok(char('_')) <* tok(')'))
        val bare          = tok(char('_'))
        (parenthesised | bare) *> option(tok(captureTail)).map(WildcardPattern(_))

    private lazy val nodePattern: Parsley[Pattern] =
        (
            tok('(' <~ skipTrivia)
                *> tok(nodeTypeName)
                <~> many(fieldPattern)
                <~ tok(')')
                <~> option(tok(captureTail))
        ).map { case ((nt, fs), cap) =>
            NodePattern(nt, fs, cap)
        }

    private lazy val fieldPattern: Parsley[FieldPattern] =
        (atomic(tok(fieldName) <~ tok(':')) <~> pattern).map { case (name, p) =>
            FieldPattern(name, p)
        }

    // --- Predicates ----------------------------------------------------------

    private val predicateArg: Parsley[PredicateArg] =
        tok(captureTail).map(CaptureRef(_)) | tok(stringLit).map(StringArg(_))

    private val eqPredicate: Parsley[Predicate] =
        (atomic(tok('(' <~ skipTrivia) *> tok("#eq?")) *> predicateArg <~> predicateArg <~ tok(')'))
            .map { case (l, r) => EqPredicate(l, r) }

    private val regexArg: Parsley[RegexPattern] =
        tok(stringLit).flatMap { raw =>
            RegexPattern.make(raw) match
                case Right(rx) => Parsley.pure(rx)
                case Left(msg) => Parsley.empty.label(msg)
        }

    private val matchPredicate: Parsley[Predicate] =
        (
            atomic(tok('(' <~ skipTrivia) *> tok("#match?"))
                *> predicateArg
                <~> regexArg
                <~ tok(')')
        ).map { case (a, r) => MatchPredicate(a, r) }

    private val predicate: Parsley[Predicate] = eqPredicate | matchPredicate

    // --- Top-level -----------------------------------------------------------

    private val queryParser: Parsley[Query] =
        (skipTrivia *> pattern <~> many(predicate) <~ eof).map { case (p, preds) =>
            Query(p, preds)
        }
