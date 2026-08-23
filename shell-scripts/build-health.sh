#!/bin/bash
#
# Reports which modules currently fail the reactor test phase, and diffs that
# against the deficiencies recorded in docs/BUILD-HEALTH.md.
#
# The point is drift detection. A hand-maintained list goes stale the moment
# someone fixes something and forgets to say so, or breaks something and does
# not notice. This runs the build and tells you which entries in the document
# are still true.
#
#   ./shell-scripts/build-health.sh              # offline, test phase only
#   ./shell-scripts/build-health.sh --online     # allow artifact downloads
#   ./shell-scripts/build-health.sh --install    # include package/install, covers build-image
#   ./shell-scripts/build-health.sh --integration # also run the container-backed tests
#
# Exit status: 0 when reality matches the document, 1 when it does not.

set -uo pipefail

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
ROOT="$( cd "$DIR/.." && pwd )"
DOC="docs/BUILD-HEALTH.md"

# Modules expected to fail, from docs/BUILD-HEALTH.md. Keep these two in sync:
# if you change this list, change the document, and vice versa.
# Empty: a default build has no known failures. If this script reports NEW,
# something regressed - that is the signal, not noise to be silenced by adding
# the module here.
KNOWN_FAILING=""
# Additionally expected to fail once the build reaches package/install.
# Names here are Maven project names as printed in the reactor summary, which is
# why this one is plural while its directory is singular.
KNOWN_FAILING_INSTALL="chat-deploy-memory-integration-tests"
# Additionally expected to fail under -Pintegration, which runs the
# container-backed tests excluded from a default build.
KNOWN_FAILING_INTEGRATION="chat-shell"

PHASE="test"
OFFLINE="-o"
INTEGRATION=""
for arg in "$@"; do
    case "$arg" in
        --online)  OFFLINE="" ;;
        --install) PHASE="install" ;;
        --integration) INTEGRATION="-Pintegration" ;;
        --help|-h) awk 'NR>1 && /^#/ {sub(/^# ?/,""); print; next} NR>1 {exit}' "$0"; exit 0 ;;
        *) echo "unknown option: $arg" >&2; exit 2 ;;
    esac
done

expected="$KNOWN_FAILING"
[ "$PHASE" = "install" ] && expected="$expected $KNOWN_FAILING_INSTALL"
[ -n "$INTEGRATION" ] && expected="$expected $KNOWN_FAILING_INTEGRATION"

log="$(mktemp -t build-health)"
trap 'rm -f "$log"' EXIT

echo "running: mvn $OFFLINE clean $PHASE -fae $INTEGRATION"
echo "(a full run takes several minutes; container-backed modules dominate)"
echo

# shellcheck disable=SC2086
(cd "$ROOT" && mvn $OFFLINE clean "$PHASE" -fae $INTEGRATION) > "$log" 2>&1

summary="$(sed -n '/Reactor Summary/,/^\[INFO\] -\{20,\}$/p' "$log")"
if [ -z "$summary" ]; then
    echo "could not find a reactor summary in the build output; last 30 lines:" >&2
    tail -30 "$log" >&2
    exit 2
fi

actual="$(echo "$summary" | awk '/FAILURE \[/ {print $2}' | sort)"
skipped="$(echo "$summary" | awk '/SKIPPED$/ {print $2}' | sort)"
expected_sorted="$(echo "$expected" | tr ' ' '\n' | grep -v '^$' | sort)"

new="$(comm -13 <(echo "$expected_sorted") <(echo "$actual"))"
resolved="$(comm -23 <(echo "$expected_sorted") <(echo "$actual"))"

status=0

if [ -n "$actual" ]; then
    echo "failing modules:"
    echo "$actual" | sed 's/^/  /'
    echo
fi

if [ -n "$new" ]; then
    echo "NEW — failing but not recorded in $DOC:"
    echo "$new" | sed 's/^/  /'
    echo "  → investigate, then add an entry or fix it"
    echo
    status=1
fi

if [ -n "$resolved" ]; then
    echo "RESOLVED — recorded in $DOC but passing now:"
    echo "$resolved" | sed 's/^/  /'
    echo "  → move the entry to the Resolved section, with the PR that fixed it"
    echo
    status=1
fi

if [ -n "$skipped" ]; then
    # A skipped module is not a passing module. Skips mean an upstream module
    # failed to build, which hides everything downstream of it.
    echo "SKIPPED — never built, so their state is unknown:"
    echo "$skipped" | sed 's/^/  /'
    echo
    status=1
fi

if [ "$status" -eq 0 ]; then
    echo "reality matches $DOC"
else
    echo "$DOC is out of date — see above"
    echo "full log: $log"
    trap - EXIT
fi

exit "$status"
