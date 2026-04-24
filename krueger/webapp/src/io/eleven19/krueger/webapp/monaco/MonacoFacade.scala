package io.eleven19.krueger.webapp.monaco

import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

/** Minimal Scala.js facade over the `monaco-editor` npm package.
  *
  * We expose only the slice the playground needs: create an editor bound to a DOM element, read/write its content,
  * subscribe to content changes, and register a language + Monarch tokenizer. The full Monaco surface is vast; adding
  * more bindings here is cheap, but resist the urge until something actually calls them.
  *
  * @note
  *   Monaco is shipped as an ES module. The bundler (Vite in the Astro site) is responsible for resolving
  *   `monaco-editor` and wiring its web workers via its own config. This facade only describes the import surface.
  */
object MonacoFacade:

    /** The `monaco` namespace — the whole package's default export. */
    @js.native
    @JSImport("monaco-editor", JSImport.Namespace)
    object Monaco extends js.Object:
        val editor: MonacoEditorNs     = js.native
        val languages: MonacoLanguages = js.native

    @js.native
    trait MonacoEditorNs extends js.Object:

        def create(container: dom.Element, options: js.UndefOr[EditorOptions] = js.undefined): IStandaloneCodeEditor =
            js.native

    @js.native
    trait IStandaloneCodeEditor extends js.Object:
        def getValue(): String                                        = js.native
        def setValue(v: String): Unit                                 = js.native
        def onDidChangeModelContent(handler: js.Function0[Unit]): Any = js.native
        def dispose(): Unit                                           = js.native

    @js.native
    trait MonacoLanguages extends js.Object:
        def register(definition: LanguageExtensionPoint): Unit           = js.native
        def getLanguages(): js.Array[LanguageExtensionPoint]             = js.native
        def setMonarchTokensProvider(id: String, rules: js.Object): Unit = js.native

    /** Options object passed to `editor.create`. Every field is optional; the playground fills in what it needs. */
    trait EditorOptions extends js.Object:
        var value: js.UndefOr[String]            = js.undefined
        var language: js.UndefOr[String]         = js.undefined
        var theme: js.UndefOr[String]            = js.undefined
        var automaticLayout: js.UndefOr[Boolean] = js.undefined
        var minimap: js.UndefOr[MinimapOptions]  = js.undefined
        var fontSize: js.UndefOr[Int]            = js.undefined

    trait MinimapOptions extends js.Object:
        var enabled: js.UndefOr[Boolean] = js.undefined

    trait LanguageExtensionPoint extends js.Object:
        var id: String
        var extensions: js.UndefOr[js.Array[String]] = js.undefined
        var aliases: js.UndefOr[js.Array[String]]    = js.undefined
