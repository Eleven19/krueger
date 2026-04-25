package io.eleven19.krueger.webapp

import zio.test.*

object AppInfoSpec extends ZIOSpecDefault:

    def spec = suite("AppInfo")(
        test("name is krueger-webapp") {
            assertTrue(AppInfo.name == "krueger-webapp")
        },
        test("version is non-empty") {
            assertTrue(AppInfo.version.nonEmpty)
        }
    )
