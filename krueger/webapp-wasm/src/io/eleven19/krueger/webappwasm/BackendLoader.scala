package io.eleven19.krueger.webappwasm

object BackendLoader:
    private var cached: Option[CompilerBackend] = None

    def current(): CompilerBackend =
        cached.getOrElse {
            val backend = WebGcBackend.load()
            cached = Some(backend)
            backend
        }
