namespace Krueger.Lang.Superpower

open Krueger.Lang.ElmSyntax
open Superpower
open Superpower.Display
open Superpower.Model

module ElmParser =

  type ElmToken =
  | None = 0 
  | Identifier = 0x0001
  | [<Token(Category = "trivia", Description="newline")>]NewLine = 0x0002
  | [<Token(Category = "trivia")>]Indent = 0x0003
  | [<Token(Category = "symbol", Example="{")>]LBrace = 0x0004
  | [<Token(Category = "symbol", Example="}")>]RBrace = 0x0005
  | [<Token(Category = "symbol", Example="[")>]LBracket = 0x0006
  | [<Token(Category = "symbol", Example="]")>]RBracket = 0x0007
  | [<Token(Category = "symbol", Example="(")>]LParen = 0x0008
  | [<Token(Category = "symbol", Example=")")>]RParen = 0x000A
  | [<Token(Category = "symbol", Example=",")>]Comma = 0x000B
  | [<Token(Category = "symbol", Example=".")>]Period = 0x000C
  | [<Token(Category = "symbol", Example=":")>]Colon = 0x000D
  | [<Token(Category = "symbol", Example="=")>]Equals = 0x000D
  | [<Token(Category = "keyword", Example="import")>]Import = 0x0020
  | [<Token(Category = "keyword", Example="module")>]Module = 0x0021
  | [<Token(Category = "keyword", Example="exposing")>]Exposing = 0x0022
  | [<Token(Category = "keyword", Example="type")>]Type = 0x0023
  | [<Token(Category = "keyword", Example="alias")>]Alias = 0x0024
  | [<Token(Category = "keyword", Example="let")>]Let = 0x0025
  | [<Token(Category = "keyword", Example="in")>]In = 0x0026
  | [<Token(Category = "keyword", Example="case")>]Case = 0x0027
  | [<Token(Category = "keyword", Example="of")>]Of = 0x0028

  let keywords =
      [
        ("alias", ElmToken.Alias)
        ("import", ElmToken.Import)
        ("exposing", ElmToken.Exposing)
        ("type", ElmToken.Type)
      ] |> Map.ofList

  type ElmTokenizer() =
    inherit Tokenizer<ElmToken>()

    override x.Tokenize(span:TextSpan, state: TokenizationState<ElmToken>) : Result<ElmToken> seq =
      Seq.empty
