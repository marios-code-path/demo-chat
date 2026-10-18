#!/usr/bin/env bash
# Checks the flags of `just launch-shell` through `just dry-run-shell`, which
# calls launch-shell with --dry-run. No case starts the shell. See
# CHAT-msanybaw.
set -uo pipefail

cd "$(dirname "$0")/.."

failures=0

# expect_plan NAME PATTERN... -- ARGS...
# The dry-run plan must exit 0 and hold every pattern.
expect_plan() {
    local name="$1"
    shift
    local patterns=()
    while [[ "$1" != -- ]]; do
        patterns+=("$1")
        shift
    done
    shift
    local output
    if ! output="$(just dry-run-shell "$@" 2>&1)"; then
        echo "FAIL $name: the recipe exited non-zero"
        echo "$output" | tail -5
        failures=$((failures + 1))
        return
    fi
    local pattern
    for pattern in "${patterns[@]}"; do
        if ! grep -qF -- "$pattern" <<< "$output"; then
            echo "FAIL $name: the plan does not hold $pattern"
            failures=$((failures + 1))
            return
        fi
    done
    echo "ok   $name"
}

# expect_refusal NAME MESSAGE -- ARGS...
# The recipe must exit non-zero and print the message.
expect_refusal() {
    local name="$1" message="$2"
    shift 3
    local output status
    output="$(just dry-run-shell "$@" 2>&1)"
    status=$?
    if [[ $status -eq 0 ]] || ! grep -qF -- "$message" <<< "$output"; then
        echo "FAIL $name: expected a refusal with '$message', got exit $status"
        echo "$output" | tail -5
        failures=$((failures + 1))
        return
    fi
    echo "ok   $name"
}

expect_refusal 'no key type is refused, and long is no default' 'needs a key type. Use --key-type TYPE' \
    --
expect_refusal 'a node id alone is refused' 'needs a key type. Use --key-type TYPE' \
    -- 3
expect_plan '--key-type long is explicit' \
    -Dapp.key.type=long -Dapp.nodeid=3 -- 3 --key-type long
expect_plan '--key-type uuid matches a uuid core' \
    -Dapp.key.type=uuid -Dapp.nodeid=1 -- 1 --key-type uuid
expect_plan '--key-type=uuid form' \
    -Dapp.key.type=uuid -Dapp.nodeid=1 -- 1 --key-type=uuid
expect_plan '--key-type keeps the default node id' \
    -Dapp.key.type=uuid -Dapp.nodeid=1 -- --key-type uuid
expect_plan 'node_id= form' \
    -Dapp.key.type=uuid -Dapp.nodeid=4 -- node_id=4 --key-type uuid
expect_refusal 'chat-build refuses an unknown key type' "invalid choice: 'bogus'" \
    -- 1 --key-type bogus

# The ! command is a build option, and a shell build includes it by default.
# The shell-host profile adds chat-shell-host and the bash run image. See
# CHAT-jrlctimq.
expect_plan 'a shell build includes the ! command by default' \
    shell-host -- 1 --key-type uuid
expect_refusal '--host-command and --no-host-command are refused together' \
    '--host-command conflicts with --no-host-command' \
    -- 1 --key-type uuid --host-command --no-host-command

# build-shell and launch-shell --build. See CHAT-tkijkkbc. Every case uses
# --dry-run, so no case installs a module.
install_modules='chat-shell,chat-shell-host'
install_line="mvn -B -ntp -pl $install_modules -am -Dmaven.test.skip=true install"

# expect_output NAME RECIPE ARGS... -- WANT... -- UNWANTED...
expect_output() {
    local name="$1" recipe="$2"
    shift 2
    local args=() want=() unwanted=()
    while [[ "$1" != -- ]]; do args+=("$1"); shift; done
    shift
    while (($# > 0)) && [[ "$1" != -- ]]; do want+=("$1"); shift; done
    if (($# > 0)); then shift; unwanted=("$@"); fi
    local output pattern
    if ! output="$(just "$recipe" ${args[@]+"${args[@]}"} 2>&1)"; then
        echo "FAIL $name: the recipe exited non-zero"
        echo "$output" | tail -5
        failures=$((failures + 1))
        return
    fi
    for pattern in ${want[@]+"${want[@]}"}; do
        if ! grep -qF -- "$pattern" <<< "$output"; then
            echo "FAIL $name: the output does not hold $pattern"
            failures=$((failures + 1))
            return
        fi
    done
    for pattern in ${unwanted[@]+"${unwanted[@]}"}; do
        if grep -qF -- "$pattern" <<< "$output"; then
            echo "FAIL $name: the output holds $pattern"
            failures=$((failures + 1))
            return
        fi
    done
    echo "ok   $name"
}

expect_output 'build-shell --dry-run prints the install command' \
    build-shell --dry-run -- "$install_line"
expect_output 'dry-run-shell --build prints the install, then the shell plan' \
    dry-run-shell 1 --build --key-type uuid -- \
    "$install_line" -Dapp.key.type=uuid spring-boot:run -- spring-boot:build-image
expect_output 'dry-run-shell without --build installs nothing' \
    dry-run-shell 1 --key-type long -- spring-boot:run -- "$install_line"
expect_output '--no-host-command leaves the ! command out' \
    dry-run-shell 1 --key-type uuid --no-host-command -- spring-boot:run -- shell-host
expect_refusal_of() {
    local name="$1" recipe="$2" message="$3"
    shift 4
    local output status
    output="$(just "$recipe" "$@" 2>&1)"
    status=$?
    if [[ $status -eq 0 ]] || ! grep -qF -- "$message" <<< "$output"; then
        echo "FAIL $name: expected a refusal with '$message', got exit $status"
        failures=$((failures + 1))
        return
    fi
    echo "ok   $name"
}
expect_refusal_of 'build-shell refuses an unknown flag' build-shell \
    'Unknown build-shell flag: --bogus' -- --bogus

# The install must cover every com.demo module that the shell launch loads.
# The launch module depends on chat-shell under its profiles, and chat-shell
# depends on the launch module, so one reactor cannot hold both with those
# profiles. build-shell installs chat-shell and chat-shell-host -am instead. This check reads both
# sets from Maven, offline, and changes nothing.
plan="$(./shell-scripts/chat-build shell --run --notls --node-id 1 --key-type long --dry-run 2>&1)"
module="$(awk '/^# module:/ {print $3}' <<< "$plan")"
profiles="$(grep -o -- '-P[^ ]*' <<< "$plan" | head -n 1)"
# dependency:list resolves from ~/.m2. A module that is not installed fails
# the resolution, and its error text also names com.demo modules. So a failed
# resolution fails the check, and only the resolved dependency lines count.
deps_status=0
deps="$(mvn -o -B -ntp -f "$module/pom.xml" "$profiles" dependency:list \
    -DincludeGroupIds=com.demo 2>&1)" || deps_status=$?
needed="$( { grep -oE '^\[INFO\] +com\.demo:[a-z0-9-]+:' <<< "$deps" \
    | sed -E 's/.*com\.demo:([a-z0-9-]+):/\1/'
    echo "${module##*/}"; } | sort -u)"
reactor="$(mvn -o -B -ntp -pl "$install_modules" -am validate 2>&1 \
    | sed -n '/Reactor Summary/,/BUILD/p' | grep -o '^\[INFO\] [a-z][a-z0-9-]* \.' \
    | awk '{print $2}' | sort -u)"
missing="$(comm -23 <(printf '%s\n' "$needed") <(printf '%s\n' "$reactor"))"
if ((deps_status != 0)); then
    echo "FAIL dependency:list for the shell launch exited $deps_status. Run just build-shell first."
    grep -m 1 'ERROR' <<< "$deps"
    failures=$((failures + 1))
elif [[ -z "$needed" || -z "$reactor" ]]; then
    echo "FAIL the install coverage check read no module list"
    failures=$((failures + 1))
elif [[ -n "$missing" ]]; then
    echo "FAIL build-shell does not install: $(tr '\n' ' ' <<< "$missing")"
    failures=$((failures + 1))
else
    echo "ok   build-shell installs all $(wc -l <<< "$needed" | tr -d ' ') modules that the launch loads"
fi

if ((failures > 0)); then
    echo "$failures case(s) failed"
    exit 1
fi
echo 'all shell recipe cases passed'
