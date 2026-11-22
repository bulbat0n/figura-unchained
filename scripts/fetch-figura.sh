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

fetch "nofigura-0.1.6.13+26.3-fabric-mc.jar" \
    "https://cdn.modrinth.com/data/qMYudiOM/versions/apIEmLXw/nofigura-0.1.6.13%2B26.3-fabric-mc.jar" \
    2d7110b9d0a3341e374572a67833770deb2bc4e5439f64e53c0dc9a47f01ab60
