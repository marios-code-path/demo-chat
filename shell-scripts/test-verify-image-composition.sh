#!/usr/bin/env bash
# Offline checks for verify-image-composition. A stub replaces Docker.
# See docs/IMAGE-VERIFICATION.md and fp issue CHAT-gyfhbsbd.
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
. "$here/test-support/clear-legacy-inputs.sh" || exit $?
. "$here/test-support/clear-composition-inputs.sh" || exit $?
unset STUB_LABEL STUB_INSPECT_STATUS STUB_INSPECT_BODY STUB_RETAG STUB_IMAGE_COMMIT \
    DOCKER_CALLS DOCKER_STATE

verifier="$here/verify-image-composition"
source_verifier="$here/verify-image-source"
expected="$here/fixtures/image-composition/expected.json"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
checks=0
failures=0
pass() { checks=$((checks + 1)); printf 'ok %s\n' "$1"; }
fail() { checks=$((checks + 1)); failures=$((failures + 1)); printf 'FAIL %s\n' "$1"; }

commit=0123456789abcdef0123456789abcdef01234567
id_a=sha256:$(printf 'a%.0s' {1..64})
id_b=sha256:$(printf 'b%.0s' {1..64})
digest=$(printf '%s' "$(cat "$expected")" | shasum -a 256 | cut -d' ' -f1)
other_digest=$(printf '0%.0s' {1..64})

# The Docker stub answers image inspect, create, cp, and rm.
# The reference names image A. STUB_RETAG=1 moves it to image B after the first inspection.
# Image B holds another label. Both images hold the stamp of $commit.
mkdir -p "$work/bin"
cat > "$work/bin/docker" <<'STUB'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$DOCKER_CALLS"
image_json() {
    local labels="null"
    [[ "$2" == none ]] || labels="{\"com.demo.chat.composition.sha256\":\"$2\",\"org.example.team\":\"chat\"}"
    printf '{"Id":"%s","Architecture":"arm64","Config":{"Labels":%s}}\n' "$1" "$labels"
}
case "$1 ${2:-}" in
    'image inspect')
        target="${*: -1}"
        [[ -z "${STUB_INSPECT_STATUS:-}" ]] || exit "$STUB_INSPECT_STATUS"
        if [[ -n "${STUB_INSPECT_BODY+set}" ]]; then printf '%s\n' "$STUB_INSPECT_BODY"; exit 0; fi
        if [[ "$target" == "$ID_B" ]] || { [[ "$target" == example/core:1 ]] \
                && [[ "${STUB_RETAG:-}" == 1 && -e "$DOCKER_CALLS.seen" ]]; }; then
            image_json "$ID_B" "$OTHER_DIGEST"
        elif [[ "$target" == "$ID_A" || "$target" == example/core:1 ]]; then
            image_json "$ID_A" "$STUB_LABEL"
        else
            exit 1
        fi
        : > "$DOCKER_CALLS.seen"
        ;;
    create*) printf 'container1\n' ;;
    cp*)
        stamp=$(mktemp -d)
        printf 'git.commit.id=%s\n' "$STUB_IMAGE_COMMIT" > "$stamp/git.properties"
        tar -cf - -C "$stamp" git.properties
        rm -rf "$stamp"
        ;;
    rm*) ;;
    *) exit 1 ;;
esac
STUB
chmod +x "$work/bin/docker"

# verify NAME ENV-ASSIGNMENTS -- ARGS...: run the composition verifier with the stub.
verify() {
    local name="$1"
    shift
    local assignments=()
    while [[ "$1" != -- ]]; do assignments+=("$1"); shift; done
    shift
    calls="$work/$name.calls"
    rm -f "$calls" "$calls.seen"
    : > "$calls"
    status=0
    env PATH="$work/bin:$PATH" DOCKER_CALLS="$calls" ID_A="$id_a" ID_B="$id_b" \
        OTHER_DIGEST="$other_digest" STUB_LABEL="$digest" STUB_IMAGE_COMMIT="$commit" \
        "${assignments[@]+"${assignments[@]}"}" "$verifier" "$@" \
        > "$work/$name.out" 2> "$work/$name.err" || status=$?
}

expect() {
    local label="$1" wanted="$2" name="$3"
    if [[ "$status" == "$wanted" ]]; then
        pass "$label"
    else
        fail "$label: expected $wanted, received $status"
        sed 's/^/    /' "$work/$name.err" 2> /dev/null | head -3
    fi
}

verify match -- --expected "$expected" --image example/core:1
expect 'a matching label proves the composition' 0 match
if [[ "$(cat "$work/match.out" 2> /dev/null)" == "$id_a" ]] \
        && [[ "$(grep -c '^image inspect' "$work/match.calls")" == 1 ]]; then
    pass 'the verifier inspects once and prints the immutable image ID'
else
    fail 'the verifier inspects once and prints the immutable image ID'
fi

# The source stamp is correct, and the composition label differs.
mkdir -p "$work/jar/BOOT-INF/classes"
printf 'git.commit.id=%s\n' "$commit" > "$work/jar/BOOT-INF/classes/git.properties"
(cd "$work/jar" && zip -q -r "$work/boot.jar" BOOT-INF)
source_status=0
env PATH="$work/bin:$PATH" DOCKER_CALLS="$work/source.calls" STUB_IMAGE_COMMIT="$commit" \
    "$source_verifier" --boot-jar "$work/boot.jar" --commit "$commit" --image "$id_a" \
    > /dev/null 2>&1 || source_status=$?
verify other-label STUB_LABEL="$other_digest" -- --expected "$expected" --image example/core:1
if (( source_status == 0 && status == 1 )) && [[ ! -s "$work/other-label.out" ]]; then
    pass 'a correct source stamp does not hide a different composition label'
else
    fail "a correct source stamp does not hide a different composition label: source $source_status, composition $status"
fi

verify missing STUB_LABEL=none -- --expected "$expected" --image example/core:1
expect 'a missing composition label fails with status 1' 1 missing
verify docker-fail STUB_INSPECT_STATUS=1 -- --expected "$expected" --image example/core:1
expect 'a Docker failure with status 1 stays status 1' 1 docker-fail
verify docker-125 STUB_INSPECT_STATUS=125 -- --expected "$expected" --image example/core:1
expect 'another Docker failure gives status 1' 1 docker-125
verify bad-inspect STUB_INSPECT_BODY='not json' -- --expected "$expected" --image example/core:1
expect 'an inspection without JSON fails with status 1' 1 bad-inspect
verify no-id STUB_INSPECT_BODY='{"Id":"latest","Config":{"Labels":{}}}' -- \
    --expected "$expected" --image example/core:1
expect 'an inspection without an immutable ID fails with status 1' 1 no-id

# Retagging: the reference moves to image B after the first inspection.
verify retag STUB_RETAG=1 -- --expected "$expected" --image example/core:1
if (( status == 0 )) && [[ "$(cat "$work/retag.out")" == "$id_a" ]] \
        && [[ "$(grep -c 'example/core:1' "$work/retag.calls")" == 1 ]]; then
    pass 'a retag after the inspection leaves the result bound to the first image ID'
else
    fail "a retag after the inspection leaves the result bound to the first image ID: status $status"
fi
# A later source check reads the printed ID, so it reads image A and not image B.
retag_id=$(cat "$work/retag.out" 2> /dev/null)
rm -f "$work/retag-source.calls"
env PATH="$work/bin:$PATH" DOCKER_CALLS="$work/retag-source.calls" STUB_IMAGE_COMMIT="$commit" \
    "$source_verifier" --boot-jar "$work/boot.jar" --commit "$commit" --image "$retag_id" \
    > /dev/null 2>&1
if [[ -n "$retag_id" ]] && grep -qx "create $id_a" "$work/retag-source.calls" 2> /dev/null; then
    pass 'the later source check reads the first image ID'
else
    fail 'the later source check reads the first image ID'
fi
# Image B itself fails the proof, so the binding matters.
verify image-b -- --expected "$expected" --image "$id_b"
expect 'the retagged image fails the proof' 1 image-b

# The digest covers the record values, not the file layout.
jq -S --indent 4 . "$expected" > "$work/pretty.json"
verify pretty -- --expected "$work/pretty.json" --image example/core:1
expect 'another layout of the same record proves the composition' 0 pretty

# Python writes the canonical form of chat-build. The verifier writes it with jq.
# Both must give one digest for non-ASCII values and escaped characters.
jq -c '.jvmOptions += ["-Dapp.note=café 😀 \\ \"q\" /"]' "$expected" > "$work/unicode.json"
python_digest=$(python3 -I -c 'import hashlib, json, sys
record = json.load(open(sys.argv[1], encoding="utf-8"))
text = json.dumps(record, sort_keys=True, separators=(",", ":"), ensure_ascii=False)
print(hashlib.sha256(text.encode("utf-8")).hexdigest())' "$work/unicode.json")
verify unicode STUB_LABEL="$python_digest" -- --expected "$work/unicode.json" --image example/core:1
expect 'the jq canonical form gives the digest of the Python canonical form' 0 unicode

# A changed expected value gives a different digest.
jq -c '.jvmOptions[8] = "-Dapp.nodeid=8"' "$expected" > "$work/node8.json"
verify node8 -- --expected "$work/node8.json" --image example/core:1
expect 'a changed expected record fails with status 1' 1 node8

# Invalid input gives status 2.
invalid() {
    local label="$1" content="$2"
    printf '%s' "$content" > "$work/invalid.json"
    verify invalid -- --expected "$work/invalid.json" --image example/core:1
    if (( status == 2 )) && ! grep -q '^image inspect' "$work/invalid.calls"; then
        pass "$label"
    else
        fail "$label: expected 2 before inspection, received $status"
    fi
}
invalid 'malformed JSON gives status 2' '{"version":1,'
invalid 'a JSON array gives status 2' '[]'
invalid 'an unsupported version gives status 2' "$(jq -c '.version = 2' "$expected")"
invalid 'a version written as 1.0 gives status 2' "$(jq -c '.version = 1' "$expected" | sed 's/"version":1/"version":1.0/')"
invalid 'a string version gives status 2' "$(jq -c '.version = "1"' "$expected")"
invalid 'a missing field gives status 2' "$(jq -c 'del(.module)' "$expected")"
invalid 'an extra field gives status 2' "$(jq -c '.digest = "x"' "$expected")"
invalid 'a non-string array item gives status 2' "$(jq -c '.mavenProfiles = [1]' "$expected")"
invalid 'a string in place of an array gives status 2' "$(jq -c '.springProfiles = "x"' "$expected")"
invalid 'a control character gives status 2' "$(jq -c '.service = "co\u0001re"' "$expected")"
invalid 'a DEL character gives status 2' "$(jq -c '.service = "co\u007fre"' "$expected")"
printf '{"version":1,"service":"\xff"}' > "$work/latin.json"
verify latin -- --expected "$work/latin.json" --image example/core:1
expect 'invalid UTF-8 gives status 2' 2 latin
verify absent -- --expected "$work/absent.json" --image example/core:1
expect 'an absent expected file gives status 2' 2 absent
verify no-image -- --expected "$expected"
expect 'a missing image argument gives status 2' 2 no-image
verify no-args --
expect 'no arguments give status 2' 2 no-args
verify unknown -- --expected "$expected" --image example/core:1 --extra
expect 'an unknown argument gives status 2' 2 unknown

printf '%s checks, %s failures\n' "$checks" "$failures"
(( failures == 0 ))
