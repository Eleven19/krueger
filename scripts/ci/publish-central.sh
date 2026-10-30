#!/usr/bin/env bash
set -euo pipefail
version="${1:?version is required}"
: "${SONATYPE_USERNAME:?SONATYPE_USERNAME is required}"
: "${SONATYPE_PASSWORD:?SONATYPE_PASSWORD is required}"
: "${PGP_SECRET:?PGP_SECRET is required}"
: "${PGP_PASSPHRASE:?PGP_PASSPHRASE is required}"
if [[ ! "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+([.-][A-Za-z0-9.-]+)?$ ]]; then
  echo 'Invalid publish version' >&2
  exit 1
fi
export PUBLISH_VERSION="$version"
# Isolate imported signing keys from a contributor's normal keyring.
export GNUPGHOME="$(mktemp -d)"
trap 'rm -rf "$GNUPGHOME"' EXIT
chmod 700 "$GNUPGHOME"
printf '%s' "$PGP_SECRET" | tr -d '\n\r ' | base64 --decode | gpg --batch --import
# sbt 2 supplies the Central Portal credentials from SONATYPE_*.
# Snapshots upload directly; releases stage signed files before promotion.
if [[ "$version" == *-SNAPSHOT ]]; then
  ./sbt --batch publishSigned
else
  ./sbt --batch publishSigned sonaRelease
fi
