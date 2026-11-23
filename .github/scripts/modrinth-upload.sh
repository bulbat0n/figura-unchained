#!/usr/bin/env bash
set -euo pipefail

: "${GITHUB_REPOSITORY:?}" "${MODRINTH_TOKEN:?}" "${MODRINTH_PROJECT:?}"
TAG=${TAG:-}

repo="$GITHUB_REPOSITORY"
server="${GITHUB_SERVER_URL:-https://github.com}"
api="https://api.modrinth.com/v2"
ua="$repo/modrinth-upload ($server/$repo)"
version_type="${MODRINTH_VERSION_TYPE:-release}"
release_url="$server/$repo/releases/tag/$TAG"
notes_src=".github/release-notes/$TAG.md"
signer="$repo/.github/workflows/build.yml"
figura_project=s9gIPDom
nofigura_project=qMYudiOM
figura_on_modrinth=" 1.20.1 1.21.1 1.21.4 "

fail() {
    echo "::error::$1"
    exit 1
}

[[ "$TAG" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || fail "Version must look like x.x.x, got '$TAG'"

mr() {
    curl -sS --fail-with-body --retry 3 -A "$ua" -H "Authorization: $MODRINTH_TOKEN" "$@"
}

rm -rf modrinth
mkdir -p modrinth/files
gh release download "$TAG" --repo "$repo" --dir modrinth/files --pattern '*.jar' --pattern SHA256SUMS
(cd modrinth/files && sha256sum --check --strict SHA256SUMS)

project_id=$(mr "$api/project/$MODRINTH_PROJECT" | jq -er .id)
mr "$api/tag/game_version" | jq -r '.[] | [.version, .version_type] | @tsv' > modrinth/game_version_tags
cut -f1 modrinth/game_version_tags > modrinth/game_versions
awk -F'\t' '$2 == "release" { print $1 }' modrinth/game_version_tags | sort -V > modrinth/releases
mr "$api/tag/loader" | jq -r '.[].name' > modrinth/loaders
mr "$api/project/$project_id/version" | jq -r '.[] | [.version_number, (.files[0].hashes.sha512 // "")] | @tsv' > modrinth/existing

notes=""
[ ! -f "$notes_src" ] || notes=$(cat "$notes_src")

: > modrinth/plan
for jar in modrinth/files/*.jar; do
    name=$(basename "$jar")
    [[ "$name" =~ ^figura-unchained-([0-9]+\.[0-9]+\.[0-9]+)\+mc([0-9.]+(-[0-9.]+)?)\+(fabric|forge|neoforge)\+(figura|nofigura)([0-9.]+)\.jar$ ]] || fail "Unexpected file name $name"
    mod_version=${BASH_REMATCH[1]}
    mc=${BASH_REMATCH[2]}
    loader=${BASH_REMATCH[4]}
    base=${BASH_REMATCH[5]}
    base_version=${BASH_REMATCH[6]}
    mc_top=${mc##*-}
    if [[ "$mc" == *-* ]]; then
        mc_low=${mc%%-*}
        games=$(awk -v lo="$mc_low" -v hi="$mc_top" '$0 == lo { on = 1 } on { print } $0 == hi { exit }' modrinth/releases | paste -sd' ')
        [[ "$games" == "$mc_low "* && "$games" == *" $mc_top" ]] || fail "Cannot expand Minecraft range $mc from Modrinth release list"
    else
        games=$mc
    fi
    [ "$mod_version" = "$TAG" ] || fail "$name is not version $TAG"
    for game in $games; do
        grep -qxF "$game" modrinth/game_versions || fail "Modrinth has no game version $game"
    done
    grep -qxF "$loader" modrinth/loaders || fail "Modrinth has no loader $loader"
    gh attestation verify "$jar" --repo "$repo" --signer-workflow "$signer" --deny-self-hosted-runners > /dev/null \
        || fail "$name has no build attestation from $signer"
    jar_version=${name#figura-unchained-}
    jar_version=${jar_version%.jar}
    if [ "$base" = nofigura ]; then
        number="$mod_version+$mc+nofigura$base_version"
    else
        number="$mod_version+$mc$loader+$base_version"
    fi
    title="figura-unchained $jar_version"
    [ "${#number}" -le 32 ] || fail "Version number $number is longer than 32 characters"
    [ "${#title}" -le 64 ] || fail "Version subtitle $title is longer than 64 characters"
    sha512=$(sha512sum "$jar" | cut -d' ' -f1)
    known=$(awk -F'\t' -v n="$number" '$1 == n { print $2 }' modrinth/existing)
    if [ -n "$known" ]; then
        [ "$known" = "$sha512" ] || fail "$number is already on Modrinth with a different file"
        echo "skip  $number (already on Modrinth)"
        continue
    fi
    case "$loader" in
        fabric) rank=1 ;;
        forge) rank=2 ;;
        neoforge) rank=3 ;;
    esac
    printf '%s\t%s\t%s|%s|%s|%s|%s|%s|%s\n' "$mc_top" "$rank" "$jar" "$games" "$loader" "$base" "$number" "$sha512" "$title" >> modrinth/plan
done
mapfile -t plan < <(sort -t $'\t' -k1,1V -k2,2n modrinth/plan | cut -f3)
[ "${#plan[@]}" -gt 0 ] || { echo "Nothing to upload"; exit 0; }
echo "Upload order:"
for entry in "${plan[@]}"; do
    echo "  $(cut -d'|' -f5 <<< "$entry")"
done

first=1
for entry in "${plan[@]}"; do
    IFS='|' read -r jar games loader base number sha512 title <<< "$entry"
    mc_top=${games##* }
    [ "$first" = 1 ] || sleep 3
    first=0
    sha256=$(sha256sum "$jar" | cut -d' ' -f1)

    if [ "$base" = nofigura ]; then
        deps="[{\"project_id\":\"$nofigura_project\",\"dependency_type\":\"required\"}]"
    elif [[ "$figura_on_modrinth" == *" $mc_top "* ]]; then
        deps="[{\"project_id\":\"$figura_project\",\"dependency_type\":\"required\"}]"
    else
        deps="[]"
    fi
    changelog=$(printf '%s\n\nBuilt by GitHub Actions, release: %s\n\nSHA-256: `%s`\n' "$notes" "$release_url" "$sha256")

    data=$(jq -n \
        --arg project "$project_id" \
        --arg name "$title" \
        --arg number "$number" \
        --arg changelog "$changelog" \
        --arg games "$games" \
        --arg loader "$loader" \
        --arg type "$version_type" \
        --argjson deps "$deps" \
        '{project_id: $project, name: $name, version_number: $number, changelog: $changelog, dependencies: $deps, game_versions: ($games | split(" ")), loaders: [$loader], version_type: $type, featured: false, status: "listed", file_parts: ["file"], primary_file: "file"}')

    response=$(mr -X POST "$api/version" --form-string "data=$data" -F "file=@$jar;type=application/java-archive")
    uploaded=$(jq -r '.files[0].hashes.sha512' <<< "$response")
    [ "$uploaded" = "$sha512" ] || fail "Modrinth stored a different file for $number"
    echo "done  $number -> https://modrinth.com/mod/$MODRINTH_PROJECT/version/$(jq -r .id <<< "$response")"
    echo "- [$number](https://modrinth.com/mod/$MODRINTH_PROJECT/version/$(jq -r .id <<< "$response"))" >> "${GITHUB_STEP_SUMMARY:-/dev/null}"
done
