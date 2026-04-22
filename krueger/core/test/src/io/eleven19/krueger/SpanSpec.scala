package io.eleven19.krueger

import zio.test.*

object SpanSpec extends ZIOSpecDefault:

    def spec = suite("Span")(
        test("zero is (0, 0)") {
            assertTrue(Span.zero == Span(0, 0))
        },
        test("end is offset + length") {
            assertTrue(Span(3, 7).end == 10)
        },
        test("between spans from start offset to end of end span") {
            val a = Span(2, 3)
            val b = Span(10, 2)
            assertTrue(Span.between(a, b) == Span(2, 10))
        }
    )
