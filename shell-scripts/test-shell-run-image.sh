#!/usr/bin/env bash
# Checks the run image of the chat-shell image build. The shell profile of
# chat-deploy names a run image that holds bash, so the ! command works in a
# chat-shell container. Other images keep the default tiny run image, which
# holds no shell. Reads the effective POM. Builds no image. See CHAT-jrlctimq.
set -uo pipefail

cd "$(dirname "$0")/.."

expected='docker.io/paketobuildpacks/ubuntu-noble-run:0.0.145'
failures=0
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

# run_image PROFILES: prints the distinct runImage values of the effective
# build. The effective POM also lists every declared profile, active or not,
# so the profiles block is removed first. Plugin executions repeat the merged
# configuration, so the values are made distinct.
run_image() {
    local output="$work/$1.xml"
    if ! mvn -q -f chat-deploy/pom.xml -P"$1" help:effective-pom -Doutput="$output" >"$work/$1.log" 2>&1; then
        echo "FAIL effective POM for $1"
        tail -5 "$work/$1.log"
        failures=$((failures + 1))
        return
    fi
    sed '/<profiles>/,/<\/profiles>/d' "$output" |
        sed -n 's:.*<runImage>\(.*\)</runImage>.*:\1:p' | sort -u
}

shell_image="$(run_image deploy,shell,client-backend)"
if [[ "$shell_image" == "$expected" ]]; then
    echo "ok   the shell image runs on $expected"
else
    echo "FAIL the shell image runs on '$shell_image', want $expected"
    failures=$((failures + 1))
fi

core_image="$(run_image deploy,client-backend)"
if [[ -z "$core_image" ]]; then
    echo "ok   other images keep the default run image"
else
    echo "FAIL other images name the run image '$core_image'"
    failures=$((failures + 1))
fi

if ((failures > 0)); then
    echo "$failures case(s) failed"
    exit 1
fi
echo "all shell run image cases passed"
