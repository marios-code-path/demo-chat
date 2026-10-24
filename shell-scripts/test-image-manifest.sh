#!/usr/bin/env bash
# Offline checks for the image manifest of chat-build. No Maven runs, and no
# Docker daemon answers. Stubs stand for both. See CHAT-jmnjvupr.
#
# The manifest records every image that one deployment run produced, so a push
# can read one list. chat-build builds one image on each call, so each call
# appends one record.
set -uo pipefail

cd "$(dirname "$0")/.."
repo_root="$PWD"
build="$repo_root/shell-scripts/chat-build"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

failures=0
ok() { printf 'ok   %s\n' "$1"; }
bad() { printf 'FAIL %s: %s\n' "$1" "$2"; failures=$((failures + 1)); }

stub_bin="$work/bin"
mkdir -p "$stub_bin"

# The Maven stub answers two calls. A property query prints one image
# reference. Every other call is the build, and MVN_EXIT sets its status.
cat > "$stub_bin/mvn" <<'STUB'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "${MVN_CALLS:?Set MVN_CALLS}"
for word in "$@"; do
    if [[ "$word" == 'help:evaluate' ]]; then
        printf '%s' "${STUB_REF:?Set STUB_REF}"
        exit 0
    fi
done
exit "${MVN_EXIT:-0}"
STUB
chmod +x "$stub_bin/mvn"

# The Docker stub answers the image query of one reference. DOCKER_EXIT sets
# its status, and STUB_DIGESTS sets the repository digest list.
cat > "$stub_bin/docker" <<'STUB'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "${DOCKER_CALLS:?Set DOCKER_CALLS}"
if [[ "${1:-}" == 'image' && "${2:-}" == 'inspect' ]]; then
    if (( ${DOCKER_EXIT:-0} != 0 )); then
        printf 'Error: No such image\n' >&2
        exit 1
    fi
    printf '{"Id":"%s","Architecture":"%s","Os":"linux","RepoDigests":%s}\n' \
        "${STUB_ID-sha256:1111}" "${STUB_ARCH-arm64}" "${STUB_DIGESTS:-[]}"
    exit 0
fi
exit 0
STUB
chmod +x "$stub_bin/docker"

# run_build MANIFEST [env assignments...] -- [chat-build arguments...]
# Run one build of the core service with the stubs. The output holds both
# streams. The status lands in $status.
run_build() {
    local manifest="$1"
    shift
    local env_pairs=()
    local args=(core --memory --build --notls --long --node-id 0)
    local seen_dash=false
    local word
    for word in "$@"; do
        if [[ "$word" == '--' ]]; then
            seen_dash=true
            args=()
            continue
        fi
        if [[ "$seen_dash" == 'true' ]]; then
            args+=("$word")
        else
            env_pairs+=("$word")
        fi
    done
    out="$(env PATH="$stub_bin:$PATH" CONSUL_HOST=10.0.0.5 CHAT_REGISTRY= \
        KEYSTORE_PASS=test-pass CASSANDRA_CONTACT_POINTS=127.0.0.1 \
        MVN_CALLS="$work/mvn-calls" DOCKER_CALLS="$work/docker-calls" \
        CHAT_IMAGE_MANIFEST="$manifest" "${env_pairs[@]}" \
        "$build" "${args[@]}" 2>&1)"
    status=$?
}

records_of() { jq -r 'length' "$1" 2> /dev/null; }

# -- One build writes one record -----------------------------------------

: > "$work/mvn-calls"
: > "$work/docker-calls"
manifest="$work/run-one.json"
run_build "$manifest" STUB_REF=docker.io/library/memory-core-service-rsocket:0.0.1 \
    STUB_ID=sha256:aaaa STUB_ARCH=arm64
case_one='ok'
if (( status != 0 )); then
    case_one="exit $status, output: $out"
elif [[ ! -f "$manifest" ]]; then
    case_one='the manifest is absent'
elif [[ "$(records_of "$manifest")" != '1' ]]; then
    case_one="the manifest holds $(records_of "$manifest") records"
elif [[ "$(jq -r '.[0].reference' "$manifest")" \
        != 'docker.io/library/memory-core-service-rsocket:0.0.1' ]]; then
    case_one="the reference is $(jq -r '.[0].reference' "$manifest")"
elif [[ "$(jq -r '.[0].imageId' "$manifest")" != 'sha256:aaaa' ]]; then
    case_one="the image id is $(jq -r '.[0].imageId' "$manifest")"
elif [[ "$(jq -r '.[0].architecture' "$manifest")" != 'arm64' ]]; then
    case_one="the architecture is $(jq -r '.[0].architecture' "$manifest")"
fi
if [[ "$case_one" == 'ok' ]]; then
    ok 'manifest_records_one_build'
else
    bad 'manifest_records_one_build' "$case_one"
fi

# A build with no repository digest records none. A local image holds no
# digest until a push gives it one.
if [[ -f "$manifest" ]] \
   && [[ "$(jq -r '.[0] | has("digest")' "$manifest" 2> /dev/null)" == 'false' ]]; then
    ok 'manifest_leaves_out_an_absent_digest'
else
    bad 'manifest_leaves_out_an_absent_digest' \
        "the record is $(jq -c '.[0]' "$manifest" 2> /dev/null)"
fi

# -- The manifest accumulates across calls -------------------------------

run_build "$manifest" STUB_REF=docker.io/library/chat-rest:0.0.1 \
    STUB_ID=sha256:bbbb
if (( status == 0 )) && [[ "$(records_of "$manifest")" == '2' ]] \
   && [[ "$(jq -r '[.[].reference] | sort | join(",")' "$manifest")" \
         == 'docker.io/library/chat-rest:0.0.1,docker.io/library/memory-core-service-rsocket:0.0.1' ]]; then
    ok 'manifest_accumulates_across_calls'
else
    bad 'manifest_accumulates_across_calls' \
        "records $(records_of "$manifest"), output: $out"
fi

# -- One reference holds one record --------------------------------------

run_build "$manifest" STUB_REF=docker.io/library/chat-rest:0.0.1 \
    STUB_ID=sha256:cccc
if (( status == 0 )) && [[ "$(records_of "$manifest")" == '2' ]] \
   && [[ "$(jq -r '.[] | select(.reference == "docker.io/library/chat-rest:0.0.1") | .imageId' "$manifest")" \
         == 'sha256:cccc' ]]; then
    ok 'manifest_replaces_one_reference'
else
    bad 'manifest_replaces_one_reference' \
        "records $(records_of "$manifest"), ids $(jq -c '[.[].imageId]' "$manifest" 2> /dev/null)"
fi

# -- A failed build writes nothing ---------------------------------------

before="$(cat "$manifest")"
run_build "$manifest" MVN_EXIT=1 STUB_REF=docker.io/library/chat-gateway:0.0.1
if (( status != 0 )) && [[ "$(cat "$manifest")" == "$before" ]]; then
    ok 'failed_build_writes_no_record'
else
    bad 'failed_build_writes_no_record' \
        "exit $status, records $(records_of "$manifest")"
fi

# -- An absent image writes nothing --------------------------------------

: > "$work/docker-calls"
run_build "$manifest" DOCKER_EXIT=1 STUB_REF=docker.io/library/chat-shell:0.0.1
if [[ "$(cat "$manifest")" != "$before" ]]; then
    bad 'absent_image_writes_no_record' 'the manifest changed'
elif [[ "$out" != *'chat-shell'* ]]; then
    bad 'absent_image_writes_no_record' "the output names no image: $out"
else
    ok 'absent_image_writes_no_record'
fi

# -- The digest reaches the record when the image holds one --------------

digest_manifest="$work/digest.json"
run_build "$digest_manifest" STUB_REF=docker.io/library/chat-web:0.0.1 \
    STUB_DIGESTS='["docker.io/library/chat-web@sha256:dddd"]'
if (( status == 0 )) \
   && [[ "$(jq -r '.[0].digest' "$digest_manifest" 2> /dev/null)" \
         == 'docker.io/library/chat-web@sha256:dddd' ]]; then
    ok 'manifest_records_a_present_digest'
else
    bad 'manifest_records_a_present_digest' \
        "record $(jq -c '.[0]' "$digest_manifest" 2> /dev/null)"
fi

matching_digest="$work/matching-digest.json"
run_build "$matching_digest" STUB_REF=docker.io/library/chat-web:0.0.1 \
    STUB_DIGESTS='["other/image@sha256:aaaa","docker.io/library/chat-web@sha256:bbbb"]'
if (( status == 0 )) \
   && [[ "$(jq -r '.[0].digest' "$matching_digest" 2> /dev/null)" \
         == 'docker.io/library/chat-web@sha256:bbbb' ]]; then
    ok 'manifest_uses_matching_digest'
else
    bad 'manifest_uses_matching_digest' "exit $status, record: $(cat "$matching_digest" 2> /dev/null)"
fi

missing_fields="$work/missing-fields.json"
run_build "$missing_fields" STUB_REF=docker.io/library/chat-web:0.0.1 \
    STUB_ID= STUB_ARCH=
if (( status != 0 )) && [[ ! -f "$missing_fields" ]]; then
    ok 'manifest_refuses_missing_image_fields'
else
    bad 'manifest_refuses_missing_image_fields' "exit $status, record: $(cat "$missing_fields" 2> /dev/null)"
fi

# -- No name, no manifest and no image query -----------------------------

: > "$work/docker-calls"
out="$(env PATH="$stub_bin:$PATH" CONSUL_HOST=10.0.0.5 CHAT_REGISTRY= \
    KEYSTORE_PASS=test-pass CASSANDRA_CONTACT_POINTS=127.0.0.1 \
    MVN_CALLS="$work/mvn-calls" DOCKER_CALLS="$work/docker-calls" \
    STUB_REF=docker.io/library/chat-rest:0.0.1 \
    "$build" core --memory --build --notls --long --node-id 0 2>&1)"
status=$?
if (( status == 0 )) && ! grep -q 'image inspect' "$work/docker-calls"; then
    ok 'no_manifest_name_keeps_the_earlier_behavior'
else
    bad 'no_manifest_name_keeps_the_earlier_behavior' \
        "exit $status, docker calls: $(cat "$work/docker-calls")"
fi

# -- The write leaves no temporary file ----------------------------------

# The lock file of the write stays beside the manifest, because a lock needs
# one stable path. Every other file of that prefix is a stage file, and a
# finished write holds none.
stage_files="$(find "$work" -maxdepth 1 -name 'digest.json.*' \
    -not -name 'digest.json.lock' | grep -c .)"
if [[ -f "$work/digest.json" ]] && [[ "$stage_files" == '0' ]]; then
    ok 'manifest_write_leaves_no_stage_file'
else
    bad 'manifest_write_leaves_no_stage_file' \
        "$(find "$work" -maxdepth 1 -name 'digest.json*' | tr '\n' ' ')"
fi

# -- A file of another JSON type stays as it is --------------------------

other_type="$work/other-type.json"
printf '{"images": []}\n' > "$other_type"
run_build "$other_type" STUB_REF=docker.io/library/chat-rest:0.0.1
if [[ "$(cat "$other_type")" != '{"images": []}' ]]; then
    bad 'manifest_of_another_type_stays' 'the file changed'
elif (( status == 0 )); then
    bad 'manifest_of_another_type_stays' 'the build reported success'
elif [[ "$out" != *'other-type.json'* ]]; then
    bad 'manifest_of_another_type_stays' "the output names no file: $out"
else
    ok 'manifest_of_another_type_stays'
fi

# A list with an invalid record must not lose that record during an update.
invalid_record="$work/invalid-record.json"
printf '[{"imageId":"sha256:old"}]\n' > "$invalid_record"
run_build "$invalid_record" STUB_REF=docker.io/library/chat-rest:0.0.1
if [[ "$(cat "$invalid_record")" == '[{"imageId":"sha256:old"}]' ]] \
   && (( status != 0 )); then
    ok 'manifest_with_invalid_record_stays'
else
    bad 'manifest_with_invalid_record_stays' "exit $status, file: $(cat "$invalid_record")"
fi

# -- Builds that share one manifest keep every record --------------------

parallel="$work/parallel.json"
for index in 1 2 3 4 5 6; do
    run_build "$parallel" "STUB_REF=docker.io/library/image-$index:0.0.1" \
        "STUB_ID=sha256:$index" &
done
wait
if [[ "$(records_of "$parallel")" == '6' ]]; then
    ok 'shared_manifest_keeps_every_record'
else
    bad 'shared_manifest_keeps_every_record' \
        "the manifest holds $(records_of "$parallel") records of 6"
fi

# -- The reference list command ------------------------------------------

references="$repo_root/shell-scripts/image-references"
if [[ ! -x "$references" ]]; then
    bad 'references_command_prints_each_reference' "no command at $references"
    bad 'references_command_refuses_another_type' "no command at $references"
else
    out="$("$references" "$parallel" 2>&1)"
    status=$?
    if (( status == 0 )) && [[ "$(printf '%s\n' "$out" | grep -c .)" == '6' ]] \
       && [[ "$(printf '%s\n' "$out" | grep -c '^docker.io/library/image-')" == '6' ]]; then
        ok 'references_command_prints_each_reference'
    else
        bad 'references_command_prints_each_reference' "exit $status, output: $out"
    fi

    out="$("$references" "$other_type" 2>&1)"
    status=$?
    if (( status != 0 )) && [[ "$out" == *'other-type.json'* ]]; then
        ok 'references_command_refuses_another_type'
    else
        bad 'references_command_refuses_another_type' "exit $status, output: $out"
    fi

    numeric_reference="$work/numeric-reference.json"
    printf '[{"reference":42}]\n' > "$numeric_reference"
    out="$("$references" "$numeric_reference" 2>&1)"
    status=$?
    if (( status != 0 )) && [[ "$out" == *'numeric-reference.json'* ]]; then
        ok 'references_command_refuses_nontext_reference'
    else
        bad 'references_command_refuses_nontext_reference' "exit $status, output: $out"
    fi
fi

if ((failures > 0)); then
    echo "$failures case(s) failed"
    exit 1
fi
echo 'all image manifest cases passed'
