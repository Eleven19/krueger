Feature: Comment parsing

  Scenario: Distinguishes doc comments from regular comments
    Given the Elm source:
      """
      module App exposing (..)

      -- regular line
      {- regular block -}
      {-| module docs -}
      main = "-- not a comment"
      """
    When the source is parsed
    Then the module has 3 comments
    And comment 1 is a "line" comment with text "regular line"
    And comment 2 is a "block" comment with text "regular block"
    And comment 3 is a "doc" comment with text "module docs"
