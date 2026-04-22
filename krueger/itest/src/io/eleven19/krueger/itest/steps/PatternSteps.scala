package io.eleven19.krueger.itest.steps

import io.cucumber.scala.{EN, ScalaDsl}

import io.eleven19.krueger.cst.*
import io.eleven19.krueger.itest.TestDriver

class PatternSteps(driver: TestDriver) extends ScalaDsl with EN:

    private def valueParams(driver: TestDriver, name: String): Vector[CstPattern] =
        driver.cst.declarations.collectFirst {
            case v: CstValueDeclaration if v.name.value == name => v.patterns
        } match
            case Some(ps) => ps
            case None     => throw new AssertionError(s"no value named [$name]")

    Then("value {string} has {int} parameter(s)") { (name: String, count: Int) =>
        val actual = valueParams(driver, name).size
        assert(actual == count, s"expected $count parameters for [$name], got $actual")
    }
