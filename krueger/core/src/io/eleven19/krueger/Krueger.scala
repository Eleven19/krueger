package io.eleven19.krueger

import io.eleven19.krueger.compiler.ParseDiagnostic
import io.eleven19.krueger.cst.{CstModule, CstTrivia}
import io.eleven19.krueger.ast.Module
import io.eleven19.krueger.parser.{
    CommentScanner,
    CstLowering,
    ModuleParser,
    ParseDiagnosticErrorBuilder,
    TriviaAssociator
}

/** Public API entry point for the Krueger Elm dialect parser. */
object Krueger:

    /** Parse Elm source code into a CST. */
    def parseCst(source: String): parsley.Result[ParseDiagnostic, CstModule] =
        val errorBuilder = ParseDiagnosticErrorBuilder(source)
        ModuleParser.module.parse[ParseDiagnostic](source)(using errorBuilder).map { module =>
            val withComments = CstModule(
                module.moduleDecl,
                module.imports,
                module.declarations,
                CstTrivia(CommentScanner.scan(source).toIndexedSeq)
            )(module.span)
            TriviaAssociator.associate(withComments)
        }

    /** Parse Elm source code into an AST (CST lowered). */
    def parseAst(source: String): parsley.Result[ParseDiagnostic, Module] =
        parseCst(source).map(CstLowering.lowerModule)
