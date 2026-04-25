package io.eleven19.krueger.webappwasm

import io.eleven19.krueger.compiler.CompilerComponent
import io.eleven19.krueger.compiler.Krueger
import io.eleven19.krueger.compiler.MatchView
import io.eleven19.krueger.cst.CstNode
import io.eleven19.krueger.trees.query.Query

final class WebGcBackend private (compiler: CompilerComponent[Unit]) extends CompilerBackend:

    import io.eleven19.krueger.cst.CstQueryableTree.given

    override val id: String = "webgc"

    override def parseCst(src: String) =
        CompilerComponent.runUnit(compiler.parseCst(src))

    override def parseAst(src: String) =
        CompilerComponent.runUnit(compiler.parseAst(src))

    override def parseQuery(src: String) =
        CompilerComponent.runUnit(compiler.parseQuery(src))

    override def runQuery(query: Query, root: CstNode): CompilerComponent.CompileResult[Unit, List[MatchView]] =
        CompilerComponent.runUnit(compiler.runQuery[CstNode](query, root))

    override def prettyQuery(query: Query): String =
        compiler.prettyQuery(query)

object WebGcBackend:
    def load(): CompilerBackend =
        WebGcBackend(Krueger.compiler[Unit])
