package io.eleven19.krueger.parser

import parsley.Parsley
import parsley.Parsley.{atomic, many, some}
import parsley.combinator.option
import parsley.position.pos

import io.eleven19.krueger.Span
import io.eleven19.krueger.cst.*
import io.eleven19.krueger.lexer.ElmLexer.*

/** Parser for Elm expressions.
  *
  * Handles literals, variables, constructors, function application, binary operators, if/then/else, let/in, case/of,
  * lambdas, tuples, lists, records, and field access.
  */
object ExpressionParser:

    private def mkSpan(start: (Int, Int), end: (Int, Int)): Span =
        Span(start._1, end._1 - start._1)

    // -----------------------------------------------------------------------
    // Atoms
    // -----------------------------------------------------------------------

    private val intLit: Parsley[CstExpression] =
        (pos <~> intLiteral <~> pos).map { case ((s, v), e) =>
            CstIntLiteral(v)(mkSpan(s, e))
        }

    private val floatLit: Parsley[CstExpression] =
        (pos <~> floatLiteral <~> pos).map { case ((s, v), e) =>
            CstFloatLiteral(v)(mkSpan(s, e))
        }

    private val stringLit: Parsley[CstExpression] =
        (pos <~> stringLiteral <~> pos).map { case ((s, v), e) =>
            CstStringLiteral(v)(mkSpan(s, e))
        }

    private val charLit: Parsley[CstExpression] =
        (pos <~> charLiteral <~> pos).map { case ((s, v), e) =>
            CstCharLiteral(v)(mkSpan(s, e))
        }

    private val variableRef: Parsley[CstExpression] =
        (pos <~> ModuleParser.qualifiedValueName <~> pos).map { case ((s, qn), e) =>
            CstVariableRef(qn)(mkSpan(s, e))
        }

    private val constructorRef: Parsley[CstExpression] =
        (pos <~> ModuleParser.qualifiedName <~> pos).map { case ((s, qn), e) =>
            CstConstructorRef(qn)(mkSpan(s, e))
        }

    private val unitLit: Parsley[CstExpression] =
        atomic((pos <~> parens(Parsley.pure(())) <~> pos).map { case ((s, _), e) =>
            CstUnitLiteral()(mkSpan(s, e))
        })

    private val parenthesized: Parsley[CstExpression] =
        (pos <~> parens(expression) <~> pos).map { case ((s, expr), e) =>
            CstParenthesized(expr)(mkSpan(s, e))
        }

    private val tupleLit: Parsley[CstExpression] =
        (pos <~> parens(expression <~> some(symbol(",") *> expression)) <~> pos).map { case ((s, (first, rest)), e) =>
            CstTupleLiteral(first :: rest)(mkSpan(s, e))
        }

    private val listLit: Parsley[CstExpression] =
        (pos <~> brackets(commaSep(expression)) <~> pos).map { case ((s, elems), e) =>
            CstListLiteral(elems)(mkSpan(s, e))
        }

    private val recordField: Parsley[CstRecordField] =
        (pos <~> ModuleParser.lowerName <~> (symbol("=") *> expression) <~> pos).map { case (((s, n), v), e) =>
            CstRecordField(n, v)(mkSpan(s, e))
        }

    private val recordLit: Parsley[CstExpression] =
        (pos <~> braces(commaSep1(recordField)) <~> pos).map { case ((s, fields), e) =>
            CstRecordLiteral(fields)(mkSpan(s, e))
        }

    private val recordUpdate: Parsley[CstExpression] =
        (pos <~> braces(ModuleParser.lowerName <~> (symbol("|") *> commaSep1(recordField))) <~> pos).map {
            case ((s, (rec, fields)), e) =>
                CstRecordUpdate(rec, fields)(mkSpan(s, e))
        }

    private val fieldAccessFn: Parsley[CstExpression] =
        (pos <~> (symbol(".") *> ModuleParser.lowerName) <~> pos).map { case ((s, n), e) =>
            CstFieldAccessFunction(n)(mkSpan(s, e))
        }

    /** An atomic expression (no application or binary ops). */
    val atom: Parsley[CstExpression] =
        atomic(floatLit)
            | intLit
            | stringLit
            | charLit
            | unitLit
            | atomic(tupleLit)
            | parenthesized
            | listLit
            | atomic(recordUpdate)
            | recordLit
            | fieldAccessFn
            | variableRef
            | constructorRef

    // -----------------------------------------------------------------------
    // Compound expressions
    // -----------------------------------------------------------------------

    private val ifThenElse: Parsley[CstExpression] =
        (pos <~> (keyword("if") *> expression) <~>
            (keyword("then") *> expression) <~>
            (keyword("else") *> expression) <~> pos).map { case ((((s, cond), thenE), elseE), e) =>
            CstIfThenElse(cond, thenE, elseE)(mkSpan(s, e))
        }

    private val letBinding: Parsley[CstLetBinding] =
        (pos <~>
            option(DeclarationParser.typeAnnotation) <~>
            PatternParser.pattern <~>
            many(PatternParser.pattern) <~>
            (symbol("=") *> expression) <~> pos).map { case (((((s, ann), pat), params), body), e) =>
            CstLetBinding(ann, pat, params, body)(mkSpan(s, e))
        }

    private val letIn: Parsley[CstExpression] =
        (pos <~> (keyword("let") *> some(letBinding)) <~>
            (keyword("in") *> expression) <~> pos).map { case (((s, bindings), body), e) =>
            CstLetIn(bindings, body)(mkSpan(s, e))
        }

    private val caseBranch: Parsley[CstCaseBranch] =
        (pos <~> PatternParser.pattern <~> (symbol("->") *> expression) <~> pos).map { case (((s, pat), body), e) =>
            CstCaseBranch(pat, body)(mkSpan(s, e))
        }

    private val caseOf: Parsley[CstExpression] =
        (pos <~> (keyword("case") *> expression) <~>
            (keyword("of") *> some(caseBranch)) <~> pos).map { case (((s, expr), branches), e) =>
            CstCaseOf(expr, branches)(mkSpan(s, e))
        }

    private val lambda: Parsley[CstExpression] =
        (pos <~> (symbol("\\") *> some(PatternParser.pattern)) <~>
            (symbol("->") *> expression) <~> pos).map { case (((s, params), body), e) =>
            CstLambda(params, body)(mkSpan(s, e))
        }

    private val negate: Parsley[CstExpression] =
        (pos <~> (symbol("-") *> atom) <~> pos).map { case ((s, expr), e) =>
            CstNegate(expr)(mkSpan(s, e))
        }

    private val fieldSuffix: Parsley[CstName] =
        symbol(".") *> ModuleParser.lowerName

    private val postfixAtom: Parsley[CstExpression] =
        (pos <~> atom <~> many(fieldSuffix) <~> pos).map { case (((s, base), fields), e) =>
            fields.foldLeft(base) { (record, field) =>
                CstFieldAccess(record, field)(Span.between(record.span, field.span))
            }
        }

    /** A non-operator expression: atom with optional function application and field access. */
    private val appExpr: Parsley[CstExpression] =
        val base = ifThenElse | letIn | caseOf | lambda | negate | postfixAtom
        (pos <~> base <~> many(postfixAtom) <~> pos).map { case (((s, fn), args), e) =>
            if args.isEmpty then fn
            else CstFunctionApplication(fn, args)(mkSpan(s, e))
        }

    /** Parse a binary operator name. */
    private val binOp: Parsley[CstName] =
        (pos <~> operator <~> pos).map { case ((s, op), e) =>
            CstName(op)(mkSpan(s, e))
        }

    /** A full expression including binary operators (flat, to be re-associated later). */
    lazy val expression: Parsley[CstExpression] =
        (pos <~> appExpr <~> many(binOp <~> appExpr) <~> pos).map { case (((s, first), ops), e) =>
            ops.foldLeft(first) { case (left, (op, right)) =>
                CstBinaryOp(left, op, right)(mkSpan(s, e))
            }
        }
