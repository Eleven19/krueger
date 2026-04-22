package io.eleven19.krueger.parser

import parsley.{Failure, Success}
import zio.test.*

object MorphirCorpusSpec extends ZIOSpecDefault:

    private case class Fixture(repo: String, path: String, source: String, expectedDeclarations: Int):
        val name: String = s"$repo/$path"

    private val fixtures: List[Fixture] = List(
        Fixture(
            "finos/morphir-examples",
            "tutorial/step_1_first_logic/src/Morphir/Example/App/Rentals.elm",
            """module Morphir.Example.App.Rentals exposing (..)
              |
              |
              |request : Int -> Int -> Result String Int
              |request availability requestedQuantity =
              |    if requestedQuantity <= availability then
              |        Ok requestedQuantity
              |
              |    else
              |        Err "Insufficient availability"
              |""".stripMargin,
            1
        ),
        Fixture(
            "finos/morphir-examples",
            "tutorial/step_2_business_language/src/Morphir/Example/App/BusinessTerms.elm",
            """module Morphir.Example.App.BusinessTerms exposing (..)
              |
              |{-| The number of items in stock.
              |-}
              |
              |
              |type alias CurrentInventory =
              |    Int
              |
              |
              |{-| The quantity granted for a reservation. Depending on availability, this might be less than the requested amount.
              |-}
              |type alias ReservationQuantity =
              |    Int
              |
              |
              |{-| Expertise level of renters. Important for deciding whether conditions are safe enough to rent.
              |-}
              |type ExpertiseLevel
              |    = Novice
              |    | Intermediate
              |    | Expert
              |
              |
              |{-| Reason codes for rejecting a reservation request.
              |-}
              |type Reason
              |    = InsufficientAvailability
              |    | ClosedDueToConditions
              |""".stripMargin,
            4
        ),
        Fixture(
            "finos/morphir-examples",
            "tutorial/step_2_business_language/src/Morphir/Example/App/Forecast.elm",
            """module Morphir.Example.App.Forecast exposing (..)
              |
              |{-| Forecast represents the API from an external weather forecast provider.
              |-}
              |
              |
              |type alias MPH =
              |    Int
              |
              |
              |type WindDirection
              |    = North
              |    | South
              |    | East
              |    | West
              |
              |
              |type alias Celcius =
              |    Int
              |
              |
              |type ForecastDetail
              |    = Showers
              |    | Thunderstorms
              |    | Snow
              |    | Fog
              |
              |
              |type alias ForecastPercent =
              |    Float
              |
              |
              |type alias Forecast =
              |    { temp : { low : Celcius, high : Celcius }
              |    , windSpeed : { min : MPH, max : MPH }
              |    , windDirection : WindDirection
              |    , shortForcast : ForecastDetail
              |    , forecastPercent : ForecastPercent
              |    }
              |""".stripMargin,
            6
        ),
        Fixture(
            "finos/morphir-examples",
            "tutorial/step_2_business_language/src/Morphir/Example/App/Analytics.elm",
            """module Morphir.Example.App.Analytics exposing (..)
              |
              |import Morphir.Example.App.BusinessTerms exposing (..)
              |
              |
              |{-| Calculates the probable reservations by applying the historical cancelation ratio to current reservations.
              |-}
              |probableReservations : ReservationQuantity -> CanceledQuantity -> ReservationQuantity -> ProbableReservations
              |probableReservations averageReservationRequests averageCancelations currentReservationCount =
              |    let
              |        cancelationRatio : CancelationRatio
              |        cancelationRatio =
              |            toFloat averageCancelations / toFloat averageReservationRequests
              |
              |        result : ProbableReservations
              |        result =
              |            ceiling (toFloat currentReservationCount * (1.0 - cancelationRatio))
              |    in
              |    result
              |""".stripMargin,
            1
        ),
        Fixture(
            "finos/morphir-elm",
            "src/Morphir/File/Path.elm",
            """module Morphir.File.Path exposing (..)
              |
              |
              |type alias Path =
              |    String
              |""".stripMargin,
            1
        ),
        Fixture(
            "finos/morphir-elm",
            "tests-integration/typespec/model/src/TestModel/BasicTypes.elm",
            """module TestModel.BasicTypes exposing (..)
              |
              |
              |type alias IsApplicable =
              |    Bool
              |
              |
              |type alias Pi =
              |    Float
              |
              |
              |type alias Age =
              |    Int
              |
              |
              |type alias Fullname =
              |    String
              |
              |
              |type alias Grade =
              |    Char
              |
              |
              |type alias SingleTypeArg a =
              |    a
              |""".stripMargin,
            6
        ),
        Fixture(
            "finos/morphir-elm",
            "tests-integration/json-schema/model/src/TestModel/TupleTypes.elm",
            """module TestModel.TupleTypes exposing (..)
              |
              |
              |type alias Location =
              |    ( Int, Int )
              |
              |
              |type alias Cordinates =
              |    ( Float, Float, Float )
              |""".stripMargin,
            2
        ),
        Fixture(
            "finos/morphir-elm",
            "tests-integration/reference-model/src/Morphir/Reference/Model/Issues/Issue350.elm",
            """module Morphir.Reference.Model.Issues.Issue350 exposing (..)
              |
              |
              |functionExample : List ( a, Float )
              |functionExample =
              |    []
              |""".stripMargin,
            1
        )
    )

    def spec = suite("Morphir corpus fixtures")(
        fixtures.map { fixture =>
            test(s"parses ${fixture.name}") {
                ModuleParser.module.parse(fixture.source) match
                    case Success(module) =>
                        assertTrue(module.declarations.length == fixture.expectedDeclarations)
                    case Failure(msg) =>
                        throw new AssertionError(s"failed to parse ${fixture.name}: $msg")
            }
        }*
    )
