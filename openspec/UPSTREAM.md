# Upstream sources

The `html-design` schema, its templates and the shared HTML document assets
under `docs/templates/` come from
[finos/morphir-moonbit](https://github.com/finos/morphir-moonbit/tree/db639506585e38326810722d4951e71f43c6c4f8/openspec),
commit `db639506585e38326810722d4951e71f43c6c4f8`, under the
[Apache License 2.0](LICENSE). The upstream notice is preserved in [NOTICE](NOTICE).

Krueger changes the schema's tracker instructions, language examples and testing
guidance. Its HTML templates load Scala and Elm grammars, and its highlighting
setup removes the MoonBit alias. The shared stylesheet is unchanged.

OpenSpec 1.14.3 generated the `openspec-*` skills under `.agents/skills/`,
`.claude/skills/`, `.github/skills/`, `.cursor/skills/`, `.grok/skills/`,
`.devin/skills/`, `.gemini/skills/` and `.opencode/skills/`, the same agent set
as morphir-moonbit. Regenerate them with `openspec update`;
put project-specific guidance in the config, schema and agent instruction files.

highlight.js has separate provenance and licensing in
[`docs/templates/assets/highlight/UPSTREAM.md`](../docs/templates/assets/highlight/UPSTREAM.md).
