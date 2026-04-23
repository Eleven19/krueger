package io.eleven19.krueger.trees.query

import io.eleven19.krueger.trees.CaptureName
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
        query.root match
            case MultiPattern(patterns) =>
                patterns.to(LazyList).flatMap { p =>
                    matches(Query(p, query.predicates), root, registry)
                }
            case _ =>
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
        captures: Map[CaptureName, T]
    )(using qt: QueryableTree[T]): Option[Map[CaptureName, T]] =
        pattern match
            case WildcardPattern(capture) =>
                Some(bind(capture, node, captures))

            case MultiPattern(patterns) =>
                patterns.iterator
                    .flatMap(p => matchPattern(p, node, captures))
                    .nextOption()

            case AlternationPattern(patterns, capture) =>
                patterns.iterator
                    .flatMap(p => matchPattern(p, node, captures))
                    .map(updated => bind(capture, node, updated))
                    .nextOption()

            case NodePattern(
                    expectedType,
                    fieldPatterns,
                    childPatterns,
                    capture,
                    adjacentChildAnchors,
                    negatedFields,
                    childQuantifiers
                ) =>
                if qt.nodeType(node) != expectedType then None
                else if !negatedFields.forall(fn => qt.fields(node).getOrElse(fn, Seq.empty).isEmpty) then None
                else
                    val base      = bind(capture, node, captures)
                    val fieldsMap = qt.fields(node)
                    val withFields =
                        fieldPatterns.foldLeft[Option[(Map[CaptureName, T], List[T])]](Some((base, Nil))) {
                            (accOpt, fp) =>
                                accOpt.flatMap { acc =>
                                    val (accCaptures, usedChildren) = acc
                                    val values                      = fieldsMap.getOrElse(fp.name, Seq.empty)
                                    values.iterator
                                        .flatMap(v => matchPattern(fp.pattern, v, accCaptures).map(c => (c, usedChildren :+ v)))
                                        .nextOption()
                                }
                        }
                    withFields.flatMap { case (acc, usedChildren) =>
                        val remainingChildren = excludeUsed(qt.children(node), usedChildren)
                        matchOrderedChildren(childPatterns, remainingChildren, acc, adjacentChildAnchors, childQuantifiers)
                    }

    private def excludeUsed[T](children: Seq[T], used: List[T]): Seq[T] =
        used.foldLeft(children) { (accChildren, usedChild) =>
            val idx = accChildren.indexWhere(c => c.equals(usedChild))
            if idx < 0 then accChildren else accChildren.patch(idx, Nil, 1)
        }

    private def matchOrderedChildren[T](
        patterns: List[Pattern],
        children: Seq[T],
        captures: Map[CaptureName, T],
        adjacentChildAnchors: Set[Int],
        childQuantifiers: Map[Int, QuantifierKind]
    )(using qt: QueryableTree[T]): Option[Map[CaptureName, T]] =
        def go(
            remaining: List[Pattern],
            nextPatternIdx: Int,
            startChildIdx: Int,
            lastMatchedChildIdx: Option[Int],
            accCaptures: Map[CaptureName, T]
        ): Option[Map[CaptureName, T]] =
            remaining match
                case Nil => Some(accCaptures)
                case wanted :: rest =>
                    val quantOpt = childQuantifiers.get(nextPatternIdx)

                    def matchOneAt(i: Int, inCaps: Map[CaptureName, T]): Option[Map[CaptureName, T]] =
                        val anchoredToPrev = adjacentChildAnchors.contains(nextPatternIdx - 1)
                        val placementOk = !anchoredToPrev || lastMatchedChildIdx.contains(i - 1)
                        if !placementOk then None
                        else matchPattern(wanted, children(i), inCaps)

                    def repeatAtLeast(
                        minMatches: Int,
                        currentStart: Int,
                        currentLast: Option[Int],
                        currentCaps: Map[CaptureName, T],
                        matched: Int
                    ): Option[Map[CaptureName, T]] =
                        val continuation =
                            if matched >= minMatches then
                                go(rest, nextPatternIdx + 1, currentStart, currentLast, currentCaps)
                            else None

                        continuation.orElse {
                            (currentStart until children.size).iterator
                                .flatMap { i =>
                                    val anchoredToPrev = adjacentChildAnchors.contains(nextPatternIdx - 1)
                                    val placementOk = !anchoredToPrev || currentLast.contains(i - 1)
                                    if !placementOk then Iterator.empty
                                    else
                                        matchPattern(wanted, children(i), currentCaps)
                                            .flatMap(updated => repeatAtLeast(minMatches, i + 1, Some(i), updated, matched + 1))
                                            .iterator
                                }
                                .nextOption()
                        }

                    quantOpt match
                        case Some(QuantifierKind.Optional) =>
                            (startChildIdx until children.size).iterator
                                .flatMap { i =>
                                    matchOneAt(i, accCaptures)
                                        .flatMap(updated => go(rest, nextPatternIdx + 1, i + 1, Some(i), updated))
                                        .iterator
                                }
                                .nextOption()
                                .orElse(go(rest, nextPatternIdx + 1, startChildIdx, lastMatchedChildIdx, accCaptures))
                        case Some(QuantifierKind.ZeroOrMore) =>
                            repeatAtLeast(minMatches = 0, startChildIdx, lastMatchedChildIdx, accCaptures, matched = 0)
                        case Some(QuantifierKind.OneOrMore) =>
                            repeatAtLeast(minMatches = 1, startChildIdx, lastMatchedChildIdx, accCaptures, matched = 0)
                        case None =>
                            (startChildIdx until children.size).iterator
                                .flatMap { i =>
                                    matchOneAt(i, accCaptures)
                                        .flatMap(updated => go(rest, nextPatternIdx + 1, i + 1, Some(i), updated))
                                        .iterator
                                }
                                .nextOption()

        go(patterns, nextPatternIdx = 0, startChildIdx = 0, lastMatchedChildIdx = None, captures)

    private def bind[T](
        capture: Option[CaptureName],
        node: T,
        captures: Map[CaptureName, T]
    ): Map[CaptureName, T] =
        capture.fold(captures)(name => captures.updated(name, node))

    // --- Predicates ----------------------------------------------------------

    private def evaluatePredicate[T](
        pred: Predicate,
        captures: Map[CaptureName, T],
        registry: PredicateRegistry
    )(using QueryableTree[T]): Boolean =
        pred match
            case EqPredicate(l, r) =>
                registry.predicates
                    .get(PredicateName.Eq)
                    .exists(_.evaluate(PredicateArgs.Eq(l, r), captures))
            case MatchPredicate(arg, regex) =>
                registry.predicates
                    .get(PredicateName.Match)
                    .exists(_.evaluate(PredicateArgs.Match(arg, regex), captures))
            case NotEqPredicate(l, r) =>
                registry.predicates
                    .get(PredicateName.NotEq)
                    .exists(_.evaluate(PredicateArgs.NotEq(l, r), captures))
            case NotMatchPredicate(arg, regex) =>
                registry.predicates
                    .get(PredicateName.NotMatch)
                    .exists(_.evaluate(PredicateArgs.NotMatch(arg, regex), captures))

/** Typed argument bundle handed to a [[PredicateImpl]]. Each built-in predicate has a dedicated variant that preserves
  * arity and type information — no `List[PredicateArg]` / arity checking at runtime.
  */
sealed trait PredicateArgs derives CanEqual

object PredicateArgs:
    final case class Eq(left: PredicateArg, right: PredicateArg)           extends PredicateArgs derives CanEqual
    final case class Match(arg: PredicateArg, regex: RegexPattern)         extends PredicateArgs derives CanEqual
    final case class NotEq(left: PredicateArg, right: PredicateArg)        extends PredicateArgs derives CanEqual
    final case class NotMatch(arg: PredicateArg, regex: RegexPattern)      extends PredicateArgs derives CanEqual
    final case class Custom(name: PredicateName, args: List[PredicateArg]) extends PredicateArgs derives CanEqual

/** Pluggable implementation of a named predicate. */
trait PredicateImpl:
    def evaluate[T](args: PredicateArgs, captures: Map[CaptureName, T])(using qt: QueryableTree[T]): Boolean

/** A lookup table of predicate names to implementations. */
final case class PredicateRegistry(predicates: Map[PredicateName, PredicateImpl]):

    /** Register or override a predicate under `name`. */
    def withPredicate(name: PredicateName, impl: PredicateImpl): PredicateRegistry =
        PredicateRegistry(predicates.updated(name, impl))

object PredicateRegistry:

    /** Default registry with `#eq?` and `#match?` for text-based comparison. */
    val default: PredicateRegistry = PredicateRegistry(
        Map(
            PredicateName.Eq    -> EqImpl,
            PredicateName.Match -> MatchImpl,
            PredicateName.NotEq -> NotEqImpl,
            PredicateName.NotMatch -> NotMatchImpl
        )
    )

private object EqImpl extends PredicateImpl:

    def evaluate[T](args: PredicateArgs, captures: Map[CaptureName, T])(using qt: QueryableTree[T]): Boolean =
        args match
            case PredicateArgs.Eq(l, r) =>
                (resolveText(l, captures), resolveText(r, captures)) match
                    case (Some(a), Some(b)) => a == b
                    case _                  => false
            case _ => false

    private def resolveText[T](
        arg: PredicateArg,
        captures: Map[CaptureName, T]
    )(using qt: QueryableTree[T]): Option[String] =
        arg match
            case CaptureRef(name) => captures.get(name).flatMap(qt.text)
            case StringArg(v)     => Some(v)

private object MatchImpl extends PredicateImpl:

    def evaluate[T](args: PredicateArgs, captures: Map[CaptureName, T])(using qt: QueryableTree[T]): Boolean =
        args match
            case PredicateArgs.Match(CaptureRef(name), regex) =>
                captures.get(name).flatMap(qt.text).exists(text => regex.compiled.findFirstIn(text).isDefined)
            case _ => false

private object NotEqImpl extends PredicateImpl:

    def evaluate[T](args: PredicateArgs, captures: Map[CaptureName, T])(using qt: QueryableTree[T]): Boolean =
        args match
            case PredicateArgs.NotEq(l, r) =>
                (resolveText(l, captures), resolveText(r, captures)) match
                    case (Some(a), Some(b)) => a != b
                    case _                  => false
            case _ => false

    private def resolveText[T](
        arg: PredicateArg,
        captures: Map[CaptureName, T]
    )(using qt: QueryableTree[T]): Option[String] =
        arg match
            case CaptureRef(name) => captures.get(name).flatMap(qt.text)
            case StringArg(v)     => Some(v)

private object NotMatchImpl extends PredicateImpl:

    def evaluate[T](args: PredicateArgs, captures: Map[CaptureName, T])(using qt: QueryableTree[T]): Boolean =
        args match
            case PredicateArgs.NotMatch(CaptureRef(name), regex) =>
                captures
                    .get(name)
                    .flatMap(qt.text)
                    .exists(text => regex.compiled.findFirstIn(text).isEmpty)
            case _ => false
