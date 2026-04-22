package io.eleven19.krueger.itest.steps

import io.cucumber.scala.{EN, ScalaDsl}

import io.eleven19.krueger.cst.*
import io.eleven19.krueger.itest.TestDriver

class ExpressionSteps(driver: TestDriver) extends ScalaDsl with EN:

    private def valueBody(driver: TestDriver, name: String): CstExpression =
        driver.cst.declarations.collectFirst {
            case v: CstValueDeclaration if v.name.value == name => v.body
        } match
            case Some(b) => b
            case None    => throw new AssertionError(s"no value named [$name]")

    Then("value {string} has an integer body {int}") { (name: String, value: Int) =>
        valueBody(driver, name) match
            case lit: CstIntLiteral =>
                assert(lit.value == value.toLong, s"expected Int($value), got Int(${lit.value})")
            case other =>
                throw new AssertionError(s"expected CstIntLiteral, got [${other.getClass.getSimpleName}]")
    }

    Then("value {string} has a float body") { (name: String) =>
        valueBody(driver, name) match
            case _: CstFloatLiteral => ()
            case other =>
                throw new AssertionError(s"expected CstFloatLiteral, got [${other.getClass.getSimpleName}]")
    }

    Then("value {string} has a unit body") { (name: String) =>
        valueBody(driver, name) match
            case _: CstUnitLiteral => ()
            case other =>
                throw new AssertionError(s"expected CstUnitLiteral, got [${other.getClass.getSimpleName}]")
    }

    Then("value {string} has a list body of {int} elements") { (name: String, count: Int) =>
        valueBody(driver, name) match
            case lit: CstListLiteral =>
                assert(lit.elements.size == count, s"expected $count elements, got ${lit.elements.size}")
            case other =>
                throw new AssertionError(s"expected CstListLiteral, got [${other.getClass.getSimpleName}]")
    }

    Then("value {string} has a record body with {int} fields") { (name: String, count: Int) =>
        valueBody(driver, name) match
            case lit: CstRecordLiteral =>
                assert(lit.fields.size == count, s"expected $count fields, got ${lit.fields.size}")
            case other =>
                throw new AssertionError(s"expected CstRecordLiteral, got [${other.getClass.getSimpleName}]")
    }
