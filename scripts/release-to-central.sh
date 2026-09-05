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
# Find a gpg that actually holds a secret key. On Windows, Git Bash ships its own gpg with an empty
# keyring while the signing key usually lives in Gpg4win (Kleopatra); prefer that one.
gpg_bin=""
candidates=("${GPG_EXECUTABLE:-}" gpg gpg2
  "/c/Program Files (x86)/GnuPG/bin/gpg.exe" "/c/Program Files/GnuPG/bin/gpg.exe"
  "/c/Program Files (x86)/Gpg4win/bin/gpg.exe" "/c/Program Files/Git/usr/bin/gpg.exe")
for c in "${candidates[@]}"; do
  [ -n "$c" ] || continue
  if command -v "$c" >/dev/null 2>&1 || [ -x "$c" ]; then
    if "$c" --list-secret-keys --keyid-format long 2>/dev/null | grep -q '^sec'; then
      gpg_bin="$c"; break
    fi
  fi
done
if [ -z "$gpg_bin" ]; then
  cat >&2 <<'ERR'
✗ no GPG secret key found in any known gpg.
  - Windows: install Gpg4win (https://gpg4win.org), import or create the key in Kleopatra, or point
    the script at your gpg:   GPG_EXECUTABLE="/c/Program Files (x86)/GnuPG/bin/gpg.exe" scripts/release-to-central.sh 0.1.1
  - Key on another machine:  gpg --export-secret-keys -a KEYID > key.asc  (there)  ·  gpg --import key.asc  (here)
  - No key yet:              gpg --full-generate-key   (RSA 4096, your e-mail)   then publish it:
                             gpg --keyserver keyserver.ubuntu.com --send-keys KEYID
ERR
  exit 1
fi
command -v java >/dev/null || { echo "✗ java not found" >&2; exit 1; }
gpg_key="$("$gpg_bin" --list-secret-keys --keyid-format long 2>/dev/null | awk '/^sec/{print $2; exit}')"
echo "✓ central server configured, GPG key $gpg_key via $gpg_bin, $(java -version 2>&1 | head -1)"
gpg_mvn_args=(-Dgpg.executable="$gpg_bin")
export GPG_TTY="${GPG_TTY:-$(tty 2>/dev/null || true)}"
# The gpg plugin signs in loopback mode. Without a pinentry (typical for Git Bash on Windows) gpg
# cannot ask for the passphrase itself, so we ask once and hand it over via the plugin's env variable.
if [ -z "${MAVEN_GPG_PASSPHRASE:-}" ]; then
  read -r -s -p "GPG passphrase for $gpg_key (leave empty if the key has none): " MAVEN_GPG_PASSPHRASE; echo
  export MAVEN_GPG_PASSPHRASE
fi
echo "▶ testing signature"
if ! echo test | "$gpg_bin" --batch --yes --pinentry-mode loopback --passphrase "$MAVEN_GPG_PASSPHRASE" \
     --local-user "$gpg_key" --armor --detach-sign --output /dev/null - 2>/tmp/gpg-test.err; then
  cat /tmp/gpg-test.err >&2
  echo "✗ gpg could not sign with $gpg_key. Wrong passphrase, or loopback pinentry is disallowed:" >&2
  echo "  add 'allow-loopback-pinentry' to ~/.gnupg/gpg-agent.conf and run: gpgconf --kill gpg-agent" >&2
  exit 1
fi
echo "✓ signature test passed"

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
./mvnw -B -ntp -Prelease -DskipTests $extra "${gpg_mvn_args[@]}" deploy

cat <<MSG

✓ Deployment $version uploaded.
  Without --publish the deployment waits for you: https://central.sonatype.com/publishing/deployments
  Check the validation there and press "Publish". Artifacts appear on Maven Central within a few
  minutes and in search after a couple of hours:
  https://central.sonatype.com/artifact/io.github.bnymndev/durable-agents-starter/$version
MSG
}

main "$@"
