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

fetch "nofigura-0.1.6.9+26.1.2-fabric-mc.jar" \
    "https://cdn.modrinth.com/data/qMYudiOM/versions/bL2E2Io0/nofigura-0.1.6.9%2B26.1.2-fabric-mc.jar" \
    5536ea8d4c675ddc763877eda47985ba54658c5072e3e717d9f060cc360eb387
