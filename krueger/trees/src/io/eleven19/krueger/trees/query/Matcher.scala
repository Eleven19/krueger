package io.eleven19.krueger.trees.query

import scala.util.matching.Regex

import io.eleven19.krueger.trees.QueryableTree

/** Runs a parsed Query against any tree with a QueryableTree instance. */
object Matcher:

    /** All matches of `query` rooted anywhere inside `root`.
      *
      * Matches are produced depth-first, pre-order, as a `LazyList` so callers can short-circuit when they only need a
      * prefix (e.g. `matches(...).headOption`).
      */
    def matches[T](
        query: Query,
        root: T,
        registry: PredicateRegistry = PredicateRegistry.default
    )(using qt: QueryableTree[T]): LazyList[Match[T]] =
        preOrder(root)
            .flatMap { node =>
                matchPattern(query.root, node, Map.empty).map(caps => Match(node, caps))
            }
            .filter(m => query.predicates.forall(p => evaluatePredicate(p, m.captures, registry)))

    // --- Traversal -----------------------------------------------------------

    private def preOrder[T](t: T)(using qt: QueryableTree[T]): LazyList[T] =
        t #:: qt.children(t).to(LazyList).flatMap(preOrder)

    // --- Pattern matching ----------------------------------------------------

    private def matchPattern[T](
        pattern: Pattern,
        node: T,
        captures: Map[String, T]
    )(using qt: QueryableTree[T]): Option[Map[String, T]] =
        pattern match
            case WildcardPattern(capture) =>
                Some(bind(capture, node, captures))

            case NodePattern(expectedType, fieldPatterns, capture) =>
                if qt.nodeType(node) != expectedType then None
                else
                    val base      = bind(capture, node, captures)
                    val fieldsMap = qt.fields(node)
                    fieldPatterns.foldLeft[Option[Map[String, T]]](Some(base)) { (accOpt, fp) =>
                        accOpt.flatMap { acc =>
                            val values = fieldsMap.getOrElse(fp.name, Seq.empty)
                            values.iterator
                                .flatMap(v => matchPattern(fp.pattern, v, acc))
                                .nextOption()
                        }
                    }

    private def bind[T](capture: Option[String], node: T, captures: Map[String, T]): Map[String, T] =
        capture.fold(captures)(name => captures.updated(name, node))

    // --- Predicates ----------------------------------------------------------

    private def evaluatePredicate[T](
        pred: Predicate,
        captures: Map[String, T],
        registry: PredicateRegistry
    )(using QueryableTree[T]): Boolean =
        pred match
            case EqPredicate(l, r) =>
                registry.predicates
                    .get("#eq?")
                    .exists(_.evaluate(List(l, r), captures))
            case MatchPredicate(arg, regex) =>
                registry.predicates
                    .get("#match?")
                    .exists(_.evaluate(List(arg, StringArg(regex)), captures))

/** Pluggable implementation of a named predicate. */
trait PredicateImpl:
    def evaluate[T](args: List[PredicateArg], captures: Map[String, T])(using qt: QueryableTree[T]): Boolean

/** A lookup table of predicate names to implementations. */
final case class PredicateRegistry(predicates: Map[String, PredicateImpl]):

    /** Register or override a predicate under `name`. */
    def withPredicate(name: String, impl: PredicateImpl): PredicateRegistry =
        PredicateRegistry(predicates.updated(name, impl))

object PredicateRegistry:

    /** Default registry with `#eq?` and `#match?` for text-based comparison. */
    val default: PredicateRegistry = PredicateRegistry(
        Map(
            "#eq?"    -> EqImpl,
            "#match?" -> MatchImpl
        )
    )

private object EqImpl extends PredicateImpl:

    def evaluate[T](args: List[PredicateArg], captures: Map[String, T])(using qt: QueryableTree[T]): Boolean =
        args match
            case l :: r :: Nil =>
                (resolveText(l, captures), resolveText(r, captures)) match
                    case (Some(a), Some(b)) => a == b
                    case _                  => false
            case _ => false

    private def resolveText[T](
        arg: PredicateArg,
        captures: Map[String, T]
    )(using qt: QueryableTree[T]): Option[String] =
        arg match
            case CaptureRef(name) => captures.get(name).flatMap(qt.text)
            case StringArg(v)     => Some(v)

private object MatchImpl extends PredicateImpl:

    def evaluate[T](args: List[PredicateArg], captures: Map[String, T])(using qt: QueryableTree[T]): Boolean =
        args match
            case CaptureRef(name) :: StringArg(pattern) :: Nil =>
                val compiled: Regex = pattern.r
                captures.get(name).flatMap(qt.text).exists(text => compiled.findFirstIn(text).isDefined)
            case _ => false
