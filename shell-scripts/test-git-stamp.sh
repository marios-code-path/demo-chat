#!/usr/bin/env bash
# Assert that git.properties names the HEAD of the tree that Maven builds.
# A linked worktree and a plain checkout both count. See CHAT-sgwiyzic.
#
# The suite makes its own repository under a temporary directory: a plain
# clone, a linked worktree of that clone, and an empty probe commit on the
# clone. So the main checkout and the worktree always name two commits, and
# a stamp of the wrong tree cannot pass. The suite copies the root pom.xml of
# the calling tree into both trees, so it tests the POM as it stands.
# It runs Maven offline, to the initialize phase of chat-core alone.
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
. "$here/test-support/clear-legacy-inputs.sh" || exit $?
. "$here/test-support/clear-composition-inputs.sh" || exit $?
unset GIT_DIR GIT_WORK_TREE GIT_INDEX_FILE GIT_COMMON_DIR GIT_OBJECT_DIRECTORY \
    GIT_ALTERNATE_OBJECT_DIRECTORIES GIT_CEILING_DIRECTORIES MAVEN_ARGS MAVEN_OPTS

repo=$(cd "$here/.." && pwd)
command -v git > /dev/null 2>&1 || { echo 'test-git-stamp: no git command.' >&2; exit 2; }
command -v mvn > /dev/null 2>&1 || { echo 'test-git-stamp: no mvn command.' >&2; exit 2; }
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
checks=0
failures=0
pass() { checks=$((checks + 1)); printf 'ok %s\n' "$1"; }
fail() { checks=$((checks + 1)); failures=$((failures + 1)); printf 'FAIL %s\n' "$1"; }

head=$(git -C "$repo" rev-parse HEAD)
git clone --quiet --shared --no-checkout "$repo" "$work/plain" || exit 2
git -C "$work/plain" checkout --quiet --detach "$head" || exit 2
git -C "$work/plain" worktree add --quiet --detach "$work/linked" "$head" || exit 2
git -C "$work/plain" -c user.name=probe -c user.email=probe@example.invalid \
    commit --quiet --allow-empty -m 'probe: the main checkout moves away from the worktree' || exit 2
for tree in plain linked; do cp "$repo/pom.xml" "$work/$tree/pom.xml"; done

# stamp TREE: build chat-core to initialize and compare its stamp with the tree HEAD.
stamp() {
    local tree="$1" expected props commit status=0
    expected=$(git -C "$work/$tree" rev-parse HEAD)
    (cd "$work/$tree" && mvn -B -ntp -q -o -pl chat-core initialize) \
        > "$work/$tree.log" 2>&1 || status=$?
    props="$work/$tree/chat-core/target/classes/git.properties"
    commit=$(sed -nE 's/^git\.commit\.id=([0-9a-f]+).*/\1/p' "$props" 2> /dev/null | head -1)
    if (( status == 0 )) && [[ "$commit" == "$expected" ]]; then
        pass "$2"
    else
        fail "$2: expected ${expected:0:12}, stamp ${commit:0:12}, maven status $status"
        (( status == 0 )) || tail -n 5 "$work/$tree.log" | sed 's/^/    /'
    fi
}

if [[ "$(git -C "$work/plain" rev-parse HEAD)" != "$(git -C "$work/linked" rev-parse HEAD)" ]]; then
    pass 'the main checkout and the linked worktree name two commits'
else
    fail 'the main checkout and the linked worktree name two commits'
fi
stamp plain 'a plain checkout build stamps its own HEAD'
stamp linked 'a linked worktree build stamps the worktree HEAD, not the main checkout HEAD'

printf '%s checks, %s failures\n' "$checks" "$failures"
(( failures == 0 ))
