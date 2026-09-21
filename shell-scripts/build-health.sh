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
#   ./shell-scripts/build-health.sh --install    # include the package and install phases
#   ./shell-scripts/build-health.sh --integration # also run the container-backed tests
#   ./shell-scripts/build-health.sh --ci          # build the image, then run every test
#
# --integration runs the container-backed tests, but it stops at the test
# phase. The chat-shell tests reach the server through the
# chat-deploy-memory-integration-test image, and the package phase builds that
# image. So --integration reports on whatever image the machine already holds.
# --ci reaches the package phase, so it builds the image before chat-shell
# runs, and the chat-shell result belongs to the current source.
#
# --ci takes the phase and the profiles of the integration job in
# .github/workflows/maven.yml, which runs
# "mvn -B clean verify -Ptest-build,integration". It is not that command.
# Two differences, both deliberate:
#
#   1. --ci adds -fae. This script diffs the failing modules against the
#      document, so it must see the result of every module. A build that stops
#      at the first failure reports the rest as skipped.
#   2. --ci resolves artifacts online, like the workflow. Every other mode
#      defaults to offline. A cold repository cannot build the image, and the
#      image build reads the network through docker in any case.
#
# So --ci measures the same phase, profiles and image path as CI. It does not
# reproduce a CI run.
#
# Exit status: 0 when reality matches the document, 1 when it does not.

set -uo pipefail

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
ROOT="$( cd "$DIR/.." && pwd )"
DOC="docs/BUILD-HEALTH.md"

# Modules expected to fail, from docs/BUILD-HEALTH.md. Keep these two in sync:
# if you change this list, change the document, and vice versa.
# If this script reports NEW, something regressed - that is the signal, not
# noise to be silenced by adding the module here.
#
# Empty again since chat-index-elastic left the reactor. A module that is not
# built cannot fail, so naming it here would make this script report RESOLVED
# on every run. See the B11 row in docs/BUILD-HEALTH.md and CHAT-gdtktbfh.
KNOWN_FAILING=""
# Additionally expected to fail once the build reaches package/install.
# Empty since B1: chat-deploy-memory-integration-test no longer builds an image
# on every install - that moved behind -Ptest-build.
KNOWN_FAILING_INSTALL=""
# Additionally expected to fail under -Pintegration, which runs the
# container-backed tests excluded from a default build.
# Empty since B2: the container-backed suites pass under -Pintegration.
KNOWN_FAILING_INTEGRATION=""

PHASE="test"
OFFLINE="-o"
PROFILES=""
CI=""
for arg in "$@"; do
    case "$arg" in
        --online)  OFFLINE="" ;;
        --install) PHASE="install" ;;
        --integration) PROFILES="integration" ;;
        # Takes the phase and the profiles of the workflow integration job, and
        # resolves online like it does. The package phase builds the chat-shell
        # test image, so this is the only mode whose chat-shell result belongs
        # to the source under test. See the header for the two differences.
        --ci) CI="yes" ;;
        --help|-h) awk 'NR>1 && /^#/ {sub(/^# ?/,""); print; next} NR>1 {exit}' "$0"; exit 0 ;;
        *) echo "unknown option: $arg" >&2; exit 2 ;;
    esac
done
# --ci names one whole command, so it cannot be combined with a flag that sets
# the phase or the profiles.
if [ -n "$CI" ]; then
    if [ "$PHASE" != "test" ] || [ -n "$PROFILES" ]; then
        echo "--ci already sets the phase and the profiles; remove the other flag" >&2
        exit 2
    fi
    PHASE="verify"
    PROFILES="test-build,integration"
    # The workflow resolves online, and a cold repository cannot build the
    # image. --online stays accepted, and it changes nothing here.
    OFFLINE=""
fi

PROFILE_ARG=""
[ -n "$PROFILES" ] && PROFILE_ARG="-P$PROFILES"

expected="$KNOWN_FAILING"
[ "$PHASE" != "test" ] && expected="$expected $KNOWN_FAILING_INSTALL"
case ",$PROFILES," in *,integration,*) expected="$expected $KNOWN_FAILING_INTEGRATION" ;; esac

log="$(mktemp -t build-health)"
trap 'rm -f "$log"' EXIT

echo "running: mvn $OFFLINE clean $PHASE -fae $PROFILE_ARG"
echo "(a full run takes several minutes; container-backed modules dominate)"
if [ "$PROFILES" = "integration" ]; then
    # The register records a stale image that gave 8 decode errors and looked
    # like a code regression. This mode cannot tell that apart from a real one.
    echo "note: this mode does not build the chat-shell test image."
    echo "      chat-shell reports against the image the machine already holds."
    echo "      run --ci to build the image first."
fi
echo

# shellcheck disable=SC2086
(cd "$ROOT" && mvn $OFFLINE clean "$PHASE" -fae $PROFILE_ARG) > "$log" 2>&1

# The counts, printed before the drift check. This gate deletes its Maven log
# on exit, so a caller that needs the numbers had to run its own build. A
# module aggregate line carries exactly ten fields, and a per class line
# carries more, so the field count separates them.
counts="$(awk '$2 == "Tests" && $3 == "run:" && $9 == "Skipped:" && NF == 10 {
    gsub(/,/, ""); t += $4; f += $6; e += $8; s += $10; n += 1
}
END { printf "%d modules ran tests: %d tests, %d failures, %d errors, %d skipped", n, t, f, e, s }' "$log")"
echo "$counts"
echo

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
