package io.eleven19.krueger.parser

import parsley.Parsley
import parsley.Parsley.{atomic, many, some}
import parsley.combinator.option
import parsley.position.pos

import io.eleven19.krueger.Span
import io.eleven19.krueger.cst.*
import io.eleven19.krueger.lexer.ElmLexer.*

/** Parser for Elm module declarations, imports, and top-level structure.
  *
  * Elm modules follow the form:
  * {{{
  *   module Name exposing (..)
  *   import List exposing (map, filter)
  *   -- declarations follow
  * }}}
  */
object ModuleParser:

    /** Helper to build a Span from two parsley positions. */
    private def mkSpan(start: (Int, Int), end: (Int, Int)): Span =
        Span(start._1, end._1 - start._1)

    // -----------------------------------------------------------------------
    // Names
    // -----------------------------------------------------------------------

    val name: Parsley[CstName] =
        (pos <~> identifier <~> pos).map { case ((s, n), e) =>
            CstName(n)(mkSpan(s, e))
        }

    val lowerName: Parsley[CstName] =
        (pos <~> lowerIdentifier <~> pos).map { case ((s, n), e) =>
            CstName(n)(mkSpan(s, e))
        }

    val upperName: Parsley[CstName] =
        (pos <~> upperIdentifier <~> pos).map { case ((s, n), e) =>
            CstName(n)(mkSpan(s, e))
        }

    val qualifiedName: Parsley[CstQualifiedName] =
        (pos <~> some(upperIdentifier) <~> pos).map { case ((s, parts), e) =>
            val sp = mkSpan(s, e)
            CstQualifiedName(parts.map(p => CstName(p)(sp)))(sp)
        }

    // -----------------------------------------------------------------------
    // Exposing lists
    // -----------------------------------------------------------------------

    private val exposedValue: Parsley[CstExposedItem] =
        (pos <~> lowerName <~> pos).map { case ((s, n), e) =>
            CstExposedValue(n)(mkSpan(s, e))
        }

    private val exposedOperator: Parsley[CstExposedItem] =
        (pos <~> parens(
            (pos <~> operator <~> pos).map { case ((s, op), e) =>
                CstName(op)(mkSpan(s, e))
            }
        ) <~> pos).map { case ((s, n), e) =>
            CstExposedOperator(n)(mkSpan(s, e))
        }

    private val exposedTypeConstructors: Parsley[CstExposedConstructors] =
        (pos <~> parens(symbol("..")) <~> pos).map { case ((s, _), e) =>
            CstExposedConstructorsAll()(mkSpan(s, e))
        }

    private val exposedType: Parsley[CstExposedItem] =
        (pos <~> upperName <~> option(exposedTypeConstructors) <~> pos).map { case (((s, n), ctors), e) =>
            CstExposedType(n, ctors)(mkSpan(s, e))
        }

    private val exposedItem: Parsley[CstExposedItem] =
        exposedType | exposedOperator | exposedValue

    val exposingList: Parsley[CstExposingList] =
        keyword("exposing") *> (
            (pos <~> parens(symbol("..")) <~> pos).map { case ((s, _), e) =>
                CstExposingAll()(mkSpan(s, e))
            }
            | (pos <~> parens(commaSep1(exposedItem)) <~> pos).map { case ((s, items), e) =>
                CstExposingExplicit(items)(mkSpan(s, e))
            }
        )

    // -----------------------------------------------------------------------
    // Module declaration
    // -----------------------------------------------------------------------

    private val moduleType: Parsley[ModuleType] =
        (keyword("port") *> Parsley.pure(ModuleType.Port))
            | (keyword("effect") *> Parsley.pure(ModuleType.Effect))
            | Parsley.pure(ModuleType.Plain)

    val moduleDeclaration: Parsley[CstModuleDeclaration] =
        (pos <~> moduleType <* keyword("module") <~> qualifiedName <~> exposingList <~> pos).map {
            case ((((s, mt), qn), exp), e) =>
                CstModuleDeclaration(mt, qn, exp)(mkSpan(s, e))
        }

    // -----------------------------------------------------------------------
    // Imports
    // -----------------------------------------------------------------------

    val importDecl: Parsley[CstImport] =
        (pos <~> keyword("import") *> qualifiedName <~>
            option(keyword("as") *> upperName) <~>
            option(exposingList) <~> pos).map { case ((((s, modName), alias), exp), e) =>
            CstImport(modName, alias, exp)(mkSpan(s, e))
        }

    // -----------------------------------------------------------------------
    // Top-level module
    // -----------------------------------------------------------------------

    /** Parse a complete Elm module. */
    val module: Parsley[CstModule] =
        fully(
            (pos <~> moduleDeclaration <~> many(importDecl) <~>
                many(DeclarationParser.declaration) <~> pos).map { case ((((s, modDecl), imports), decls), e) =>
                CstModule(modDecl, imports, decls)(mkSpan(s, e))
            }
        )
