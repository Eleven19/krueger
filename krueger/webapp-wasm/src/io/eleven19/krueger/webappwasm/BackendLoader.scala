package io.eleven19.krueger.webappwasm

/** Lazy registry of compile backends keyed by stable id strings.
  *
  * The two ids `"webgc"` and `"js"` are the contract surfaced through the JS facade in [[KruegerJs.setBackend]] /
  * [[KruegerJs.currentBackend]] and the SvelteKit playground's backend selector. Both backends currently link the same
  * `compiler-api.js` Scala.js compiler — the WASM-backed `webgc` variant is wired up here so the playground can default
  * to it when WebAssembly GC is supported and surface a deterministic string id, but the actual delegation to
  * `compiler-api.wasm` is tracked as separate follow-up work.
  *
  * Backends are constructed on first use and cached. Switching back and forth is therefore cheap once both have been
  * touched.
  */
object BackendLoader:

    /** Default backend id; matches the SvelteKit playground default when WebAssembly GC is supported.
      */
    val defaultId: String = "webgc"

    private val ids: Set[String] = Set("webgc", "js")

    private var cached: Map[String, CompilerBackend] = Map.empty
    private var activeId: String                     = defaultId

    /** Currently-selected backend id (one of [[ids]]). */
    def currentId(): String = activeId

    /** Resolve the active backend, instantiating it on first use. */
    def current(): CompilerBackend = backendFor(activeId)

    /** Switch the active backend.
      *
      * @return
      *   `true` if `id` is a known backend (after which subsequent [[current]] calls return that backend); `false` if
      *   the id is unknown, in which case the active backend is left unchanged.
      */
    def setBackend(id: String): Boolean =
        if !ids.contains(id) then false
        else
            activeId = id
            // Eagerly construct so failures surface synchronously at switch
            // time rather than the next compile call.
            val _ = backendFor(id)
            true

    /** Test hook — wipe the cache so each test starts on a clean slate. */
    private[webappwasm] def resetForTesting(): Unit =
        cached = Map.empty
        activeId = defaultId

    private def backendFor(id: String): CompilerBackend =
        cached.getOrElse(
            id, {
                val backend = id match
                    case "webgc" => WebGcBackend.load()
                    case "js"    => JsBackend.load()
                    case other   =>
                        // setBackend rejects unknown ids; this branch only
                        // fires if the caller bypassed it (e.g. in tests).
                        throw new IllegalStateException(s"unknown backend id: $other")
                cached = cached.updated(id, backend)
                backend
            }
        )
