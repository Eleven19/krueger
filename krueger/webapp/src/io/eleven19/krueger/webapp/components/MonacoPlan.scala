package io.eleven19.krueger.webapp.components

/** Pure description of how a [[MonacoEditor]] should be configured on mount.
  *
  * Kept in its own source file with no dependency on [[io.eleven19.krueger.webapp.monaco.MonacoFacade]] so that unit
  * tests can exercise [[MonacoPlan.default]] without the Scala.js test runtime trying to resolve the `monaco-editor`
  * npm package (which is only available in the browser bundle).
  */
final case class MonacoPlan(
    language: String,
    initialValue: String,
    automaticLayout: Boolean,
    minimapEnabled: Boolean,
    fontSize: Int
) derives CanEqual

object MonacoPlan:

    /** Default plan used by the playground: layout auto-tracks, minimap off (side-by-side editors), 13px font. */
    def default(language: String, initialValue: String): MonacoPlan =
        MonacoPlan(
            language = language,
            initialValue = initialValue,
            automaticLayout = true,
            minimapEnabled = false,
            fontSize = 13
        )
