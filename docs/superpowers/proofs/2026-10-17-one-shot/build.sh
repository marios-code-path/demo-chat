#!/usr/bin/env bash
# Builds the shell artifacts of the stage 0 proof at the current commit, and
# checks that each project jar on the shell classpath is the jar this build
# made. Maven builds. Maven is never the measured shell process. The
# classpath holds runtime scope alone, as a shell launch does. The default
# scope of build-classpath also adds test jars, such as chat-core tests.
set -uo pipefail
S="${1:?usage: build.sh <scratch-dir>}"
cd "$(git rev-parse --show-toplevel)"
# Untracked proof files do not reach any module, so only tracked changes count.
if [[ -n "$(git status --porcelain --untracked-files=no)" ]]; then
    echo "build: the tree has tracked changes" >&2
    exit 2
fi
git rev-parse HEAD > "$S/commit"
just build-shell > "$S/build-shell.log" 2>&1 || { echo "build: just build-shell failed, see $S/build-shell.log" >&2; exit 1; }
mvn -o -B -ntp -f chat-deploy/pom.xml -Pdeploy,shell,shell-host,client-backend \
    -Dmaven.test.skip=true compile dependency:build-classpath \
    -DincludeScope=runtime \
    -Dmdep.outputFile="$S/shell.cp" > "$S/build-deploy.log" 2>&1 \
    || { echo "build: chat-deploy compile failed, see $S/build-deploy.log" >&2; exit 1; }
differ=0
: > "$S/artifacts.tsv"
tr ':' '\n' < "$S/shell.cp" | grep '/com/demo/' | while read -r jar; do
    module="$(basename "$(dirname "$(dirname "$jar")")")"
    built="$module/target/$(basename "$jar")"
    installed_sum="$(shasum -a 256 "$jar" | cut -c1-64)"
    built_sum="$( [[ -f "$built" ]] && shasum -a 256 "$built" | cut -c1-64 || echo missing)"
    state=match
    [[ "$installed_sum" == "$built_sum" ]] || state=differ
    printf '%s\t%s\t%s\t%s\n' "$module" "$installed_sum" "$built_sum" "$state" >> "$S/artifacts.tsv"
done
if grep -q 'differ$' "$S/artifacts.tsv" || [[ ! -s "$S/artifacts.tsv" ]]; then
    echo "build: a project jar on the classpath is not from this build" >&2
    exit 3
fi
