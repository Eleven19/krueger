package io.eleven19.krueger.compiler

/** Structured compilation diagnostics emitted by Krueger compiler and tooling APIs.
  *
  * Cases carry enough context (phase, message, optional span) so downstream UIs can render actionable errors without
  * re-parsing messages.
  */
sealed trait CompileError derives CanEqual:
    def message: String

object CompileError:

    /** Failure while parsing Elm source into a CST or AST. */
    final case class ParseError(phase: String, message: String, span: Option[Span] = None) extends CompileError

    /** Failure while parsing or evaluating a query. */
    final case class QueryError(message: String, span: Option[Span] = None) extends CompileError

    /** Unexpected internal failure (bug in the compiler surface). */
    final case class InternalError(message: String) extends CompileError

/** Source span `[start, end)` in 0-based character offsets. */
final case class Span(start: Int, end: Int) derives CanEqual
