#!/usr/bin/env bash
# Pinned sbt launcher. Uses the same project/build.properties as installed sbt.
set -euo pipefail
cd "$(dirname "$0")"
version="$(sed -n 's/^sbt.version=//p' project/build.properties)"
cache="${XDG_CACHE_HOME:-$HOME/.cache}/krueger/sbt"
launcher="$cache/sbt-launch-$version.jar"
mkdir -p "$cache"
expected="2069338b22ed795a3b65615da0858e5b8733fe3f9a3f73b2fcfd559421e11374"
verify() {
  if command -v sha256sum >/dev/null 2>&1; then
    [ "$(sha256sum "$1" | cut -d ' ' -f 1)" = "$expected" ]
  else
    [ "$(shasum -a 256 "$1" | cut -d ' ' -f 1)" = "$expected" ]
  fi
}
if [ ! -f "$launcher" ] || ! verify "$launcher"; then
  temporary="$(mktemp "$cache/launcher.XXXXXX")"
  trap 'rm -f "$temporary"' EXIT
  curl --fail --location --silent --show-error "https://repo.maven.apache.org/maven2/org/scala-sbt/sbt-launch/$version/sbt-launch-$version.jar" -o "$temporary"
  verify "$temporary" || { echo "sbt launcher checksum mismatch" >&2; exit 1; }
  mv -f "$temporary" "$launcher"
fi
# This launcher runs a standalone JVM. Accept --batch for CLI compatibility.
if [ "${1:-}" = "--batch" ]; then shift; fi
exec java ${JAVA_OPTS:-} -Dsbt.supershell=false -Dsbt.log.noformat=true -jar "$launcher" "$@"
