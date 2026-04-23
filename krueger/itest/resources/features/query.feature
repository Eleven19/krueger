Feature: Tree queries
  Krueger ships a tree-sitter-inspired query DSL that selects
  CST or AST nodes by pattern, with captures and predicates.

  Scenario: CST query surfaces a single value declaration
    Given the Elm source:
      """
      module M exposing (..)

      main = 42
      """
    When the CST is queried with "(CstValueDeclaration name: (CstName) @n)"
    Then the query matches exactly 1 time
    And capture "n" of match 1 is a "CstName"
    And capture "n" of match 1 has text "main"

  Scenario: CST wildcard matches every node in the tree
    Given the Elm source:
      """
      module M exposing (..)

      main = 42
      """
    When the CST is queried with "_"
    Then the query matches at least 5 times

  Scenario: CST query with a predicate filters captures
    Given the Elm source:
      """
      module M exposing (..)

      main = 42
      """
    When the CST is queried with "(CstName) @n (#eq? @n \"main\")"
    Then the query matches exactly 1 time
    And capture "n" of match 1 has text "main"

  Scenario: CST query with a regex predicate
    Given the Elm source:
      """
      module M exposing (..)

      main = 42
      """
    When the CST is queried with "(CstName) @n (#match? @n \"^m\")"
    Then the query matches at least 1 time

  Scenario: CST query with no matches yields an empty result
    Given the Elm source:
      """
      module M exposing (..)

      main = 42
      """
    When the CST is queried with "(CstFloatLiteral)"
    Then the query has no matches

  Scenario: AST query surfaces the value declaration by name
    Given the Elm source:
      """
      module M exposing (..)

      main = 42
      """
    When the AST is queried with "(ValueDeclaration) @v"
    Then the query matches exactly 1 time
    And capture "v" of match 1 is a "ValueDeclaration"
    And capture "v" of match 1 has text "main"

  Scenario: AST query with a predicate on the captured text
    Given the Elm source:
      """
      module M exposing (..)

      main = 42
      """
    When the AST is queried with "(ValueDeclaration) @v (#eq? @v \"main\")"
    Then the query matches exactly 1 time

  Scenario: Predicate capture must be bound
    Given the Elm source:
      """
      module M exposing (..)

      main = 42
      """
    When the CST is queried with "(CstName) @n (#eq? @missing \"main\")"
    Then the query fails with message containing "@missing"

  Scenario: CST query supports ordered unfielded child patterns
    Given the Elm source:
      """
      module M exposing (..)

      main = 42
      """
    When the CST is queried with "(CstValueDeclaration (CstName) @n (CstIntLiteral) @i)"
    Then the query matches exactly 1 time
    And capture "n" of match 1 has text "main"
    And capture "i" of match 1 has text "42"

  Scenario: CST query with multiple top-level patterns
    Given the Elm source:
      """
      module M exposing (..)

      main = 42
      """
    When the CST is queried with "(CstName) (CstIntLiteral)"
    Then the query matches at least 2 times

  Scenario: Multi-pattern query can include predicates
    Given the Elm source:
      """
      module M exposing (..)

      main = 42
      """
    When the CST is queried with "(CstName) @n (CstIntLiteral) (#eq? @n \"main\")"
    Then the query matches at least 1 time

  Scenario: Unknown capture in multi-pattern query fails query parse
    Given the Elm source:
      """
      module M exposing (..)

      main = 42
      """
    When the CST is queried with "(CstName) @n (CstIntLiteral) (#eq? @missing \"x\")"
    Then the query fails with message containing "@missing"

  Scenario: Malformed query with unmatched paren fails with stable parse prefix
    Given the Elm source:
      """
      module M exposing (..)

      main = 42
      """
    When the CST is queried with "(CstName"
    Then the query fails with message containing "Query parse failed:"

  Scenario: Malformed predicate arity fails with stable parse prefix
    Given the Elm source:
      """
      module M exposing (..)

      main = 42
      """
    When the CST is queried with "(CstName) @n (#eq? @n)"
    Then the query fails with message containing "Query parse failed:"

  Scenario: Predicate argument kind mismatch fails query parse
    Given the Elm source:
      """
      module M exposing (..)

      main = 42
      """
    When the CST is queried with "(CstName) @n (#match? \"main\" \"^m\")"
    Then the query fails with message containing "Query parse failed:"

  Scenario: Unknown predicate fails with explicit predicate diagnostic
    Given the Elm source:
      """
      module M exposing (..)

      main = 42
      """
    When the CST is queried with "(CstName) @n (#foo? @n \"main\")"
    Then the query fails with message containing "#foo?"
