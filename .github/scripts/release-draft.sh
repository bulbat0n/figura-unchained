#!/usr/bin/env bash
set -euo pipefail

: "${GITHUB_REPOSITORY:?}" "${GITHUB_SHA:?}" "${BRANCHES:?}"
VERSION=${VERSION:-}

repo="$GITHUB_REPOSITORY"
server="${GITHUB_SERVER_URL:-https://github.com}"
notes_src=".github/release-notes/$VERSION.md"
signer="$repo/.github/workflows/build.yml"
sources="figura-unchained-$VERSION-sources"

fail() {
    echo "::error::$1"
    exit 1
}

[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || fail "Version must look like x.x.x, got '$VERSION'"
[ -f "$notes_src" ] || fail "Write $notes_src on this branch first"
if gh release view "$VERSION" --repo "$repo" > /dev/null 2>&1; then
    fail "Release $VERSION already exists"
fi

rm -rf release work
mkdir -p release work
index="$PWD/work/sources.index"
GIT_INDEX_FILE="$index" git read-tree --empty

for branch in $BRANCHES; do
    sha=$(gh api "repos/$repo/git/ref/heads/$branch" --jq .object.sha)
    run=$(gh run list --repo "$repo" --workflow build.yml --branch "$branch" --commit "$sha" --event push --status success --limit 1 --json databaseId --jq '.[0].databaseId // empty')
    [ -n "$run" ] || fail "No successful build.yml run for branch $branch at ${sha:0:7}: push it and wait for the build"

    dir="work/$branch"
    gh run download "$run" --repo "$repo" --name "figura-unchained-$branch" --dir "$dir"
    mapfile -t jars < <(find "$dir" -type f -name 'figura-unchained-*.jar' | sort)
    [ "${#jars[@]}" -gt 0 ] || fail "Build $run of $branch has no jars"

    for jar in "${jars[@]}"; do
        name=$(basename "$jar")
        [[ "$name" =~ ^figura-unchained-([0-9]+\.[0-9]+\.[0-9]+)\+mc([0-9.]+(-[0-9.]+)?)\+(fabric|forge|neoforge)\+ ]] || fail "Unexpected file name $name from branch $branch"
        jar_version=${BASH_REMATCH[1]}
        label=${BASH_REMATCH[2]}
        [ "$jar_version" = "$VERSION" ] || fail "$name from branch $branch is not version $VERSION"
        [ "${label##*-}" = "$branch" ] || fail "$name from branch $branch is not built for $branch"
        [ ! -e "release/$name" ] || fail "Two builds produced $name"
        gh attestation verify "$jar" --repo "$repo" \
            --signer-workflow "$signer" \
            --source-ref "refs/heads/$branch" \
            --source-digest "$sha" \
            --deny-self-hosted-runners > /dev/null \
            || fail "$name has no build attestation from $branch at ${sha:0:7}"
        cp "$jar" "release/$name"
        echo "ok  $name  ($branch ${sha:0:7}, run $run)"
    done

    git fetch --quiet --no-tags --depth 1 origin "$sha"
    GIT_INDEX_FILE="$index" git read-tree --prefix="$branch/" "$sha"
done

(cd release && sha256sum -- *.jar > SHA256SUMS && sha512sum -- *.jar > SHA512SUMS)

tree=$(GIT_INDEX_FILE="$index" git write-tree)
mtime=$(git log -1 --format=%cI "$GITHUB_SHA")
git archive --format=zip --prefix="$sources/" --mtime="$mtime" -o "release/$sources.zip" "$tree"
git archive --format=tar.gz --prefix="$sources/" --mtime="$mtime" -o "release/$sources.tar.gz" "$tree"

if gh api "repos/$repo/git/ref/tags/$VERSION" > /dev/null 2>&1; then
    gh release create "$VERSION" release/* --repo "$repo" --draft --verify-tag --title "$VERSION" --notes-file "$notes_src"
else
    gh release create "$VERSION" release/* --repo "$repo" --draft --target "$GITHUB_SHA" --title "$VERSION" --notes-file "$notes_src"
fi

{
    echo "Draft release **$VERSION** is ready: $server/$repo/releases"
    echo
    echo "Check it and press **Publish release**. Publishing starts the Modrinth upload."
} >> "${GITHUB_STEP_SUMMARY:-/dev/null}"
