namespace Krueger.Lang

[<AutoOpen>]
module ElmSyntax =
    
    type Identifier = Identifier of string

    type Namespace = Namespace of Identifier list

    type Name =
        | Name of Identifier
        | QualifiedName of Namespace * Identifier

    type SyntaxNode =
        | TypeAlias


    module Name =
        let show name = 
            match name with
            | Name (Identifier localName) -> localName
            | QualifiedName (Namespace ns, Identifier localName) -> localName

    type Name with
        member x.Show() = Name.show x