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

fetch "figura-0.1.6+1.21.4-neoforge-mc.jar" \
    "https://cdn.modrinth.com/data/s9gIPDom/versions/zsrG9CmF/figura-0.1.6%2B1.21.4-neoforge-mc.jar" \
    3d3469cc423587f9e277e23fd42fac22a4202368c6aae4c6e82c2d9804b5b56c
