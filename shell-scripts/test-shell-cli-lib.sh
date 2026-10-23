#!/usr/bin/env bash
# Offline checks for the installed shell CLI runtime. No JVM starts, and no
# container starts. See CHAT-bjqzvsoe and the plan at
# docs/superpowers/plans/2026-10-22-installed-shell-cli-runtime.md.
#
# A java stub records its own arguments, so each case reads the command line
# that the launcher builds. The suite needs no free port.
set -uo pipefail

cd "$(dirname "$0")/.."
repo_root="$PWD"
launcher="$repo_root/shell-scripts/chat-shell-cli"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

failures=0
ok() { printf 'ok   %s\n' "$1"; }
bad() { printf 'FAIL %s: %s\n' "$1" "$2"; failures=$((failures + 1)); }

# The development identities. No shipped file may name one of them. The suite
# is a fixture, so it may.
dev_names='Sigma|Theta|Phi|Mario'

stub_bin="$work/bin"
mkdir -p "$stub_bin"

# A java stub. It writes its arguments, one on each line, and its own count.
cat > "$stub_bin/java" <<'STUB'
#!/usr/bin/env bash
set -uo pipefail
printf '%s\n' "$@" > "${JAVA_ARGS:?Set JAVA_ARGS}"
{
    printf 'CHAT_SHELL_USER=%s\n' "${CHAT_SHELL_USER:-}"
    printf 'CHAT_SHELL_PASSWORD_FILE=%s\n' "${CHAT_SHELL_PASSWORD_FILE:-}"
} > "$JAVA_ARGS.env"
if [[ -n "${JAVA_SLEEP:-}" ]]; then sleep "$JAVA_SLEEP"; fi
exit 0
STUB

# A chat-build stub. It records every call, so a case can prove that lib mode
# calls no source-tree script.
cat > "$stub_bin/chat-build" <<'STUB'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "${CHAT_BUILD_CALLS:?Set CHAT_BUILD_CALLS}"
printf '  -Dstub.plan=1\n'
exit 0
STUB
chmod +x "$stub_bin/java" "$stub_bin/chat-build"

# make_lib DIR  Build one fake installed lib directory.
make_lib() {
    local dir="$1"
    mkdir -p "$dir"
    : > "$dir/one.jar"
    {
        printf '# key-type uuid\n'
        printf '# node-id 9\n'
        printf -- '-Dchat.one=1\n'
        printf -- '-Dchat.two=2\n'
    } > "$dir/shell-cli.options"
}

lib="$work/lib"
make_lib "$lib"

# run_lib [extra env] -- ARGS...  Run the launcher in lib mode.
run_lib() {
    local args_file="$work/java-args"
    rm -f "$args_file" "$args_file.env"
    out="$(env PATH="$stub_bin:$PATH" JAVA_ARGS="$args_file" \
        CHAT_BUILD_CALLS="$work/chat-build-calls" \
        CHAT_SHELL_LIB="$lib" CHAT_SHELL_KEY_TYPE=uuid CHAT_SHELL_NODE_ID=9 \
        "$@" "$launcher" whoami 2>&1)"
    status=$?
}

# -- Lib mode ------------------------------------------------------------

rm -f "$work/chat-build-calls"
run_lib
cp_line="$(grep -A 1 -x -- '-cp' "$work/java-args" 2> /dev/null | tail -1)"
if [[ $status -eq 0 && "$cp_line" == "$lib/*" ]]; then
    ok 'lib_mode_uses_the_lib_classpath'
else
    bad 'lib_mode_uses_the_lib_classpath' \
        "exit $status, classpath: $cp_line, output: $out"
fi

option_case='ok'
for option in '-Dchat.one=1' '-Dchat.two=2'; do
    grep -qx -- "$option" "$work/java-args" 2> /dev/null \
        || option_case="missing: $option"
done
if grep -qx -- '# key-type uuid' "$work/java-args" 2> /dev/null; then
    option_case='one header line reached the command line'
fi
if [[ "$option_case" == 'ok' ]]; then
    ok 'lib_mode_uses_the_frozen_options'
else
    bad 'lib_mode_uses_the_frozen_options' "$option_case"
fi

if [[ ! -s "$work/chat-build-calls" ]]; then
    ok 'lib_mode_calls_no_chat_build'
else
    bad 'lib_mode_calls_no_chat_build' \
        "calls: $(cat "$work/chat-build-calls")"
fi

run_lib CHAT_SHELL_KEY_TYPE=long
if [[ $status -eq 2 && "$out" == *'uuid'* ]]; then
    ok 'lib_mode_refuses_another_key_type'
else
    bad 'lib_mode_refuses_another_key_type' "exit $status, output: $out"
fi

run_lib CHAT_SHELL_NODE_ID=1
if [[ $status -eq 2 && "$out" == *'9'* ]]; then
    ok 'lib_mode_refuses_another_node_id'
else
    bad 'lib_mode_refuses_another_node_id' "exit $status, output: $out"
fi

# A caller that sets no key type and no node id takes the frozen pair. The
# acceptance gate of Task 4 runs that shape.
rm -f "$work/java-args"
out="$(env PATH="$stub_bin:$PATH" JAVA_ARGS="$work/java-args" \
    CHAT_BUILD_CALLS="$work/chat-build-calls" CHAT_SHELL_LIB="$lib" \
    "$launcher" whoami 2>&1)"
status=$?
cp_line="$(grep -A 1 -x -- '-cp' "$work/java-args" 2> /dev/null | tail -1)"
if [[ $status -eq 0 && "$cp_line" == "$lib/*" ]]; then
    ok 'lib_mode_defaults_to_the_frozen_pair'
else
    bad 'lib_mode_defaults_to_the_frozen_pair' "exit $status, output: $out"
fi

# The login is a run-time pair. Two callers reach two logins, and neither
# run carries the login of the other.
printf 'alpha-secret\n' > "$work/alpha.password"
printf 'beta-secret\n' > "$work/beta.password"
identity_case='ok'
run_lib CHAT_SHELL_USER=alpha CHAT_SHELL_PASSWORD_FILE="$work/alpha.password"
alpha_env="$(cat "$work/java-args.env" 2> /dev/null)"
run_lib CHAT_SHELL_USER=beta CHAT_SHELL_PASSWORD_FILE="$work/beta.password"
beta_env="$(cat "$work/java-args.env" 2> /dev/null)"
if [[ "$alpha_env" != *'CHAT_SHELL_USER=alpha'* ]]; then
    identity_case='the first run carries no alpha identity'
elif [[ "$alpha_env" != *"$work/alpha.password"* ]]; then
    identity_case='the first run carries no alpha password file'
elif [[ "$alpha_env" == *'beta'* ]]; then
    identity_case='the first run carries the beta login'
elif [[ "$beta_env" != *'CHAT_SHELL_USER=beta'* ]]; then
    identity_case='the second run carries no beta identity'
elif [[ "$beta_env" != *"$work/beta.password"* ]]; then
    identity_case='the second run carries no beta password file'
elif [[ "$beta_env" == *'alpha'* ]]; then
    identity_case='the second run carries the alpha login'
fi
if [[ "$identity_case" == 'ok' ]]; then
    ok 'lib_mode_passes_two_identities'
else
    bad 'lib_mode_passes_two_identities' "$identity_case"
fi

# No shipped file of this runtime names a development identity.
identity_leak='ok'
if grep -qE "$dev_names" "$launcher"; then
    identity_leak="the launcher names $(grep -oE "$dev_names" "$launcher" | sort -u | tr '\n' ' ')"
elif grep -qE "$dev_names" "$lib/shell-cli.options"; then
    identity_leak='the frozen options name a development identity'
fi
if [[ "$identity_leak" == 'ok' ]]; then
    ok 'lib_mode_holds_no_fixed_identity'
else
    bad 'lib_mode_holds_no_fixed_identity' "$identity_leak"
fi

# -- Source mode ---------------------------------------------------------

rm -f "$work/chat-build-calls"
rm -f "$work/java-args"
mkdir -p "$work/fake-classpath"
printf '/tmp/fake-one.jar\n' > "$work/fake-classpath/shell-cli.classpath"
out="$(env PATH="$stub_bin:$PATH" JAVA_ARGS="$work/java-args" \
    CHAT_BUILD_CALLS="$work/chat-build-calls" \
    CHAT_SHELL_KEY_TYPE=uuid CHAT_SHELL_NODE_ID=9 \
    CHAT_SHELL_CLASSPATH_FILE="$work/fake-classpath/shell-cli.classpath" \
    "$launcher" whoami 2>&1)"
status=$?
cp_line="$(grep -A 1 -x -- '-cp' "$work/java-args" 2> /dev/null | tail -1)"
if [[ $status -eq 0 && "$cp_line" == *'chat-deploy/target/classes'* ]]; then
    ok 'source_mode_keeps_the_old_classpath'
else
    bad 'source_mode_keeps_the_old_classpath' \
        "exit $status, classpath: $cp_line, output: $out"
fi

# -- The read limit ------------------------------------------------------

# register-read holds a 30000 ms limit. A short budget and a slow java give
# status 124 and one line on standard error.
rm -f "$work/java-args"
err_file="$work/limit.err"
out="$(env PATH="$stub_bin:$PATH" JAVA_ARGS="$work/java-args" \
    JAVA_SLEEP=5 CHAT_BUILD_CALLS="$work/chat-build-calls" \
    CHAT_SHELL_LIB="$lib" CHAT_SHELL_KEY_TYPE=uuid CHAT_SHELL_NODE_ID=9 \
    CHAT_SHELL_LIMIT_MS=1000 \
    "$launcher" register-read 2> "$err_file")"
status=$?
err_lines="$(grep -c . "$err_file" 2> /dev/null)"
if [[ $status -eq 124 && "$err_lines" -eq 1 ]]; then
    ok 'lib_mode_keeps_the_read_limit'
else
    bad 'lib_mode_keeps_the_read_limit' \
        "exit $status, stderr lines $err_lines: $(head -2 "$err_file")"
fi

if ((failures > 0)); then
    echo "$failures case(s) failed"
    exit 1
fi
echo 'all shell CLI runtime cases passed'
