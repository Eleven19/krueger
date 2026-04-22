package io.eleven19.krueger

import io.eleven19.krueger.cst.{CstModule, CstTrivia}
import io.eleven19.krueger.ast.Module
import io.eleven19.krueger.parser.{CommentScanner, CstLowering, ModuleParser, TriviaAssociator}

/** Public API entry point for the Krueger Elm dialect parser. */
object Krueger:

    /** Parse Elm source code into a CST. */
    def parseCst(source: String): parsley.Result[String, CstModule] =
        ModuleParser.module.parse(source).map { module =>
            val withComments = CstModule(
                module.moduleDecl,
                module.imports,
                module.declarations,
                CstTrivia(CommentScanner.scan(source).toIndexedSeq)
            )(module.span)
            TriviaAssociator.associate(withComments)
        }

    /** Parse Elm source code into an AST (CST lowered). */
    def parseAst(source: String): parsley.Result[String, Module] =
        parseCst(source).map(CstLowering.lowerModule)
