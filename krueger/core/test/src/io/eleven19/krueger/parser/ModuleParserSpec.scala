package io.eleven19.krueger.parser

import parsley.{Failure, Success}
import zio.test.*

import io.eleven19.krueger.cst.*

object ModuleParserSpec extends ZIOSpecDefault:

    private def parseOrFail(src: String): CstModule =
        ModuleParser.module.parse(src) match
            case Success(m)   => m
            case Failure(msg) => throw new AssertionError(s"parse failed: $msg\nSource:\n$src")

    def spec = suite("ModuleParser")(
        test("parses minimal plain module") {
            val m = parseOrFail("module Main exposing (..)\n")
            assertTrue(
                m.moduleDecl.moduleType == ModuleType.Plain,
                m.moduleDecl.name.parts.map(_.value) == List("Main"),
                m.moduleDecl.exposing.isInstanceOf[CstExposingAll]
            )
        },
        test("parses port module") {
            val m = parseOrFail("port module Ports exposing (..)\n")
            assertTrue(m.moduleDecl.moduleType == ModuleType.Port)
        },
        test("parses effect module") {
            val m = parseOrFail("effect module Eff exposing (..)\n")
            assertTrue(m.moduleDecl.moduleType == ModuleType.Effect)
        },
        test("parses qualified module name") {
            val m = parseOrFail("module Data.List exposing (..)\n")
            assertTrue(m.moduleDecl.name.parts.map(_.value) == List("Data", "List"))
        },
        test("parses plain import") {
            val m = parseOrFail("module M exposing (..)\nimport List\n")
            assertTrue(m.imports.map(_.moduleName.parts.map(_.value)) == List(List("List")))
        },
        test("parses import with alias") {
            val m = parseOrFail("module M exposing (..)\nimport Data.List as L\n")
            assertTrue(
                m.imports.head.alias.map(_.value).contains("L"),
                m.imports.head.moduleName.parts.map(_.value) == List("Data", "List")
            )
        },
        test("parses import with exposing list") {
            val m = parseOrFail("module M exposing (..)\nimport Html exposing (text, div)\n")
            val items = m.imports.head.exposing match
                case Some(e: CstExposingExplicit) => e.items.collect { case v: CstExposedValue => v.name.value }
                case _                            => Nil
            assertTrue(items == List("text", "div"))
        },
        test("parses annotated value declaration without consuming the value name as a type argument") {
            val m = parseOrFail("module M exposing (..)\nfoo : Int\nfoo = 42\n")
            val decl = m.declarations.head match
                case v: CstValueDeclaration => v
                case other                  => throw new AssertionError(s"expected value declaration, got $other")
            assertTrue(
                decl.name.value == "foo",
                decl.annotation.exists(_.name.value == "foo"),
                decl.annotation.exists(_.typeExpr.isInstanceOf[CstTypeReference]),
                decl.body == CstIntLiteral(42L)(decl.body.span)
            )
        },
        test("fails on malformed module header") {
            ModuleParser.module.parse("module !!!") match
                case Failure(_) => assertCompletes
                case Success(_) => throw new AssertionError("expected failure")
        }
    )
