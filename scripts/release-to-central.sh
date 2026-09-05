#!/usr/bin/env bash
# Publishes a tagged release to Maven Central through the Central Portal, the same way uuidulid is
# published: GPG-signed artifacts, server id "central" from ~/.m2/settings.xml, manual publish in
# the portal afterwards (autoPublish=false).
#
#   scripts/release-to-central.sh 0.1.1            # deploy tag v0.1.1
#   scripts/release-to-central.sh 0.1.1 --publish  # ... and publish automatically in the portal
#
# Prerequisites (both already in place if uuidulid was published from this machine):
#   ~/.m2/settings.xml  →  <server><id>central</id><username>…</username><password>…</password></server>
#   gpg --list-secret-keys shows the signing key; gpg-agent knows the passphrase or prompts for it
set -euo pipefail

# Everything lives in main() so bash has parsed the whole file before `git checkout <tag>` swaps the tree.
main() {

version="${1:-}"
publish="${2:-}"
if [ -z "$version" ]; then
  echo "usage: $0 <version> [--publish]   e.g. $0 0.1.1" >&2
  exit 2
fi
tag="v$version"
cd "$(dirname "$0")/.."

echo "▶ checking prerequisites"
if ! grep -q "<id>central</id>" ~/.m2/settings.xml 2>/dev/null; then
  echo "✗ no <server><id>central</id> in ~/.m2/settings.xml (Central Portal user token)" >&2
  exit 1
fi
if ! gpg --list-secret-keys --keyid-format long 2>/dev/null | grep -q '^sec'; then
  echo "✗ no GPG secret key available" >&2
  exit 1
fi
command -v java >/dev/null || { echo "✗ java not found" >&2; exit 1; }
echo "✓ central server configured, GPG key present, $(java -version 2>&1 | head -1)"

echo "▶ fetching tag $tag"
git fetch --tags origin
if ! git rev-parse -q --verify "refs/tags/$tag" >/dev/null; then
  echo "✗ tag $tag does not exist on origin. Create the release first (release workflow or git tag)." >&2
  exit 1
fi
if [ -n "$(git status --porcelain)" ]; then
  echo "✗ working tree is not clean; commit or stash first" >&2
  exit 1
fi
current_ref="$(git symbolic-ref -q --short HEAD || git rev-parse HEAD)"
git checkout -q "$tag"
trap 'git checkout -q "$current_ref"' EXIT

pom_version="$(./mvnw -q -B help:evaluate -Dexpression=project.version -DforceStdout)"
if [ "$pom_version" != "$version" ]; then
  echo "✗ tag $tag carries pom version $pom_version, expected $version" >&2
  exit 1
fi

echo "▶ verifying build (tests included)"
./mvnw -B -ntp verify

echo "▶ deploying $version to the Central Portal"
extra=""
if [ "$publish" = "--publish" ]; then
  extra="-DautoPublish=true"
fi
./mvnw -B -ntp -Prelease -DskipTests $extra deploy

cat <<MSG

✓ Deployment $version uploaded.
  Without --publish the deployment waits for you: https://central.sonatype.com/publishing/deployments
  Check the validation there and press "Publish". Artifacts appear on Maven Central within a few
  minutes and in search after a couple of hours:
  https://central.sonatype.com/artifact/io.github.bnymndev/durable-agents-starter/$version
MSG
}

main "$@"
