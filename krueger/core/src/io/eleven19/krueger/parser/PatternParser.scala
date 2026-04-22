package io.eleven19.krueger.parser

import parsley.Parsley
import parsley.Parsley.{atomic, many, some}
import parsley.combinator.option
import parsley.position.pos

import io.eleven19.krueger.Span
import io.eleven19.krueger.cst.*
import io.eleven19.krueger.lexer.ElmLexer.*

/** Parser for Elm patterns used in function arguments, case branches, and let bindings. */
object PatternParser:

    private def mkSpan(start: (Int, Int), end: (Int, Int)): Span =
        Span(start._1, end._1 - start._1)

    // -----------------------------------------------------------------------
    // Atomic patterns
    // -----------------------------------------------------------------------

    private val anythingPat: Parsley[CstPattern] =
        (pos <~> symbol("_") <~> pos).map { case ((s, _), e) =>
            CstAnythingPattern()(mkSpan(s, e))
        }

    private val intPat: Parsley[CstPattern] =
        (pos <~> intLiteral <~> pos).map { case ((s, v), e) =>
            CstIntPattern(v)(mkSpan(s, e))
        }

    private val floatPat: Parsley[CstPattern] =
        (pos <~> floatLiteral <~> pos).map { case ((s, v), e) =>
            CstFloatPattern(v)(mkSpan(s, e))
        }

    private val variablePat: Parsley[CstPattern] =
        (pos <~> ModuleParser.lowerName <~> pos).map { case ((s, n), e) =>
            CstVariablePattern(n)(mkSpan(s, e))
        }

    private val unitPat: Parsley[CstPattern] =
        atomic((pos <~> parens(Parsley.pure(())) <~> pos).map { case ((s, _), e) =>
            CstUnitPattern()(mkSpan(s, e))
        })

    private val tuplePat: Parsley[CstPattern] =
        (pos <~> parens(pattern <~> some(symbol(",") *> pattern)) <~> pos).map { case ((s, (first, rest)), e) =>
            CstTuplePattern(first :: rest)(mkSpan(s, e))
        }

    private val listPat: Parsley[CstPattern] =
        (pos <~> brackets(commaSep(pattern)) <~> pos).map { case ((s, elems), e) =>
            CstListPattern(elems)(mkSpan(s, e))
        }

    private val recordPat: Parsley[CstPattern] =
        (pos <~> braces(commaSep1(ModuleParser.lowerName)) <~> pos).map { case ((s, fields), e) =>
            CstRecordPattern(fields)(mkSpan(s, e))
        }

    private val constructorPat: Parsley[CstPattern] =
        (pos <~> ModuleParser.qualifiedName <~> many(atomPattern) <~> pos).map { case (((s, name), args), e) =>
            CstConstructorPattern(name, args)(mkSpan(s, e))
        }

    private val parenthesizedPat: Parsley[CstPattern] =
        (pos <~> parens(pattern) <~> pos).map { case ((s, p), e) =>
            CstParenthesizedPattern(p)(mkSpan(s, e))
        }

    /** An atomic pattern (no cons or as). */
    val atomPattern: Parsley[CstPattern] =
        anythingPat
            | atomic(floatPat)
            | intPat
            | unitPat
            | atomic(tuplePat)
            | listPat
            | recordPat
            | constructorPat
            | variablePat
            | parenthesizedPat

    /** A pattern with optional `as` alias. */
    lazy val pattern: Parsley[CstPattern] =
        (pos <~> atomPattern <~> option(keyword("as") *> ModuleParser.lowerName) <~> pos).map {
            case (((s, pat), Some(alias)), e) => CstAsPattern(pat, alias)(mkSpan(s, e))
            case (((_, pat), None), _)        => pat
        }
