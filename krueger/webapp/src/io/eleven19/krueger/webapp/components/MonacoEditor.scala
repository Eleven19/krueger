package io.eleven19.krueger.webapp.components

import com.raquo.laminar.api.L.*
import org.scalajs.dom

import scala.scalajs.js

import io.eleven19.krueger.webapp.monaco.MonacoFacade

/** Mounts a Monaco standalone editor into a Laminar host element and keeps its content in sync with a `Var[String]`.
  *
  * The pure plan that configures the editor lives in [[MonacoPlan]], which has no dependency on [[MonacoFacade]] — so
  * Node-based unit tests can pin plan shape without the Scala.js test runtime trying to resolve the `monaco-editor` npm
  * package. The DOM-facing pieces (create, dispose, change subscription) stay here, guarded inside `onMountCallback` so
  * unit tests never invoke them.
  *
  * Two-way binding policy:
  *   - On mount, the editor is created with the Var's current value as its initial content.
  *   - Content changes in the editor flow back to the Var (source of truth is the user's typing).
  *   - External writes to the Var are pushed into the editor only if they differ from what Monaco already shows. This
  *     prevents the echo loop where Monaco → Var → Monaco oscillates on every keystroke.
  *
  * On unmount, `editor.dispose()` runs so the Monaco model and listeners are freed (REQ-002).
  */
object MonacoEditor:

    /** Translate a plan into the Monaco `EditorOptions` JS shape. */
    private def toFacadeOptions(p: MonacoPlan): MonacoFacade.EditorOptions =
        val minimap = (new MonacoFacade.MinimapOptions {}).asInstanceOf[MonacoFacade.MinimapOptions]
        minimap.enabled = p.minimapEnabled
        val opts = (new MonacoFacade.EditorOptions {}).asInstanceOf[MonacoFacade.EditorOptions]
        opts.language = p.language
        opts.value = p.initialValue
        opts.automaticLayout = p.automaticLayout
        opts.minimap = minimap
        opts.fontSize = p.fontSize
        opts

    /** Mount a Monaco editor driving the given `Var`. The host div is sized to fill its parent via CSS — callers decide
      * the parent's dimensions.
      */
    def apply(textVar: Var[String], language: String): HtmlElement =
        div(
            cls := s"krueger-monaco-host krueger-monaco-${language}",
            onMountCallback { ctx =>
                val editor = MonacoFacade.Monaco.editor.create(
                    ctx.thisNode.ref,
                    js.defined(toFacadeOptions(MonacoPlan.default(language, textVar.now())))
                )

                var lastKnown = textVar.now()

                editor.onDidChangeModelContent { () =>
                    val next = editor.getValue()
                    if next != lastKnown then
                        lastKnown = next
                        textVar.set(next)
                }

                val _ = textVar.signal.foreach { incoming =>
                    if incoming != lastKnown then
                        lastKnown = incoming
                        editor.setValue(incoming)
                }(using ctx.owner)

                ctx.thisNode.ref.asInstanceOf[js.Dynamic].__kruegerMonaco = editor.asInstanceOf[js.Any]
            },
            onUnmountCallback { node =>
                val handle = node.ref.asInstanceOf[js.Dynamic].__kruegerMonaco.asInstanceOf[js.UndefOr[js.Any]]
                handle.foreach { e =>
                    e.asInstanceOf[MonacoFacade.IStandaloneCodeEditor].dispose()
                }
            }
        )
