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

fetch "figura-0.1.6+1.21.1-neoforge-mc.jar" \
    "https://cdn.modrinth.com/data/s9gIPDom/versions/AvgeQoZm/figura-0.1.6%2B1.21.1-neoforge-mc.jar" \
    f379147b52953e8013c27ee5b9d1552618b649f80a0cbe974ed0c6e8de89515c
