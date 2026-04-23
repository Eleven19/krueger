package io.eleven19.krueger.trees

import zio.test.*

object TreesSpec extends ZIOSpecDefault:

    def spec = suite("Trees")(
        test("module marker is set") {
            assertTrue(Trees.moduleName == "krueger-trees")
        }
    )
