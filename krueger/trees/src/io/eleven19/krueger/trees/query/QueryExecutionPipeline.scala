package io.eleven19.krueger.trees.query

import io.eleven19.krueger.trees.CaptureName
import io.eleven19.krueger.trees.QueryableTree

object QueryExecutionPipeline:

    final case class Analysis(nodeCount: Int, captureCount: Int, predicateCount: Int) derives CanEqual

    final case class Plan(query: Query, analysis: Analysis) derives CanEqual

    final case class Lowered(query: Query, registry: PredicateRegistry) derives CanEqual

    final case class PipelineResult[T](
        normalized: Query,
        analysis: Analysis,
        plan: Plan,
        lowered: Lowered,
        matches: Vector[Match[T]]
    )

    def normalize[Ctx, Err](query: Query)(using
        purelogic.Writer[String],
        purelogic.State[QueryLogic.QueryState[Ctx, Err]]
    ): Query =
        QueryLogic.log[Ctx, String, Err]("normalize")
        query

    def analyze[Ctx, Err](query: Query)(using
        purelogic.Writer[String],
        purelogic.State[QueryLogic.QueryState[Ctx, Err]]
    ): Analysis =
        QueryLogic.log[Ctx, String, Err]("analyze")
        Analysis(
            nodeCount = QueryVisitor.count(query),
            captureCount = query.captureNames.size,
            predicateCount = query.predicates.size
        )

    def validate[Ctx](query: Query)(using
        purelogic.Writer[String],
        purelogic.State[QueryLogic.QueryState[Ctx, String]]
    ): Query =
        QueryLogic.log[Ctx, String, String]("validate")
        val missing = predicateCaptureRefs(query.predicates).diff(query.captureNames)
        if missing.nonEmpty then
            val rendered = missing.toList.map(CaptureName.unwrap).sorted.map(n => s"@$n").mkString(", ")
            QueryLogic.error[Ctx, String, String](s"Predicate references unknown capture(s): $rendered")
        query

    def lower[Ctx, Err](query: Query, registry: PredicateRegistry = PredicateRegistry.default)(using
        purelogic.Writer[String],
        purelogic.State[QueryLogic.QueryState[Ctx, Err]]
    ): Lowered =
        QueryLogic.log[Ctx, String, Err]("lower")
        Lowered(query, registry)

    def execute[Ctx, T, Err](lowered: Lowered, root: T)(using
        purelogic.Writer[String],
        purelogic.State[QueryLogic.QueryState[Ctx, Err]],
        QueryableTree[T]
    ): Vector[Match[T]] =
        QueryLogic.log[Ctx, String, Err]("execute")
        Matcher.matches(lowered.query, root, lowered.registry).toVector

    def run[Ctx, T](
        query: Query,
        root: T,
        initialContext: Ctx,
        registry: PredicateRegistry = PredicateRegistry.default
    )(using qt: QueryableTree[T]): QueryLogic.Result[Ctx, String, String, PipelineResult[T]] =
        QueryLogic.run[Ctx, String, String, PipelineResult[T]](initialContext) {
            val normalized = normalize[Ctx, String](query)
            val analysis   = analyze[Ctx, String](normalized)
            val validated  = validate[Ctx](normalized)
            val plan       = Plan(validated, analysis)
            val lowered    = lower[Ctx, String](plan.query, registry)
            val matches    = execute[Ctx, T, String](lowered, root)
            PipelineResult(normalized, analysis, plan, lowered, matches)
        }

    private def predicateCaptureRefs(predicates: List[Predicate]): Set[CaptureName] =
        predicates.foldLeft(Set.empty[CaptureName]) { (acc, p) =>
            p match
                case EqPredicate(left, right) =>
                    acc ++ argCapture(left) ++ argCapture(right)
                case MatchPredicate(arg, _) =>
                    acc ++ argCapture(arg)
                case NotEqPredicate(left, right) =>
                    acc ++ argCapture(left) ++ argCapture(right)
                case NotMatchPredicate(arg, _) =>
                    acc ++ argCapture(arg)
        }

    private def argCapture(arg: PredicateArg): Set[CaptureName] = arg match
        case CaptureRef(name) => Set(name)
        case StringArg(_)     => Set.empty
