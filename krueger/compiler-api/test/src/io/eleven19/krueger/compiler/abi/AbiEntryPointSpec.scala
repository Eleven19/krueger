package io.eleven19.krueger.compiler.abi

import java.nio.charset.StandardCharsets

import zio.test.*

import InvokeJson.decode
import InvokeJson.given

object AbiEntryPointSpec extends ZIOSpecDefault:

    private val utf8 = StandardCharsets.UTF_8

    def spec = suite("AbiEntryPoint")(
        test("happy path: round-trips a UTF-8 parseCst request to a JSON envelope") {
            val bytes = AbiEntryPoint.invokeUtf8(
                "parseCst".getBytes(utf8),
                """{"source":"module Demo exposing (..)\n\nmain = 42\n"}""".getBytes(utf8)
            )
            val response = decode[InvokeResponse](String(bytes, utf8))

            assertTrue(
                response.ok,
                response.errors.isEmpty,
                response.value.exists(_.startsWith("CstModule("))
            )
        },
        test("failure path: returns a structured error for malformed UTF-8 JSON payloads") {
            val bytes = AbiEntryPoint.invokeUtf8(
                "parseCst".getBytes(utf8),
                Array[Byte](0xff.toByte)
            )
            val response = decode[InvokeResponse](String(bytes, utf8))

            assertTrue(
                !response.ok,
                response.value.isEmpty,
                response.errors.exists(error => error.phase == "internal")
            )
        },
        test("determinism path: identical UTF-8 requests return byte-identical JSON") {
            val op      = "parseCst".getBytes(utf8)
            val payload = """{"source":"module Demo exposing (..)\n\nmain = 42\n"}""".getBytes(utf8)

            assertTrue(
                AbiEntryPoint.invokeUtf8(op, payload).toVector ==
                    AbiEntryPoint.invokeUtf8(op, payload).toVector
            )
        }
    )
