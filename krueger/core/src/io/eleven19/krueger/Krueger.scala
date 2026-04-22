package io.eleven19.krueger

import io.eleven19.krueger.cst.CstModule
import io.eleven19.krueger.ast.Module
import io.eleven19.krueger.parser.{CommentScanner, CstLowering, ModuleParser}

/** Public API entry point for the Krueger Elm dialect parser. */
object Krueger:

    /** Parse Elm source code into a CST. */
    def parseCst(source: String): parsley.Result[String, CstModule] =
        ModuleParser.module.parse(source).map { module =>
            CstModule(
                module.moduleDecl,
                module.imports,
                module.declarations,
                CommentScanner.scan(source)
            )(module.span)
        }

    /** Parse Elm source code into an AST (CST lowered). */
    def parseAst(source: String): parsley.Result[String, Module] =
        parseCst(source).map(CstLowering.lowerModule)
