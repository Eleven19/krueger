package io.eleven19.krueger.webappwasm

import io.eleven19.krueger.ast.Module
import io.eleven19.krueger.compiler.CompilerComponent
import io.eleven19.krueger.compiler.MatchView
import io.eleven19.krueger.cst.CstModule
import io.eleven19.krueger.cst.CstNode
import io.eleven19.krueger.trees.query.Query

trait CompilerBackend:
    def id: String
    def parseCst(src: String): CompilerComponent.CompileResult[Unit, CstModule]
    def parseAst(src: String): CompilerComponent.CompileResult[Unit, Module]
    def parseQuery(src: String): CompilerComponent.CompileResult[Unit, Query]
    def runQuery(query: Query, root: CstNode): CompilerComponent.CompileResult[Unit, List[MatchView]]
    def prettyQuery(query: Query): String
