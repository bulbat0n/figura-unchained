#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

fetch() {
    local file="libs/$1" url="$2" sum="$3"
    if [ -f "$file" ]; then
        echo "$sum  $file" | sha256sum -c -
        return
    fi
    mkdir -p libs
    curl -fL --retry 3 -o "$file.part" "$url"
    echo "$sum  $file.part" | sha256sum -c -
    mv "$file.part" "$file"
    echo "$sum  $file" | sha256sum -c -
}

fetch "nofigura-0.1.6.10+26.2-fabric-mc.jar" \
    "https://cdn.modrinth.com/data/qMYudiOM/versions/zgYKGP8s/nofigura-0.1.6.10%2B26.2-fabric-mc.jar" \
    cde2096c4532c406dd0ba91bd7d262229cb97a8372ec7f832bf9f9fcbda2ffa6
