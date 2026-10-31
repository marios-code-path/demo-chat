#!/usr/bin/env bash
# Offline checks for verify-image-source. Stubs replace Docker.
# Fixture jars carry the git.properties stamps that the verifier reads.
# See docs/IMAGE-VERIFICATION.md and fp issue CHAT-gyfhbsbd.
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
. "$here/test-support/clear-legacy-inputs.sh" || exit $?
. "$here/test-support/clear-composition-inputs.sh" || exit $?
unset STUB_CREATE_FAIL STUB_CREATE_PARTIAL STUB_CP_FAIL STUB_IMAGE_COMMIT STUB_NO_STAMP DOCKER_CALLS DOCKER_STATE

verifier="$here/verify-image-source"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
checks=0
failures=0
pass() { checks=$((checks + 1)); printf 'ok %s\n' "$1"; }
fail() { checks=$((checks + 1)); failures=$((failures + 1)); printf 'FAIL %s\n' "$1"; }

commit=0123456789abcdef0123456789abcdef01234567
other=fedcba9876543210fedcba9876543210fedcba98
image_id=sha256:$(printf 'a%.0s' {1..64})

# The Docker stub records each call. It answers create, cp, and rm.
# Each created container ID goes to created, and each removed ID goes to removed.
mkdir -p "$work/bin"
cat > "$work/bin/docker" <<'STUB'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$DOCKER_CALLS"
case "$1" in
    create)
        [[ "${STUB_CREATE_FAIL:-}" != 1 ]] || exit 1
        id="container$RANDOM"
        printf '%s\n' "$id" >> "$DOCKER_CALLS.created"
        printf '%s\n' "$id"
        # STUB_CREATE_PARTIAL=1 creates the record and then reports a failure.
        [[ "${STUB_CREATE_PARTIAL:-}" != 1 ]] || exit 1
        ;;
    cp)
        [[ "${STUB_CP_FAIL:-}" != 1 ]] || exit 1
        stamp=$(mktemp -d)
        if [[ "${STUB_NO_STAMP:-}" == 1 ]]; then
            printf 'git.branch=main\n' > "$stamp/git.properties"
        else
            printf 'git.commit.id=%s\n' "$STUB_IMAGE_COMMIT" > "$stamp/git.properties"
        fi
        tar -cf - -C "$stamp" git.properties
        rm -rf "$stamp"
        ;;
    rm) printf '%s\n' "${@: -1}" >> "$DOCKER_CALLS.removed" ;;
    *) exit 1 ;;
esac
STUB
chmod +x "$work/bin/docker"

# make_jar PATH BOOT_STAMP NESTED_STAMP. An empty stamp leaves out git.commit.id.
make_jar() {
    local target="$1" boot="$2" nested="$3" build lib
    build=$(mktemp -d "$work/jar.XXXXXX")
    lib=$(mktemp -d "$work/lib.XXXXXX")
    mkdir -p "$build/BOOT-INF/classes" "$build/BOOT-INF/lib"
    if [[ -n "$nested" ]]; then printf 'git.commit.id=%s\n' "$nested" > "$lib/git.properties"
    else printf 'git.branch=main\n' > "$lib/git.properties"; fi
    (cd "$lib" && zip -q "$build/BOOT-INF/lib/chat-core-0.0.1.jar" git.properties \
        && zip -q "$build/BOOT-INF/lib/chat-api-0.0.1.jar" git.properties)
    printf 'unrelated\n' > "$lib/readme.txt"
    (cd "$lib" && zip -q "$build/BOOT-INF/lib/reactor-core-3.jar" readme.txt)
    if [[ -n "$boot" ]]; then
        printf 'git.commit.id=%s\n' "$boot" > "$build/BOOT-INF/classes/git.properties"
    fi
    (cd "$build" && zip -q -r "$target" BOOT-INF)
}

# verify NAME ENV-ASSIGNMENTS -- ARGS...: run the verifier with the stub.
verify() {
    local name="$1"
    shift
    local assignments=()
    while [[ "$1" != -- ]]; do assignments+=("$1"); shift; done
    shift
    calls="$work/$name.calls"
    : > "$calls"
    : > "$calls.created"
    : > "$calls.removed"
    status=0
    env PATH="$work/bin:$PATH" DOCKER_CALLS="$calls" STUB_IMAGE_COMMIT="$commit" \
        "${assignments[@]+"${assignments[@]}"}" "$verifier" "$@" \
        > "$work/$name.out" 2> "$work/$name.err" || status=$?
}

# cleaned NAME: every created container was removed once, and no other ID.
cleaned() {
    cmp -s <(sort "$work/$1.calls.created") <(sort "$work/$1.calls.removed")
}

expect() {
    local label="$1" wanted="$2" name="$3"
    if [[ "$status" == "$wanted" ]] && cleaned "$name"; then
        pass "$label"
    else
        fail "$label: expected $wanted, received $status"
        sed 's/^/    /' "$work/$name.err" 2> /dev/null | head -3
    fi
}

make_jar "$work/good.jar" "$commit" "$commit"
make_jar "$work/stale-boot.jar" "$other" "$commit"
make_jar "$work/stale-nested.jar" "$commit" "$other"
make_jar "$work/no-boot-stamp.jar" '' "$commit"
make_jar "$work/no-nested-stamp.jar" "$commit" ''

verify good -- --boot-jar "$work/good.jar" --commit "$commit" --image "$image_id"
expect 'matching stamps prove the source' 0 good
if grep -q '3 jars' "$work/good.out" && grep -qx "create $image_id" "$work/good.calls"; then
    pass 'the proof counts the boot jar and both chat jars, and it reads the image ID'
else
    fail 'the proof counts the boot jar and both chat jars, and it reads the image ID'
fi

verify stale-boot -- --boot-jar "$work/stale-boot.jar" --commit "$commit" --image "$image_id"
expect 'a stale boot-jar stamp fails with status 1' 1 stale-boot
verify stale-nested -- --boot-jar "$work/stale-nested.jar" --commit "$commit" --image "$image_id"
expect 'a stale nested-jar stamp fails with status 1' 1 stale-nested
verify no-boot -- --boot-jar "$work/no-boot-stamp.jar" --commit "$commit" --image "$image_id"
expect 'an absent boot-jar stamp fails with status 1' 1 no-boot
verify no-nested -- --boot-jar "$work/no-nested-stamp.jar" --commit "$commit" --image "$image_id"
expect 'an absent nested-jar stamp fails with status 1' 1 no-nested
verify stale-image STUB_IMAGE_COMMIT="$other" -- \
    --boot-jar "$work/good.jar" --commit "$commit" --image "$image_id"
expect 'a stale image-layer stamp fails with status 1 and removes its container' 1 stale-image
verify no-image-stamp STUB_NO_STAMP=1 -- \
    --boot-jar "$work/good.jar" --commit "$commit" --image "$image_id"
expect 'an absent image-layer stamp fails with status 1' 1 no-image-stamp
verify copy-fail STUB_CP_FAIL=1 -- \
    --boot-jar "$work/good.jar" --commit "$commit" --image "$image_id"
expect 'a failed copy fails with status 1 and removes its container' 1 copy-fail
if [[ -s "$work/copy-fail.calls.created" ]]; then
    pass 'the failed copy case created a container to remove'
else
    fail 'the failed copy case created a container to remove'
fi
verify create-fail STUB_CREATE_FAIL=1 -- \
    --boot-jar "$work/good.jar" --commit "$commit" --image "$image_id"
expect 'a failed create fails with status 1 and removes no container' 1 create-fail
verify create-partial STUB_CREATE_PARTIAL=1 -- \
    --boot-jar "$work/good.jar" --commit "$commit" --image "$image_id"
expect 'a create that fails after it made a record still removes that record' 1 create-partial
if [[ -s "$work/create-partial.calls.created" ]]; then
    pass 'the partial create case made a record to remove'
else
    fail 'the partial create case made a record to remove'
fi
if ! grep -q '^rm' "$work/create-fail.calls"; then
    pass 'a failed create sends no removal'
else
    fail 'a failed create sends no removal'
fi
if ! grep -qE '^(run|start|exec)' "$work"/*.calls; then
    pass 'the verifier starts no process'
else
    fail 'the verifier starts no process'
fi

# Invalid input gives status 2. The verifier has no deployment defaults.
verify no-args --
expect 'no arguments give status 2' 2 no-args
verify no-jar -- --commit "$commit" --image "$image_id"
expect 'a missing boot jar argument gives status 2' 2 no-jar
verify absent-jar -- --boot-jar "$work/absent.jar" --commit "$commit" --image "$image_id"
expect 'an absent boot jar file gives status 2' 2 absent-jar
verify bad-commit -- --boot-jar "$work/good.jar" --commit main --image "$image_id"
expect 'a commit that is not a full hexadecimal ID gives status 2' 2 bad-commit
verify reference -- --boot-jar "$work/good.jar" --commit "$commit" --image node-9/core:0.0.1
expect 'an image reference instead of an image ID gives status 2' 2 reference
verify short-id -- --boot-jar "$work/good.jar" --commit "$commit" --image sha256:0123456789abcdef
expect 'a short image ID gives status 2' 2 short-id
verify unknown -- --boot-jar "$work/good.jar" --commit "$commit" --image "$image_id" --extra
expect 'an unknown argument gives status 2' 2 unknown
if [[ -f "$verifier" ]] && ! grep -qE 'node-9|chat-deploy-cassandra|CORE_IMAGE' "$verifier"; then
    pass 'the verifier holds no deployment name'
else
    fail 'the verifier holds no deployment name'
fi

printf '%s checks, %s failures\n' "$checks" "$failures"
(( failures == 0 ))
