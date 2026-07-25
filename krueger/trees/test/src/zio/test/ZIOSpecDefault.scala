package zio.test

import kyo.Abort
import kyo.Async
import kyo.Frame
import kyo.Scope
import kyo.kernel.<
import kyo.test.AssertScope

abstract class ZIOSpecDefault extends kyo.test.Test[Any]:
    protected sealed trait SpecNode
    protected final case class SuiteNode(name: String, children: Vector[SpecNode]) extends SpecNode
    protected final case class TestNode(register: () => Unit) extends SpecNode

    protected def spec: SpecNode

    register(spec)

    private def register(node: SpecNode): Unit =
        node match
            case SuiteNode(name, children) =>
                name - {
                    children.foreach(register)
                }
            case TestNode(registerLeaf) =>
                registerLeaf()

    protected final def suite(name: String)(cases: SpecNode*): SpecNode =
        SuiteNode(name, cases.toVector)

    protected final def test(name: String)(body: AssertScope ?=> Unit < (Async & Abort[Any] & Scope))(using Frame): SpecNode =
        TestNode(() =>
            name in {
                body
            })

    protected def assertTrue(conditions: Boolean*): Unit =
        if !conditions.forall(identity) then
            throw new AssertionError("assertTrue failed")

    protected def assertCompletes: Unit = ()
