#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

FIGURA_REPO="https://github.com/FiguraMC/Figura.git"
FIGURA_COMMIT="15f08d415cb96afbaa6a98b3a8a2dc833e672716"
FIGURA_WRAPPER_SHA256="0336f591bc0ec9aa0c9988929b93ecc916b3c1d52aed202c7381db144aa0ef15"
FIGURA_GRADLE_URL='distributionUrl=https\://services.gradle.org/distributions/gradle-8.11-bin.zip'
FIGURA_GRADLE_SHA256="57dafb5c2622c6cc08b993c85b7c06956a2f53536432a30ead46166dbca0f1e9"
TARGET="libs/figura-0.1.6+1.21.10-fabric-mc.jar"
SRC="build/figura-src"
FIGURA_JAVA_HOME="/usr/lib/jvm/java-21-openjdk-amd64"

if [ -f "$TARGET" ]; then
    echo "$TARGET is already present"
    exit 0
fi

rm -rf "$SRC"
mkdir -p "$SRC"
git -C "$SRC" init -q
git -C "$SRC" fetch -q --depth 1 "$FIGURA_REPO" "$FIGURA_COMMIT"
git -C "$SRC" checkout -q --detach FETCH_HEAD
test "$(git -C "$SRC" rev-parse HEAD)" = "$FIGURA_COMMIT"

echo "$FIGURA_WRAPPER_SHA256  $SRC/gradle/wrapper/gradle-wrapper.jar" | sha256sum -c -
grep -qxF "$FIGURA_GRADLE_URL" "$SRC/gradle/wrapper/gradle-wrapper.properties"
printf '\ndistributionSha256Sum=%s\n' "$FIGURA_GRADLE_SHA256" >> "$SRC/gradle/wrapper/gradle-wrapper.properties"

for attempt in 1 2 3 4 5; do
    if (cd "$SRC" && JAVA_HOME="$FIGURA_JAVA_HOME" sh ./gradlew --no-daemon --version); then
        break
    fi
    [ "$attempt" -lt 5 ] || exit 1
    sleep $((attempt * 30))
done
(cd "$SRC" && JAVA_HOME="$FIGURA_JAVA_HOME" sh ./gradlew --no-daemon :fabric:remapJar)

mkdir -p libs
cp "$SRC/fabric/build/libs/figura-0.1.6+1.21.10-fabric-mc.jar" "$TARGET"
sha256sum "$TARGET"
