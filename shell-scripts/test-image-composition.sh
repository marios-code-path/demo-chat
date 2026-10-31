#!/usr/bin/env bash
# Assert that chat-build --composition-json prints the resolved image composition.
# The mode runs no Maven, Docker, network, or write operation.
# See docs/IMAGE-VERIFICATION.md and fp issue CHAT-gyfhbsbd.
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
. "$here/test-support/clear-legacy-inputs.sh" || exit $?
. "$here/test-support/clear-composition-inputs.sh" || exit $?

builder="$here/chat-build"
fixtures="$here/fixtures/image-composition"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
checks=0
failures=0

# Each external command records its call and fails. A record proves a forbidden call.
mkdir -p "$work/bin" "$work/home" "$work/tmp" "$work/cwd"
for tool in mvn docker curl wget; do
    printf '#!/usr/bin/env bash\necho "%s $*" >> "%s/calls"\nexit 97\n' \
        "$tool" "$work" > "$work/bin/$tool"
    chmod +x "$work/bin/$tool"
done
export PATH="$work/bin:$PATH" HOME="$work/home" TMPDIR="$work/tmp"
export CONSUL_HOST=10.0.0.5

pass() { checks=$((checks + 1)); printf 'ok %s\n' "$1"; }
fail() { checks=$((checks + 1)); failures=$((failures + 1)); printf 'FAIL %s\n' "$1"; }

# compose NAME ARGS... writes NAME.json and NAME.err, and returns the builder status.
compose() {
    local name="$1"
    shift
    (cd "$work/cwd" && "$builder" "$@") > "$work/$name.json" 2> "$work/$name.err"
}

plan_case=(core --cassandra --index lucene --pubsub redis-pubsub --notls --key-type uuid --consul)

# The two-composition case of the plan.
if compose a "${plan_case[@]}" --node-id 7 --composition-json \
        && compose b "${plan_case[@]}" --node-id 8 --composition-json \
        && [[ "$(jq -r '.version' "$work/a.json")" == 1 ]] \
        && [[ "$(jq -r '.service' "$work/a.json")" == core ]] \
        && ! cmp -s "$work/a.json" "$work/b.json"; then
    pass 'node IDs 7 and 8 give two records'
else
    fail 'node IDs 7 and 8 give two records'
    sed 's/^/    /' "$work/a.err" "$work/b.err"
fi

if cmp -s "$work/a.json" "$fixtures/expected.json"; then
    pass 'the record matches the expected fixture byte for byte'
else
    fail 'the record matches the expected fixture byte for byte'
fi

schema='keys == ["jvmOptions","mavenProfiles","module","service","springProfiles","version"]
    and (.version | type == "number" and . == 1)
    and (.service | type == "string") and (.module | type == "string")
    and all(.mavenProfiles, .springProfiles, .jvmOptions; type == "array" and all(.[]; type == "string"))'
if jq -e "$schema" "$work/a.json" > /dev/null 2>&1; then
    pass 'the record has the exact version 1 schema'
else
    fail 'the record has the exact version 1 schema'
fi

# Canonical form: sorted keys, compact separators, one final LF.
if [[ -s "$work/a.json" ]] && jq -cS . "$work/a.json" | cmp -s - "$work/a.json" \
        && [[ $(wc -l < "$work/a.json") -eq 1 ]]; then
    pass 'the record is canonical JSON with one final LF'
else
    fail 'the record is canonical JSON with one final LF'
fi

compose a2 "${plan_case[@]}" --node-id 7 --composition-json
if [[ -s "$work/a.json" ]] && cmp -s "$work/a.json" "$work/a2.json"; then
    pass 'a repeated run gives the same bytes'
else
    fail 'a repeated run gives the same bytes'
fi

# Equivalent accepted flag forms give one record.
compose e1 core --consul --uuid --pubsub=redis-pubsub --index=lucene --cassandra \
    --node-id=007 --notls --composition-json
compose e2 core --composition-json --node-id 7 --notls --cassandra --consul \
    --pubsub redis-pubsub --index lucene --key-type uuid
if [[ -s "$work/a.json" ]] && cmp -s "$work/a.json" "$work/e1.json" \
        && cmp -s "$work/a.json" "$work/e2.json"; then
    pass 'equivalent flag forms give one record'
else
    fail 'equivalent flag forms give one record'
fi

# differs LABEL ENV-ASSIGNMENTS -- ARGS...: the changed input must change the record.
differs() {
    local label="$1" name="c$checks"
    shift
    local assignments=()
    while [[ "$1" != -- ]]; do assignments+=("$1"); shift; done
    shift
    if (cd "$work/cwd" && env "${assignments[@]+"${assignments[@]}"}" "$builder" "$@") \
            > "$work/$name.json" 2> "$work/$name.err" \
            && jq -e "$schema" "$work/$name.json" > /dev/null 2>&1 \
            && ! cmp -s "$work/a.json" "$work/$name.json"; then
        pass "$label"
    else
        fail "$label"
        sed 's/^/    /' "$work/$name.err"
    fi
}
base_args=(--notls --node-id 7 --key-type uuid --consul --composition-json)
differs 'a changed backend changes the record' -- core --memory "${base_args[@]}"
differs 'a changed index changes the record' -- \
    core --cassandra --index cassandra --pubsub redis-pubsub "${base_args[@]}"
differs 'a changed pubsub changes the record' -- \
    core --cassandra --index lucene --pubsub memory "${base_args[@]}"
differs 'a changed key type changes the record' -- \
    core --cassandra --index lucene --pubsub redis-pubsub --notls --node-id 7 \
    --key-type long --consul --composition-json
differs 'an added vector and embedding change the record' -- \
    "${plan_case[@]}" --node-id 7 --vector embedded --embedding openai --composition-json
differs 'local discovery changes the record' -- \
    core --cassandra --index lucene --pubsub redis-pubsub --notls --node-id 7 \
    --key-type uuid --composition-json
differs 'a changed registry host changes the record' CONSUL_HOST=10.0.0.6 -- \
    "${plan_case[@]}" --node-id 7 --composition-json
differs 'a changed core port changes the record' CORE_PORT=7790 -- \
    "${plan_case[@]}" --node-id 7 --composition-json

# A profile gives the same record as the explicit flags it replaces.
printf 'NODE_PROFILE_VERSION=1\nNODE_ID=7\nNODE_KEY_TYPE=uuid\n' > "$work/profile.env"
if (cd "$work/cwd" && CHAT_NODE_PROFILE="$work/profile.env" "$builder" core --cassandra \
        --index lucene --pubsub redis-pubsub --notls --consul --composition-json) \
        > "$work/p.json" 2> "$work/p.err" \
        && [[ -s "$work/a.json" ]] && cmp -s "$work/a.json" "$work/p.json"; then
    pass 'profile inputs give the record of the explicit flags'
else
    fail 'profile inputs give the record of the explicit flags'
    sed 's/^/    /' "$work/p.err"
fi
printf 'NODE_PROFILE_VERSION=1\nNODE_ID=8\nNODE_KEY_TYPE=uuid\n' > "$work/profile8.env"
differs 'a changed profile node ID changes the record' CHAT_NODE_PROFILE="$work/profile8.env" -- \
    core --cassandra --index lucene --pubsub redis-pubsub --notls --consul --composition-json

# Credentials do not enter the record or its diagnostics.
mkdir -p "$work/keys"
if (cd "$work/cwd" && KEYSTORE_PASS=sentinel-keystore-secret "$builder" core --cassandra \
        --index lucene --pubsub redis-pubsub --tls "$work/keys" --node-id 7 --key-type uuid \
        --consul --composition-json) > "$work/t.json" 2> "$work/t.err" \
        && jq -e "$schema" "$work/t.json" > /dev/null 2>&1 \
        && ! grep -q sentinel-keystore-secret "$work/t.json" "$work/t.err" \
        && ! grep -q 'password=actuator' "$work/t.json" \
        && ! grep -q 'Authorization: Basic' "$work/t.json"; then
    pass 'credentials stay out of the record and its diagnostics'
else
    fail 'credentials stay out of the record and its diagnostics'
fi

# A URI with user information or a secret query parameter keeps its shape alone.
if (cd "$work/cwd" && ROOTKEY_SOURCE_URI='http://admin:s3cret@core:6791/rootkeys?token=t0ken&x=1;password=pw9' \
        "$builder" rest --notls --node-id 1 --key-type uuid --composition-json) \
        > "$work/uri.json" 2> "$work/uri.err" \
        && jq -e "$schema" "$work/uri.json" > /dev/null 2>&1 \
        && ! grep -qE 'admin|s3cret|t0ken|pw9' "$work/uri.json" "$work/uri.err" \
        && grep -qF 'http://<redacted>@core:6791/rootkeys?token=<redacted>&x=1;password=<redacted>' "$work/uri.json"; then
    pass 'URI user information and secret query values stay out of the record'
else
    fail 'URI user information and secret query values stay out of the record'
    grep -o 'rootkeys.consume.source=[^"]*' "$work/uri.json" 2> /dev/null | sed 's/^/    /'
fi
if (cd "$work/cwd" && ROOTKEY_SOURCE_URI='http://core:6791/rootkeys?x=1' "$builder" rest --notls \
        --node-id 1 --key-type uuid --composition-json) 2> /dev/null \
        | grep -qF -- '-Dapp.rootkeys.consume.source=http://core:6791/rootkeys?x=1"'; then
    pass 'a URI without credentials stays as it is'
else
    fail 'a URI without credentials stays as it is'
fi

# expect_status LABEL STATUS ARGS...: the mode keeps each refusal status.
expect_status() {
    local label="$1" expected="$2" status=0
    shift 2
    (cd "$work/cwd" && "$builder" "$@") > "$work/s.out" 2> "$work/s.err" || status=$?
    if [[ "$status" == "$expected" && ! -s "$work/s.out" ]]; then
        pass "$label"
    else
        fail "$label: expected $expected, received $status"
    fi
}
: > "$work/key.jwk"
expect_status 'index without Cassandra keeps status 1' 1 \
    core --memory --index lucene --node-id 0 --key-type uuid --notls --composition-json
expect_status 'vector pairing keeps status 2' 2 \
    core --cassandra --vector embedded --node-id 0 --key-type uuid --notls --composition-json
expect_status 'a JWK bake refusal keeps status 1' 1 \
    authserv --jwk "$work/key.jwk" --node-id 0 --key-type uuid --notls --composition-json
expect_status 'an agent bake refusal keeps status 1' 1 \
    authserv --agent a=Agent --node-id 0 --key-type uuid --notls --composition-json
expect_status 'an index-root bake refusal keeps status 1' 1 \
    core --memory --index-root "$work/index" --node-id 0 --key-type uuid --notls --composition-json
expect_status 'a missing key type keeps status 2' 2 \
    core --memory --node-id 0 --notls --composition-json
expect_status 'a missing TLS choice keeps status 1' 1 \
    core --memory --node-id 0 --key-type uuid --composition-json
expect_status 'a build goal beside the mode is refused with status 2' 2 \
    core --memory --node-id 0 --key-type uuid --notls --build --composition-json
expect_status 'a dry run beside the mode is refused with status 2' 2 \
    core --memory --node-id 0 --key-type uuid --notls --dry-run --composition-json
unset CONSUL_HOST
expect_status 'Consul discovery without a host is refused with status 2' 2 \
    core --memory --consul --node-id 0 --key-type uuid --notls --composition-json
if grep -q 'needs CONSUL_HOST or --registry' "$work/s.err"; then
    pass 'the Consul refusal names the missing host input'
else
    fail 'the Consul refusal names the missing host input'
fi
export CONSUL_HOST=10.0.0.5
CHAT_NODE_PROFILE="$work/absent" expect_status 'an absent profile keeps status 2' 2 \
    core --memory --node-id 0 --key-type uuid --notls --composition-json

# The mode wrote nothing and called no external command.
compose w "${plan_case[@]}" --node-id 7 --env-file "$work/env-file" \
    --image-manifest "$work/manifest.json" --composition-json
if [[ ! -e "$work/calls" && ! -e "$work/env-file" && ! -e "$work/manifest.json" ]] \
        && [[ -z "$(find "$work/home" "$work/tmp" "$work/cwd" -mindepth 1 -print -quit)" ]]; then
    pass 'the mode calls no external command and writes no file'
else
    fail 'the mode calls no external command and writes no file'
    [[ ! -e "$work/calls" ]] || sed 's/^/    /' "$work/calls"
fi

# -- Task C2: the build passes its own composition digest to Maven ----------
# This Maven stub records the label input of the build and reports success.
mkdir -p "$work/capture-bin"
cat > "$work/capture-bin/mvn" <<'STUB'
#!/usr/bin/env bash
if [[ -n "${CHAT_IMAGE_COMPOSITION_LABELS+set}" ]]; then
    printf '%s' "$CHAT_IMAGE_COMPOSITION_LABELS" > "$CAPTURE"
else
    printf '<unset>' > "$CAPTURE"
fi
# The options that reach Maven. An unset variable leaves the POM expression
# ${env.JAVA_TOOL_OPTIONS} unresolved, and the image receives that literal.
if [[ -n "${JAVA_TOOL_OPTIONS+set}" ]]; then
    printf 'set:%s' "$JAVA_TOOL_OPTIONS" > "$CAPTURE.options"
else
    printf '<unset>' > "$CAPTURE.options"
fi
STUB
chmod +x "$work/capture-bin/mvn"
reserved=com.demo.chat.composition.sha256
digest=$(printf '%s' "$(cat "$fixtures/expected.json")" | shasum -a 256 | cut -d' ' -f1)

# capture NAME ENV-ASSIGNMENTS -- ARGS...: build with the capture stub.
capture() {
    local name="$1"
    shift
    local assignments=()
    while [[ "$1" != -- ]]; do assignments+=("$1"); shift; done
    shift
    rm -f "$work/$name.labels" "$work/$name.labels.options"
    capture_status=0
    (cd "$work/cwd" && env PATH="$work/capture-bin:$PATH" CAPTURE="$work/$name.labels" \
        "${assignments[@]+"${assignments[@]}"}" "$builder" "$@" \
        --env-file "$work/$name.env") > "$work/$name.out" 2> "$work/$name.err" \
        || capture_status=$?
}

capture g -- "${plan_case[@]}" --node-id 7 --build
if (( capture_status == 0 )) && [[ "$(cat "$work/g.labels" 2> /dev/null)" == "$reserved=$digest" ]]; then
    pass 'a build passes the digest of its own record as the reserved label'
else
    fail 'a build passes the digest of its own record as the reserved label'
    printf '    status %s, labels %s\n' "$capture_status" "$(cat "$work/g.labels" 2> /dev/null)"
fi

capture f "CHAT_IMAGE_COMPOSITION_LABELS=$reserved=forged" -- "${plan_case[@]}" --node-id 7 --build
if (( capture_status == 2 )) && [[ ! -e "$work/f.labels" ]] && grep -q "$reserved" "$work/f.err"; then
    pass 'a caller label with the reserved key is refused before Maven runs'
else
    fail 'a caller label with the reserved key is refused before Maven runs'
    printf '    status %s, labels %s\n' "$capture_status" "$(cat "$work/f.labels" 2> /dev/null)"
fi

# decoded NAME: the labels of NAME as the pinned image-labels parser reads them.
# The helper copies the v4.14.4 Go parser. See docs/IMAGE-VERIFICATION.md.
decoded() {
    python3 -I "$here/test-support/image-labels-parse.py" "$(cat "$work/$1.labels" 2> /dev/null)" | jq -cS .
}
if [[ "$(decoded g)" == "$(jq -cnS --arg k "$reserved" --arg d "$digest" '{labels: {($k): $d}}')" ]]; then
    pass 'the buildpack parser reads the reserved label of a build'
else
    fail 'the buildpack parser reads the reserved label of a build'
fi

capture u "CHAT_IMAGE_COMPOSITION_LABELS=org.example.team=chat 'org.example.note=two words'" -- \
    "${plan_case[@]}" --node-id 7 --build
want=$(jq -cnS --arg k "$reserved" --arg d "$digest" \
    '{labels: {($k): $d, "org.example.team": "chat", "org.example.note": "two words"}}')
if (( capture_status == 0 )) && [[ "$(decoded u)" == "$want" ]]; then
    pass 'unrelated caller labels with a space survive the buildpack parser'
else
    fail 'unrelated caller labels with a space survive the buildpack parser'
    printf '    status %s, labels %s, parsed %s\n' "$capture_status" \
        "$(cat "$work/u.labels" 2> /dev/null)" "$(decoded u)"
fi

# Both quote characters in values. The parser ends a quoted value at either one.
capture q "CHAT_IMAGE_COMPOSITION_LABELS=org.example.q=\"it's \\\"x\\\"\" org.example.s='say \"hi\"' org.example.e=b=c" -- \
    "${plan_case[@]}" --node-id 7 --build
want=$(jq -cnS --arg k "$reserved" --arg d "$digest" \
    '{labels: {($k): $d, "org.example.q": "it'"'"'s \"x\"", "org.example.s": "say \"hi\"", "org.example.e": "b=c"}}')
if (( capture_status == 0 )) && [[ "$(decoded q)" == "$want" ]]; then
    pass 'values with both quote characters and an equals sign survive the buildpack parser'
else
    fail 'values with both quote characters and an equals sign survive the buildpack parser'
    printf '    status %s, labels %s, parsed %s\n' "$capture_status" \
        "$(cat "$work/q.labels" 2> /dev/null)" "$(decoded q)"
fi

# refused NAME LABELS LABEL: the builder refuses LABELS with status 2 before Maven.
refused() {
    capture "$1" "CHAT_IMAGE_COMPOSITION_LABELS=$2" -- "${plan_case[@]}" --node-id 7 --build
    if (( capture_status == 2 )) && [[ ! -e "$work/$1.labels" ]]; then
        pass "$3"
    else
        fail "$3: status $capture_status, labels $(cat "$work/$1.labels" 2> /dev/null)"
    fi
}
refused ks "'org.example.a b=c'" 'a caller key with a space is refused with status 2'
refused kd 'org.example.\"a=c' 'a caller key with a double quote is refused with status 2'
refused kq "org.example.\\'a=c" 'a caller key with a single quote is refused with status 2'
refused vb "org.example.a='x\\y'" 'a caller value with a backslash is refused with status 2'

capture m "CHAT_IMAGE_COMPOSITION_LABELS=no-value" -- "${plan_case[@]}" --node-id 7 --build
if (( capture_status == 2 )) && [[ ! -e "$work/m.labels" ]]; then
    pass 'a malformed caller label is refused before Maven runs'
else
    fail 'a malformed caller label is refused before Maven runs'
fi

capture r "CHAT_IMAGE_COMPOSITION_LABELS=org.example.team=chat" -- "${plan_case[@]}" --node-id 7 --run
if (( capture_status == 0 )) && [[ "$(cat "$work/r.labels" 2> /dev/null)" == '<unset>' ]]; then
    pass 'a run passes no image label input to Maven'
else
    fail 'a run passes no image label input to Maven'
    printf '    status %s, labels %s\n' "$capture_status" "$(cat "$work/r.labels" 2> /dev/null)"
fi

capture n CONSUL_HOST=10.0.0.6 -- "${plan_case[@]}" --node-id 7 --build
if (( capture_status == 0 )) && [[ "$(cat "$work/n.labels" 2> /dev/null)" == "$reserved="* ]] \
        && [[ "$(cat "$work/n.labels")" != "$reserved=$digest" ]]; then
    pass 'a changed composition changes the reserved label'
else
    fail 'a changed composition changes the reserved label'
fi

# -- --bake: the image receives no options, so the record holds none --------
compose k "${plan_case[@]}" --node-id 7 --bake --composition-json
if [[ -s "$work/a.json" ]] && jq -e '.jvmOptions == []' "$work/k.json" > /dev/null 2>&1 \
        && [[ "$(jq -cS 'del(.jvmOptions)' "$work/k.json")" == "$(jq -cS 'del(.jvmOptions)' "$work/a.json")" ]]; then
    pass 'a baked composition record holds no JVM options and keeps the other fields'
else
    fail 'a baked composition record holds no JVM options and keeps the other fields'
fi
bake_digest=$(printf '%s' "$(jq -cS '.jvmOptions = []' "$fixtures/expected.json")" | shasum -a 256 | cut -d' ' -f1)
capture kb -- "${plan_case[@]}" --node-id 7 --build --bake
if (( capture_status == 0 )) && [[ "$(cat "$work/kb.labels" 2> /dev/null)" == "$reserved=$bake_digest" ]]; then
    pass 'a baked build labels the record of the options that the image receives'
else
    fail 'a baked build labels the record of the options that the image receives'
    printf '    status %s, labels %s\n' "$capture_status" "$(cat "$work/kb.labels" 2> /dev/null)"
fi
if [[ "$(cat "$work/kb.labels.options" 2> /dev/null)" == 'set:' ]]; then
    pass 'a baked build passes JAVA_TOOL_OPTIONS to Maven as an empty value'
else
    fail "a baked build passes JAVA_TOOL_OPTIONS to Maven as an empty value: $(cat "$work/kb.labels.options" 2> /dev/null)"
fi
if [[ "$(cat "$work/g.labels.options" 2> /dev/null)" == set:--enable-native-access=* ]]; then
    pass 'an inherited build passes its options to Maven'
else
    fail 'an inherited build passes its options to Maven'
fi
if [[ -s "$work/kb.labels" && "$(cat "$work/kb.labels")" != "$(cat "$work/g.labels" 2> /dev/null)" ]]; then
    pass 'a baked build and an inherited build give two labels'
else
    fail 'a baked build and an inherited build give two labels'
fi

printf '%s checks, %s failures\n' "$checks" "$failures"
(( failures == 0 ))
