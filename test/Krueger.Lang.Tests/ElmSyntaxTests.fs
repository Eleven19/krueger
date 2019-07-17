module ElmSyntaxTests
open Krueger.Lang.ElmSyntax

open Expecto

[<Tests>]
let tests =
    testList "Elm syntax" [
        testCase "When parsing a simple type alias " <| fun _ ->
            let sourceCode = """blah"""
            Expect.equal "blah" sourceCode "These should be equal"
    ]