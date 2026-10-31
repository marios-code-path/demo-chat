#!/usr/bin/env bash
# Offline checks for the node 9 image build. See CHAT-gwrrazjg.
#
# Stubs on PATH replace mvn, docker, and chat-build. So the suite compiles
# nothing, and it builds no image. A fixture boot jar carries the stamps that
# the jar proof reads. NODE9_CHAT_BUILD and CHAT_DEPLOY_CASSANDRA_TARGET_DIR
# point the script at the stub and at the fixture.
set -uo pipefail

. "$(dirname "${BASH_SOURCE[0]}")/test-support/clear-legacy-inputs.sh" || exit $?

cd "$(dirname "$0")/.."
repo_root="$PWD"
builder="$repo_root/shell-scripts/build-node-9-image"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

failures=0
ok() { printf 'ok   %s\n' "$1"; }
bad() { printf 'FAIL %s: %s\n' "$1" "$2"; failures=$((failures + 1)); }

tree_commit="$(git -C "$repo_root" rev-parse HEAD)"
core_image="$(sed -n 's/^CORE_IMAGE=//p' "$repo_root/node-9/node-9.env" | head -1)"

stub_bin="$work/bin"
mkdir -p "$stub_bin"

# A mvn stub. It records every call, and it compiles nothing.
cat > "$stub_bin/mvn" <<'STUB'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "${MVN_CALLS:?Set MVN_CALLS}"
exit 0
STUB

# A chat-build stub. It records its arguments and the four environment names
# that the image build must carry.
cat > "$stub_bin/chat-build-stub" <<'STUB'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "${BUILD_CALLS:?Set BUILD_CALLS}"
{
    printf 'IMAGE_REPO_PREFIX=%s\n' "${IMAGE_REPO_PREFIX:-}"
    printf 'CONSUL_HOST=%s\n' "${CONSUL_HOST:-}"
    printf 'CONSUL_PORT=%s\n' "${CONSUL_PORT:-}"
    printf 'CASSANDRA_CONTACT_POINTS=%s\n' "${CASSANDRA_CONTACT_POINTS:-}"
    printf 'DOCKER_CONFIG=%s\n' "${DOCKER_CONFIG:-}"
} > "$BUILD_CALLS.env"
if [[ "${STUB_BUILD_FAIL:-}" == 1 ]]; then
    printf 'STUB IMAGE BUILD FAILED\n' >&2
    exit 3
fi
exit 0
STUB

# A docker stub. It records every call. It answers inspect, create, cp, and
# rm. STUB_IMAGE_MISSING hides the image. STUB_CP_FAIL refuses the file read.
# STUB_IMAGE_COMMIT sets the commit inside the image. The creation time is
# the fixed value that a buildpack writes.
cat > "$stub_bin/docker" <<'STUB'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "${DOCKER_CALLS:?Set DOCKER_CALLS}"
case "$1" in
    image)
        if [[ "${STUB_IMAGE_MISSING:-}" == 1 ]]; then exit 1; fi
        printf 'sha256:0123456789abcdef amd64 1980-01-01T00:00:01Z\n'
        ;;
    create)
        if [[ "${STUB_IMAGE_MISSING:-}" == 1 ]]; then exit 1; fi
        printf 'stubcontainer0001\n'
        ;;
    cp)
        if [[ "${STUB_CP_FAIL:-}" == 1 ]]; then exit 1; fi
        stamp_dir="$(mktemp -d)"
        printf 'git.commit.id=%s\n' "${STUB_IMAGE_COMMIT:?Set STUB_IMAGE_COMMIT}" \
            > "$stamp_dir/git.properties"
        tar -cf - -C "$stamp_dir" git.properties
        rm -rf "$stamp_dir"
        ;;
    rm) ;;
esac
exit 0
STUB
chmod +x "$stub_bin/mvn" "$stub_bin/chat-build-stub" "$stub_bin/docker"

# make_boot_jar PATH MODULE_COMMIT NESTED_COMMIT  Write one fixture boot jar.
# The jar holds BOOT-INF/classes/git.properties and one nested module jar
# under BOOT-INF/lib, as a Spring Boot repackage writes them.
make_boot_jar() {
    local target="$1" module_commit="$2" nested_commit="$3"
    local build nested
    build="$(mktemp -d)"
    nested="$(mktemp -d)"
    printf 'git.commit.id=%s\n' "$nested_commit" > "$nested/git.properties"
    mkdir -p "$build/BOOT-INF/classes" "$build/BOOT-INF/lib"
    ( cd "$nested" && zip -q -r "$build/BOOT-INF/lib/chat-core-0.0.1.jar" \
        git.properties )
    printf 'git.commit.id=%s\n' "$module_commit" \
        > "$build/BOOT-INF/classes/git.properties"
    rm -f "$target"
    ( cd "$build" && zip -q -r "$target" BOOT-INF )
    rm -rf "$build" "$nested"
}

# run_build DIR [env assignments...]  Run the script against one target dir.
run_build() {
    local target_dir="$1"
    shift
    rm -f "$work/mvn-calls" "$work/build-calls" "$work/build-calls.env" \
        "$work/docker-calls"
    out="$(env PATH="$stub_bin:$PATH" \
        MVN_CALLS="$work/mvn-calls" BUILD_CALLS="$work/build-calls" \
        DOCKER_CALLS="$work/docker-calls" \
        NODE9_CHAT_BUILD="$stub_bin/chat-build-stub" \
        CHAT_DEPLOY_CASSANDRA_TARGET_DIR="$target_dir" \
        STUB_IMAGE_COMMIT="$tree_commit" \
        "$@" "$builder" 2>&1)"
    status=$?
}

# A dry run names the image and both build steps. It starts nothing.
out="$("$builder" --dry-run 2>&1)"
status=$?
dry_case='ok'
if [[ $status -ne 0 ]]; then
    dry_case="exit $status, output: $out"
elif [[ "$out" != *"image: $core_image"* ]]; then
    dry_case='the plan names no image'
elif [[ "$out" != *'clean install'* ]]; then
    dry_case='the plan names no clean module build'
elif [[ "$out" != *'--key-type uuid'* || "$out" != *'--index lucene'* ]]; then
    dry_case='the plan names no node 9 flag set'
elif [[ "$out" != *"the git.properties of $core_image"* ]]; then
    dry_case='the plan names no image proof'
fi
if [[ "$dry_case" == 'ok' ]]; then
    ok 'dry_run_names_the_image_and_the_steps'
else
    bad 'dry_run_names_the_image_and_the_steps' "$dry_case"
fi

out="$("$builder" --wrong 2>&1)"
if [[ $? -eq 2 && "$out" == *'unknown argument'* ]]; then
    ok 'build_refuses_an_unknown_argument'
else
    bad 'build_refuses_an_unknown_argument' "output: $out"
fi

# A complete run. Every stamp equals the commit of the working tree.
good_target="$work/target-good"
mkdir -p "$good_target"
make_boot_jar "$good_target/chat-deploy-cassandra-0.0.1-exec.jar" \
    "$tree_commit" "$tree_commit"
run_build "$good_target"
pass_case='ok'
if [[ $status -ne 0 ]]; then
    pass_case="exit $status, output: $out"
else
    for name in 'module build' 'image build' 'jar proof' 'image proof'; do
        [[ "$out" == *"$name"* ]] || pass_case="the output names no $name phase"
    done
    [[ "$out" == *'[4/4]'* ]] || pass_case='the output names no fourth phase'
    [[ "$out" == *"2 jars at $tree_commit"* ]] \
        || pass_case="the proof reports $(printf '%s' "$out" | grep 'jar proof done')"
fi
if [[ "$pass_case" == 'ok' ]]; then
    ok 'build_prints_a_line_for_each_phase'
else
    bad 'build_prints_a_line_for_each_phase' "$pass_case"
fi

# The module build runs clean, so no earlier target directory reaches the
# image.
clean_case='ok'
if ! grep -q 'clean install' "$work/mvn-calls"; then
    clean_case="the Maven call is $(cat "$work/mvn-calls")"
elif ! grep -q 'chat-deploy-cassandra' "$work/mvn-calls"; then
    clean_case='the Maven call names another module'
fi
if [[ "$clean_case" == 'ok' ]]; then
    ok 'build_runs_a_clean_module_build'
else
    bad 'build_runs_a_clean_module_build' "$clean_case"
fi

# The image build takes the flag set and the four environment names of
# node-9/bin/start. It also takes an empty Docker configuration.
flag_case='ok'
for flag in '--cassandra' '--index lucene' '--pubsub redis-pubsub' '--notls' \
    '--node-id 9' '--key-type uuid' '--vector embedded' '--embedding openai' \
    '--init users,rootkeys' '--consul' '--build'; do
    grep -q -- "$flag" "$work/build-calls" \
        || flag_case="the image build takes no $flag"
done
for pair in 'IMAGE_REPO_PREFIX=node-9' 'CONSUL_HOST=consul' \
    'CONSUL_PORT=8500' 'CASSANDRA_CONTACT_POINTS=cassandra'; do
    grep -qx -- "$pair" "$work/build-calls.env" \
        || flag_case="the image build takes no $pair"
done
if [[ "$flag_case" == 'ok' ]]; then
    docker_config="$(sed -n 's/^DOCKER_CONFIG=//p' "$work/build-calls.env")"
    if [[ -z "$docker_config" ]]; then
        flag_case='the image build takes no Docker configuration'
    elif [[ "$(cat "$docker_config/config.json" 2> /dev/null)" != '{}' ]]; then
        flag_case='the Docker configuration holds a login'
    fi
fi
if [[ "$flag_case" == 'ok' ]]; then
    ok 'build_passes_the_node_9_flag_set'
else
    bad 'build_passes_the_node_9_flag_set' "$flag_case"
fi

# A nested module jar of another commit stops the run.
stale_target="$work/target-stale"
mkdir -p "$stale_target"
make_boot_jar "$stale_target/chat-deploy-cassandra-0.0.1-exec.jar" \
    "$tree_commit" '0123456789abcdef0123456789abcdef01234567'
run_build "$stale_target"
if [[ $status -ne 0 && "$out" == *'not fresh'* ]]; then
    ok 'build_refuses_a_nested_jar_of_another_commit'
else
    bad 'build_refuses_a_nested_jar_of_another_commit' \
        "exit $status, output: $out"
fi

# A boot jar of another commit stops the run as well.
module_target="$work/target-module"
mkdir -p "$module_target"
make_boot_jar "$module_target/chat-deploy-cassandra-0.0.1-exec.jar" \
    '0123456789abcdef0123456789abcdef01234567' "$tree_commit"
run_build "$module_target"
if [[ $status -ne 0 && "$out" == *'not fresh'* ]]; then
    ok 'build_refuses_a_boot_jar_of_another_commit'
else
    bad 'build_refuses_a_boot_jar_of_another_commit' \
        "exit $status, output: $out"
fi

# A target directory without a boot jar stops the run.
mkdir -p "$work/target-empty"
run_build "$work/target-empty"
if [[ $status -ne 0 && "$out" == *'no boot jar'* ]]; then
    ok 'build_refuses_a_target_without_a_boot_jar'
else
    bad 'build_refuses_a_target_without_a_boot_jar' \
        "exit $status, output: $out"
fi

# An absent image stops the run. The build tagged another reference.
run_build "$good_target" STUB_IMAGE_MISSING=1
if [[ $status -ne 0 && "$out" == *'another reference'* ]]; then
    ok 'build_refuses_an_absent_image'
else
    bad 'build_refuses_an_absent_image' "exit $status, output: $out"
fi

# An image that holds another commit stops the run. The creation time of a
# buildpack image is fixed, so the commit inside the image is the proof.
run_build "$good_target" \
    STUB_IMAGE_COMMIT=0123456789abcdef0123456789abcdef01234567
if [[ $status -ne 0 && "$out" == *'The image is not fresh'* ]]; then
    ok 'build_refuses_an_image_of_another_commit'
else
    bad 'build_refuses_an_image_of_another_commit' \
        "exit $status, output: $out"
fi

# An unreadable stamp inside the image stops the run.
run_build "$good_target" STUB_CP_FAIL=1
if [[ $status -ne 0 && "$out" == *'cannot read'* ]]; then
    ok 'build_refuses_an_image_without_a_readable_stamp'
else
    bad 'build_refuses_an_image_without_a_readable_stamp' \
        "exit $status, output: $out"
fi

# The image proof starts no process, and it leaves no container record.
run_build "$good_target"
container_case='ok'
if [[ $status -ne 0 ]]; then
    container_case="exit $status, output: $out"
elif ! grep -qx -- "create $core_image" "$work/docker-calls"; then
    container_case='the proof creates no container record'
elif grep -qE '^(run|start) ' "$work/docker-calls"; then
    container_case="the proof starts a process: $(grep -E '^(run|start) ' "$work/docker-calls")"
elif ! grep -qx -- 'rm -f stubcontainer0001' "$work/docker-calls"; then
    container_case='the proof removes no container record'
fi
if [[ "$container_case" == 'ok' ]]; then
    ok 'image_proof_starts_no_process_and_leaves_no_container'
else
    bad 'image_proof_starts_no_process_and_leaves_no_container' "$container_case"
fi

# A failed image build names its log, and it prints the end of that log.
run_build "$good_target" STUB_BUILD_FAIL=1
if [[ $status -ne 0 && "$out" == *'image build failed'* \
      && "$out" == *'STUB IMAGE BUILD FAILED'* ]]; then
    ok 'build_names_the_log_of_a_failed_phase'
else
    bad 'build_names_the_log_of_a_failed_phase' "exit $status, output: $out"
fi

if ((failures > 0)); then
    echo "$failures case(s) failed"
    exit 1
fi
echo 'all node-9 image build cases passed'
