package io.eleven19.krueger.trees.query

import purelogic.Abort
import purelogic.Logic
import purelogic.Reader
import purelogic.State
import purelogic.Writer

object QueryLogic:

    final case class QueryState[Ctx, Err](context: Ctx, errors: Vector[Err]) derives CanEqual

    final case class Result[Ctx, Log, Err, A](
        context: Ctx,
        logs: Vector[Log],
        errors: Vector[Err],
        value: Either[Vector[Err], A]
    ) derives CanEqual

    type QueryEffect[Ctx, Log, Err, A] = Logic[Unit, Log, QueryState[Ctx, Err], Err, A]

    def run[Ctx, Log, Err, A](initialContext: Ctx)(effect: QueryEffect[Ctx, Log, Err, A]): Result[Ctx, Log, Err, A] =
        val (logs, (state, value)) = Reader(()) {
            Writer[Log, (QueryState[Ctx, Err], Either[Err, A])] {
                State(QueryState(initialContext, Vector.empty[Err])) {
                    Abort(effect)
                }
            }
        }
        // Combine accumulated state errors and any abort error into a single list.
        val allErrors = value.left.toOption.fold(state.errors)(state.errors :+ _)
        // Return Left(allErrors) if any errors exist; otherwise preserve the Right value.
        val rendered = if allErrors.nonEmpty then Left(allErrors) else value.left.map(_ => allErrors)
        Result(state.context, logs, allErrors, rendered)

    def readContext[Ctx, Log, Err](using State[QueryState[Ctx, Err]]): Ctx =
        State.get[QueryState[Ctx, Err]].context

    def setContext[Ctx, Log, Err](context: Ctx)(using State[QueryState[Ctx, Err]]): Unit =
        State.update[QueryState[Ctx, Err]](_.copy(context = context))

    def updateContext[Ctx, Log, Err](f: Ctx => Ctx)(using State[QueryState[Ctx, Err]]): Unit =
        State.update[QueryState[Ctx, Err]](s => s.copy(context = f(s.context)))

    def log[Ctx, Log, Err](entry: Log)(using Writer[Log]): Unit =
        Writer.write(entry)

    def error[Ctx, Log, Err](err: Err)(using State[QueryState[Ctx, Err]]): Unit =
        State.update[QueryState[Ctx, Err]](s => s.copy(errors = s.errors :+ err))

    def failFast[Ctx, Log, Err](err: Err)(using Abort[Err]): Nothing =
        Abort.fail(err)
