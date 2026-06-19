package io.eleven19.krueger.compiler

final case class DiagnosticContextLine(
    line: Int,
    text: String,
    isErrorLine: Boolean
) derives CanEqual
