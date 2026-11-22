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

fetch "figura-0.1.6+1.20.1-forge-mc.jar" \
    "https://cdn.modrinth.com/data/s9gIPDom/versions/LXMnmMxn/figura-0.1.6%2B1.20.1-forge-mc.jar" \
    3988f6263875fad029e8ecdf692d3477c97d0e278505895936a97ff6f07c9e85
