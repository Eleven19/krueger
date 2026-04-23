package io.eleven19.krueger.trees.query

import scala.annotation.tailrec

/** Root of the query AST. A parsed query is one pattern plus zero or more predicates that constrain captured nodes. */
final case class Query(root: Pattern, predicates: List[Predicate]) derives CanEqual:

    /** Every capture name bound anywhere in the root pattern.
      *
      * Useful for validation (e.g. confirming every `@foo` referenced by a predicate is actually bound by the pattern).
      */
    def captureNames: Set[String] =
        @tailrec
        def go(stack: List[Pattern], acc: Set[String]): Set[String] = stack match
            case Nil => acc
            case p :: rest =>
                val withOwn = p.capture.fold(acc)(acc + _)
                p match
                    case NodePattern(_, fields, _) =>
                        go(fields.map(_.pattern) ::: rest, withOwn)
                    case _: WildcardPattern =>
                        go(rest, withOwn)
        go(List(root), Set.empty)

/** A tree pattern: either a node pattern with an optional list of named sub-patterns, or a wildcard that matches any
  * node.
  */
sealed trait Pattern derives CanEqual:
    /** Optional capture name bound to the node that matches this pattern. */
    def capture: Option[String]

final case class NodePattern(
    nodeType: String,
    fieldPatterns: List[FieldPattern],
    capture: Option[String]
) extends Pattern derives CanEqual

final case class WildcardPattern(capture: Option[String]) extends Pattern derives CanEqual

/** A named sub-pattern attached to a parent NodePattern. */
final case class FieldPattern(name: String, pattern: Pattern) derives CanEqual

/** A predicate clause that constrains captured nodes after structural matching. */
sealed trait Predicate derives CanEqual

final case class EqPredicate(left: PredicateArg, right: PredicateArg) extends Predicate derives CanEqual
final case class MatchPredicate(arg: PredicateArg, regex: String)     extends Predicate derives CanEqual

/** Argument supplied to a predicate: a reference to a capture or a string literal. */
sealed trait PredicateArg derives CanEqual

final case class CaptureRef(name: String) extends PredicateArg derives CanEqual
final case class StringArg(value: String) extends PredicateArg derives CanEqual

/** One successful match: the node that matched the root pattern plus every capture binding collected along the way. */
final case class Match[T](root: T, captures: Map[String, T]) derives CanEqual
