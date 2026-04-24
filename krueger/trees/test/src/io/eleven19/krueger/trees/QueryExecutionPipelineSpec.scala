package io.eleven19.krueger.trees

import zio.test.*

import io.eleven19.krueger.trees.ToyTree.*
import io.eleven19.krueger.trees.query.*

object QueryExecutionPipelineSpec extends ZIOSpecDefault:

    private val namedType: NodeTypeName = NodeTypeName.make("Named").toOption.get
    private val leafType: NodeTypeName  = NodeTypeName.make("Leaf").toOption.get
    private val n: CaptureName          = CaptureName.make("n").toOption.get
    private val missing: CaptureName    = CaptureName.make("missing").toOption.get
    private val nameField: FieldName    = FieldName.make("name").toOption.get

    private val tree: ToyTree =
        Named(Leaf("main"), Leaf("42"))

    def spec = suite("QueryExecutionPipeline")(
        test("run executes normalize/analyze/validate/lower/execute with deterministic logs") {
            val query = Query(
                NodePattern(namedType, List(FieldPattern(nameField, NodePattern(leafType, Nil, Nil, Some(n)))), Nil, None),
                Nil
            )
            val run = QueryExecutionPipeline.run[Int, ToyTree](query, tree, initialContext = 1)
            assertTrue(
                run.value.isRight,
                run.logs == Vector("normalize", "analyze", "validate", "lower", "execute"),
                run.value.toOption.exists(_.analysis.captureCount == 1),
                run.value.toOption.exists(_.matches.nonEmpty)
            )
        },
        test("validate accumulates unknown-capture diagnostics as errors") {
            val query = Query(
                NodePattern(namedType, Nil, Nil, None),
                List(EqPredicate(CaptureRef(missing), StringArg("x")))
            )
            val run = QueryExecutionPipeline.run[Int, ToyTree](query, tree, initialContext = 0)
            assertTrue(
                run.value.isLeft,
                run.errors.exists(_.contains("@missing"))
            )
        }
    )
