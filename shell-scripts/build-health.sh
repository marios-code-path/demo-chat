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
#   ./shell-scripts/build-health.sh --record      # also write the list of modules that ran tests
#
# Each mode also checks which modules ran tests. A module whose test classes
# are missing runs no test and passes, so a check on failures alone cannot see
# it. shell-scripts/build-health-tests-unit.txt names the modules that run
# tests without the integration profile, and build-health-tests-integration.txt
# names those that run tests with it. A listed module that runs no test is
# drift. An unlisted module that runs tests is drift too, so the list cannot go
# stale in silence. --record writes the list of the mode from the run, and
# skips that comparison. See CHAT-lantftth.
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
# --ci also runs shell-scripts/agent-http-gate.sh, which runs the agent HTTP and
# relay test classes in their own builds. See CHAT-frcrctdp.
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
#
# B2 is closed: the container-backed suites pass under -Pintegration.
#
# B12 is closed. chat-shell left this list on 2026-10-02, under
# CHAT-mfveaecc. The shell tests log in where an operation needs an identity,
# and a join grants SEND. See B12 in the Resolved table of
# docs/BUILD-HEALTH.md.
KNOWN_FAILING_INTEGRATION=""

PHASE="test"
OFFLINE="-o"
PROFILES=""
CI=""
RECORD=""
for arg in "$@"; do
    case "$arg" in
        --online)  OFFLINE="" ;;
        # Writes the modules that ran tests to the list of this mode. Use it
        # after a change adds or removes the tests of a module, and review the
        # diff of the list before you commit it.
        --record)  RECORD="yes" ;;
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

# The modules that ran tests before, for this mode. The integration profile
# adds container tests, so it has its own list. --install and --ci reach later
# phases, but surefire runs the same tests in the test phase.
TESTS_LIST="$DIR/build-health-tests-unit.txt"
case ",$PROFILES," in *,integration,*) TESTS_LIST="$DIR/build-health-tests-integration.txt" ;; esac
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

# The modules that ran at least one test, by name. Surefire prints the module
# in its execution header, and the aggregate line of that module follows it.
ran="$(awk '/--- surefire:.*:test .* @ / {mod = $(NF-1)}
$2 == "Tests" && $3 == "run:" && $9 == "Skipped:" && NF == 10 {
    gsub(/,/, ""); if ($4 > 0) print mod
}' "$log" | sort -u)"

if [ -n "$RECORD" ]; then
    {
        echo "# Modules that ran tests in this mode. Written by build-health.sh --record."
        echo "# Review the diff before you commit it. See CHAT-lantftth."
        echo "$ran"
    } > "$TESTS_LIST"
    echo "recorded $(echo "$ran" | grep -c .) modules that ran tests to ${TESTS_LIST#"$ROOT"/}"
    echo
else
    listed="$(grep -v -e '^#' -e '^$' "$TESTS_LIST" 2>/dev/null | sort -u)"
    # A failing or skipped module is reported above, so it is not reported twice.
    reported="$(printf '%s\n%s\n' "$actual" "$skipped" | grep -v '^$' | sort -u)"
    no_tests="$(comm -23 <(echo "$listed") <(echo "$ran") | grep -v '^$' | comm -23 - <(echo "$reported"))"
    unlisted="$(comm -13 <(echo "$listed") <(echo "$ran") | grep -v '^$')"

    if [ -n "$no_tests" ]; then
        # The failure this check exists for: the build passes, and nothing was
        # tested. Look in target/test-classes before you trust any result.
        echo "NO TESTS — ran tests before, ran none now:"
        echo "$no_tests" | sed 's/^/  /'
        echo "  → check target/classes and target/test-classes for .class files"
        echo
        status=1
    fi

    if [ -n "$unlisted" ]; then
        echo "UNLISTED — ran tests, not named in ${TESTS_LIST#"$ROOT"/}:"
        echo "$unlisted" | sed 's/^/  /'
        echo "  → rerun with --record, then review and commit the list"
        echo
        status=1
    fi
fi

if [ -n "$skipped" ]; then
    # A skipped module is not a passing module. Skips mean an upstream module
    # failed to build, which hides everything downstream of it.
    echo "SKIPPED — never built, so their state is unknown:"
    echo "$skipped" | sed 's/^/  /'
    echo
    status=1
fi

if [ -n "$CI" ]; then
    # The reactor run cannot activate expose-webflux or rest-core-e2e, so the
    # agent HTTP and relay test classes run in their own builds. See
    # CHAT-frcrctdp and shell-scripts/agent-http-gate.sh.
    echo "running: shell-scripts/agent-http-gate.sh"
    if ! "$DIR/agent-http-gate.sh"; then
        echo "AGENT HTTP GATE — a class failed or was skipped; see above"
        echo
        status=1
    fi
fi

if [ "$status" -eq 0 ]; then
    echo "reality matches $DOC"
else
    echo "$DOC is out of date — see above"
    echo "full log: $log"
    trap - EXIT
fi

exit "$status"
