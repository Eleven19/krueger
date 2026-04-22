package io.eleven19.krueger.parser

import parsley.Parsley
import parsley.Parsley.{atomic, many, some}
import parsley.combinator.option
import parsley.position.pos

import io.eleven19.krueger.Span
import io.eleven19.krueger.cst.*
import io.eleven19.krueger.lexer.ElmLexer.*

/** Parser for Elm top-level declarations: value definitions, type aliases, custom types, ports, and infix declarations.
  */
object DeclarationParser:

    private def mkSpan(start: (Int, Int), end: (Int, Int)): Span =
        Span(start._1, end._1 - start._1)

    // -----------------------------------------------------------------------
    // Type expressions
    // -----------------------------------------------------------------------

    private val typeVariable: Parsley[CstTypeExpression] =
        (pos <~> ModuleParser.lowerName <~> pos).map { case ((s, n), e) =>
            CstTypeVariable(n)(mkSpan(s, e))
        }

    private val typeReference: Parsley[CstTypeExpression] =
        (pos <~> ModuleParser.qualifiedName <~> pos).map { case ((s, qn), e) =>
            CstTypeReference(qn)(mkSpan(s, e))
        }

    private val unitType: Parsley[CstTypeExpression] =
        (pos <~> parens(Parsley.pure(())) <~> pos).map { case ((s, _), e) =>
            CstUnitType()(mkSpan(s, e))
        }

    private val tupleType: Parsley[CstTypeExpression] =
        (pos <~> parens(typeExpression <~> some(symbol(",") *> typeExpression)) <~> pos).map {
            case ((s, (first, rest)), e) =>
                CstTupleType(first :: rest)(mkSpan(s, e))
        }

    private val recordFieldType: Parsley[CstRecordFieldType] =
        (pos <~> ModuleParser.lowerName <~> (symbol(":") *> typeExpression) <~> pos).map { case (((s, n), t), e) =>
            CstRecordFieldType(n, t)(mkSpan(s, e))
        }

    private val recordType: Parsley[CstTypeExpression] =
        (pos <~> braces(
            option(atomic(ModuleParser.lowerName <* symbol("|"))) <~> commaSep1(recordFieldType)
        ) <~> pos).map { case ((s, (ext, fields)), e) =>
            CstRecordType(fields, ext)(mkSpan(s, e))
        }

    private val parenthesizedType: Parsley[CstTypeExpression] =
        parens(typeExpression)

    /** An atomic type (not a function type or application). */
    val atomType: Parsley[CstTypeExpression] =
        unitType
            | atomic(tupleType)
            | recordType
            | typeReference
            | typeVariable
            | parenthesizedType

    /** A type with optional type application. */
    val appType: Parsley[CstTypeExpression] =
        (pos <~> atomType <~> many(atomType) <~> pos).map { case (((s, con), args), e) =>
            if args.isEmpty then con
            else CstTypeApplication(con, args)(mkSpan(s, e))
        }

    /** A full type expression including function types (`a -> b`). */
    lazy val typeExpression: Parsley[CstTypeExpression] =
        (pos <~> appType <~> many(symbol("->") *> appType) <~> pos).map { case (((s, first), rest), e) =>
            rest.foldRight(first) { (next, acc) =>
                CstFunctionType(acc, next)(mkSpan(s, e))
            }
        }

    // -----------------------------------------------------------------------
    // Type annotation
    // -----------------------------------------------------------------------

    val typeAnnotation: Parsley[CstTypeAnnotation] =
        (pos <~> ModuleParser.lowerName <~> (symbol(":") *> typeExpression) <~> pos).map { case (((s, n), t), e) =>
            CstTypeAnnotation(n, t)(mkSpan(s, e))
        }

    // -----------------------------------------------------------------------
    // Declarations
    // -----------------------------------------------------------------------

    private val valueDeclaration: Parsley[CstDeclaration] =
        (pos <~> option(atomic(typeAnnotation)) <~>
            ModuleParser.lowerName <~>
            many(PatternParser.atomPattern) <~>
            (symbol("=") *> ExpressionParser.expression) <~> pos).map { case (((((s, ann), name), params), body), e) =>
            CstValueDeclaration(ann, name, params, body)(mkSpan(s, e))
        }

    private val typeAliasDeclaration: Parsley[CstDeclaration] =
        (pos <~> (keyword("type") *> keyword("alias") *> ModuleParser.upperName) <~>
            many(ModuleParser.lowerName) <~>
            (symbol("=") *> typeExpression) <~> pos).map { case ((((s, name), vars), body), e) =>
            CstTypeAliasDeclaration(name, vars, body)(mkSpan(s, e))
        }

    private val constructor: Parsley[CstConstructor] =
        (pos <~> ModuleParser.upperName <~> many(atomType) <~> pos).map { case (((s, name), params), e) =>
            CstConstructor(name, params)(mkSpan(s, e))
        }

    private val customTypeDeclaration: Parsley[CstDeclaration] =
        (pos <~> (keyword("type") *> ModuleParser.upperName) <~>
            many(ModuleParser.lowerName) <~>
            (symbol("=") *> constructor) <~>
            many(symbol("|") *> constructor) <~> pos).map { case (((((s, name), vars), first), rest), e) =>
            CstCustomTypeDeclaration(name, vars, first :: rest)(mkSpan(s, e))
        }

    private val portDeclaration: Parsley[CstDeclaration] =
        (pos <~> (keyword("port") *> ModuleParser.lowerName) <~>
            (symbol(":") *> typeExpression) <~> pos).map { case (((s, name), t), e) =>
            CstPortDeclaration(name, t)(mkSpan(s, e))
        }

    private val associativity: Parsley[Associativity] =
        (keyword("left") *> Parsley.pure(Associativity.Left))
            | (keyword("right") *> Parsley.pure(Associativity.Right))
            | (keyword("non") *> Parsley.pure(Associativity.Non))

    private val infixDeclaration: Parsley[CstDeclaration] =
        (pos <~> (keyword("infix") *> associativity) <~>
            intLiteral <~>
            parens(
                (pos <~> operator <~> pos).map { case ((s, op), e) =>
                    CstName(op)(mkSpan(s, e))
                }
            ) <~>
            (symbol("=") *> ModuleParser.lowerName) <~> pos).map { case (((((s, assoc), prec), op), fn), e) =>
            CstInfixDeclaration(assoc, prec.toInt, op, fn)(mkSpan(s, e))
        }

    /** A top-level declaration. */
    val declaration: Parsley[CstDeclaration] =
        atomic(typeAliasDeclaration)
            | customTypeDeclaration
            | portDeclaration
            | infixDeclaration
            | valueDeclaration
