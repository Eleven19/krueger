package io.eleven19.krueger.itest.steps

import io.cucumber.scala.{EN, ScalaDsl}

import io.eleven19.krueger.itest.TestDriver

class QuerySteps(driver: TestDriver) extends ScalaDsl with EN:
    private var lastQueryFailure: Option[AssertionError] = None

    private def runAndCaptureFailure(run: => Unit): Unit =
        try
            run
            lastQueryFailure = None
        catch
            case ae: AssertionError =>
                lastQueryFailure = Some(ae)

    private def assertNoQueryFailure(): Unit =
        assert(lastQueryFailure.isEmpty, s"query failed unexpectedly: ${lastQueryFailure.map(_.getMessage).getOrElse("")}")

    When("the CST is queried with {string}") { (queryText: String) =>
        runAndCaptureFailure(driver.queryCst(queryText))
    }

    When("the AST is queried with {string}") { (queryText: String) =>
        runAndCaptureFailure(driver.queryAst(queryText))
    }

    When("the CST is queried with:") { (queryText: String) =>
        runAndCaptureFailure(driver.queryCst(queryText))
    }

    When("the AST is queried with:") { (queryText: String) =>
        runAndCaptureFailure(driver.queryAst(queryText))
    }

    Then("the query matches exactly {int} time(s)") { (count: Int) =>
        assertNoQueryFailure()
        val actual = driver.lastMatches.size
        assert(actual == count, s"expected exactly $count match(es), got $actual")
    }

    Then("the query matches at least {int} time(s)") { (count: Int) =>
        assertNoQueryFailure()
        val actual = driver.lastMatches.size
        assert(actual >= count, s"expected at least $count match(es), got $actual")
    }

    Then("the query has no matches") { () =>
        assertNoQueryFailure()
        val actual = driver.lastMatches.size
        assert(actual == 0, s"expected no matches, got $actual")
    }

    Then("the query fails with message containing {string}") { (needle: String) =>
        val failure = lastQueryFailure.getOrElse(
            throw new AssertionError("expected query to fail, but it succeeded")
        )
        assert(
            failure.getMessage.contains(needle),
            s"""expected query failure message to contain [$needle], got:
               |${failure.getMessage}
               |""".stripMargin
        )
    }

    Then("capture {string} of match {int} is a {string}") {
        (captureName: String, oneBasedIndex: Int, expectedNodeType: String) =>
            assertNoQueryFailure()
            val m = driver.lastMatches(oneBasedIndex - 1)
            val cap = m.captures.getOrElse(
                captureName,
                throw new AssertionError(
                    s"no capture named [$captureName] in match $oneBasedIndex; available: ${m.captures.keySet.mkString(", ")}"
                )
            )
            assert(
                cap.nodeType == expectedNodeType,
                s"expected capture [$captureName] of match $oneBasedIndex to be a $expectedNodeType, got ${cap.nodeType}"
            )
    }

    Then("capture {string} of match {int} has text {string}") {
        (captureName: String, oneBasedIndex: Int, expectedText: String) =>
            assertNoQueryFailure()
            val m = driver.lastMatches(oneBasedIndex - 1)
            val cap = m.captures.getOrElse(
                captureName,
                throw new AssertionError(
                    s"no capture named [$captureName] in match $oneBasedIndex; available: ${m.captures.keySet.mkString(", ")}"
                )
            )
            val actual = cap.text.getOrElse(
                throw new AssertionError(
                    s"capture [$captureName] of match $oneBasedIndex has no text (node type ${cap.nodeType})"
                )
            )
            assert(
                actual == expectedText,
                s"expected capture [$captureName] of match $oneBasedIndex to have text [$expectedText], got [$actual]"
            )
    }

    Then("capture {string} of match {int} has {int} direct child(ren)") {
        (captureName: String, oneBasedIndex: Int, expectedCount: Int) =>
            assertNoQueryFailure()
            val m = driver.lastMatches(oneBasedIndex - 1)
            val cap = m.captures.getOrElse(
                captureName,
                throw new AssertionError(
                    s"no capture named [$captureName] in match $oneBasedIndex; available: ${m.captures.keySet.mkString(", ")}"
                )
            )
            assert(
                cap.childCount == expectedCount,
                s"expected capture [$captureName] of match $oneBasedIndex to have $expectedCount direct child(ren), got ${cap.childCount}"
            )
    }
