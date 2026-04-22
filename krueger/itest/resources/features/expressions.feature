Feature: Expressions
  Krueger parses Elm expressions appearing as declaration bodies.

  Scenario: Integer literal body
    Given the Elm source:
      """
      module M exposing (..)

      x = 42
      """
    When the source is parsed
    Then declaration 1 is a value named "x"
    And value "x" has an integer body 42

  Scenario: Float literal body
    Given the Elm source:
      """
      module M exposing (..)

      pi = 3.14
      """
    When the source is parsed
    Then declaration 1 is a value named "pi"
    And value "pi" has a float body

  Scenario: Unit literal body
    Given the Elm source:
      """
      module M exposing (..)

      u = ()
      """
    When the source is parsed
    Then declaration 1 is a value named "u"
    And value "u" has a unit body

  Scenario: List literal body
    Given the Elm source:
      """
      module M exposing (..)

      xs = [1, 2, 3]
      """
    When the source is parsed
    Then declaration 1 is a value named "xs"
    And value "xs" has a list body of 3 elements

  Scenario: Record literal body
    Given the Elm source:
      """
      module M exposing (..)

      rec = { x = 1, y = 2 }
      """
    When the source is parsed
    Then declaration 1 is a value named "rec"
    And value "rec" has a record body with 2 fields
