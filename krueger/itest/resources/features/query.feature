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
