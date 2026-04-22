package io.eleven19.krueger.lexer

import parsley.{Failure, Success}
import zio.test.*

import io.eleven19.krueger.lexer.ElmLexer.*

object ElmLexerSpec extends ZIOSpecDefault:

    private def ok[A](r: parsley.Result[String, A]): Boolean = r match
        case Success(_) => true
        case Failure(_) => false

    private def valueOf[A](r: parsley.Result[String, A]): A = r match
        case Success(a)   => a
        case Failure(msg) => throw new AssertionError(s"lex failed: $msg")

    def spec = suite("ElmLexer")(
        suite("identifiers")(
            test("lowerIdentifier parses lowercase names") {
                assertTrue(ok(fully(lowerIdentifier).parse("foo")))
            },
            test("lowerIdentifier accepts underscore prefix") {
                assertTrue(ok(fully(lowerIdentifier).parse("_bar")))
            },
            test("lowerIdentifier rejects uppercase names") {
                assertTrue(!ok(fully(lowerIdentifier).parse("Foo")))
            },
            test("upperIdentifier parses capitalised names") {
                assertTrue(ok(fully(upperIdentifier).parse("Foo")))
            },
            test("upperIdentifier rejects lowercase names") {
                assertTrue(!ok(fully(upperIdentifier).parse("foo")))
            },
            test("identifier rejects keyword 'module'") {
                assertTrue(!ok(fully(identifier).parse("module")))
            }
        ),
        suite("operators")(
            test("operator parses a user-defined operator") {
                assertTrue(ok(fully(operator).parse(":=:")))
            }
        ),
        suite("keywords and symbols")(
            test("keyword matches a declared keyword") {
                assertTrue(ok(fully(keyword("module")).parse("module")))
            },
            test("symbol matches a declared operator") {
                assertTrue(ok(fully(symbol("->")).parse("->")))
            }
        ),
        suite("numeric literals")(
            test("intLiteral parses decimals") {
                assertTrue(valueOf(fully(intLiteral).parse("42")) == 42L)
            },
            test("floatLiteral parses decimal floats") {
                val v = valueOf(fully(floatLiteral).parse("3.14"))
                assertTrue(math.abs(v - 3.14) < 1e-9)
            }
        ),
        suite("text literals")(
            test("stringLiteral parses quoted text") {
                assertTrue(valueOf(fully(stringLiteral).parse("\"hello\"")) == "hello")
            },
            test("charLiteral parses quoted characters") {
                assertTrue(valueOf(fully(charLiteral).parse("'x'")) == 'x')
            }
        ),
        suite("enclosers")(
            test("parens wraps inner parser") {
                assertTrue(ok(fully(parens(intLiteral)).parse("(1)")))
            },
            test("brackets wraps inner parser") {
                assertTrue(ok(fully(brackets(intLiteral)).parse("[1]")))
            },
            test("braces wraps inner parser") {
                assertTrue(ok(fully(braces(intLiteral)).parse("{1}")))
            }
        ),
        suite("comma separators")(
            test("commaSep parses an empty list") {
                assertTrue(valueOf(fully(commaSep(intLiteral)).parse("")) == List.empty[Long])
            },
            test("commaSep parses multiple items") {
                assertTrue(valueOf(fully(commaSep(intLiteral)).parse("1, 2, 3")) == List(1L, 2L, 3L))
            },
            test("commaSep1 requires at least one item") {
                assertTrue(!ok(fully(commaSep1(intLiteral)).parse("")))
            }
        ),
        suite("whitespace and comments")(
            test("line comments are skipped") {
                assertTrue(valueOf(fully(intLiteral).parse("42 -- trailing comment\n")) == 42L)
            },
            test("nested block comments are skipped") {
                assertTrue(valueOf(fully(intLiteral).parse("42 {- outer {- inner -} still outer -}")) == 42L)
            }
        )
    )
