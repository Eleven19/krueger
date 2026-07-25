package io.eleven19.krueger.trees

import kyo.test.*

class TreesSpec extends Test[Any]:

    "Trees" - {
        "module marker is set" in
            assert(Trees.moduleName == "krueger-trees")
    }
