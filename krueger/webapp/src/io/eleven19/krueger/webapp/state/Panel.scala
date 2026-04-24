package io.eleven19.krueger.webapp.state

/** Which output the right-hand panel is currently showing.
  *
  * Matches the activity bar on the left: a single selected [[Panel]] drives which child component [[ResultsPanel]]
  * renders. Labels and icons live next to the cases so the bar and the panel chrome stay in lock-step — one place to
  * rename, one place to pin by test.
  */
enum Panel derives CanEqual:
    case Matches, Cst, Ast, PrettyQuery

object Panel:

    /** The panel selected on first paint. Matches is user-primary — most people will want to see query results before
      * the structural trees.
      */
    val default: Panel = Panel.Matches

    /** Enumerates panels in the order they appear on the activity bar. */
    val all: List[Panel] = Panel.values.toList

    /** Short, user-facing label. */
    def label(p: Panel): String = p match
        case Panel.Matches     => "Matches"
        case Panel.Cst         => "CST"
        case Panel.Ast         => "AST"
        case Panel.PrettyQuery => "Canonical Query"

    /** Single-glyph icon drawn in the activity bar. Text-only so the facade stays portable — can upgrade to SVG later
      * without rewriting consumers.
      */
    def icon(p: Panel): String = p match
        case Panel.Matches     => "\u21e2" // ⇢
        case Panel.Cst         => "\u25c7" // ◇
        case Panel.Ast         => "\u25c6" // ◆
        case Panel.PrettyQuery => "\u2261" // ≡
