package io.eleven19.krueger.webapp.components

/** Classification of a compile-result envelope into "show value" or "show error messages". Keeps the render-time
  * branching out of the Laminar component so it can be tested on Node (no DOM) — see [[ResultsPanelSpec]].
  */
enum ViewOutcome[+A] derives CanEqual:
    case Ok(value: A)
    case Error(messages: List[String])

/** Classification of the match list. `Empty` drives the "no matches" placeholder that REQ-webapp-components-002-edge
  * pins down — callers must render something readable rather than a blank panel.
  */
enum MatchesOutcome derives CanEqual:
    case Empty
    case Matched(matches: List[io.eleven19.krueger.compiler.MatchView])
