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
    printf 'CORE_PORT=%s\n' "${CORE_PORT:-}"
    printf 'CORE_MGMT_PORT=%s\n' "${CORE_MGMT_PORT:-}"
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
printf '  -Dapp.rootkeys.consume.source=http://127.0.0.1:%s\n' \
    "${CORE_MGMT_PORT:-6791}"
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

if grep -qx 'CORE_PORT=6890' "$work/java-args.env" \
   && grep -qx 'CORE_MGMT_PORT=6891' "$work/java-args.env"; then
    ok 'lib_mode_defaults_to_node_9_ports'
else
    bad 'lib_mode_defaults_to_node_9_ports' "$(cat "$work/java-args.env")"
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

# -- The stage script ----------------------------------------------------

stager="$repo_root/shell-scripts/build-shell-cli-lib"

# A mvn stub. It writes two jars into the output directory of the call, and it
# records every call.
cat > "$stub_bin/mvn" <<'STUB'
#!/usr/bin/env bash
set -uo pipefail
printf '%s\n' "$*" >> "${MVN_CALLS:?Set MVN_CALLS}"
out=''
for word in "$@"; do
    case "$word" in
        -DoutputDirectory=*) out="${word#-DoutputDirectory=}" ;;
    esac
done
if [[ -n "$out" ]]; then
    mkdir -p "$out"
    # One real jar holds the main class entry, because the stage script checks
    # for it. The other is a plain file, which is enough for a count.
    stage_tmp="$(mktemp -d)"
    mkdir -p "$stage_tmp/com/demo/chat"
    : > "$stage_tmp/com/demo/chat/ShellCliKt.class"
    ( cd "$stage_tmp" && zip -q -r "$out/chat-shell-0.0.1.jar" com )
    rm -rf "$stage_tmp"
    printf 'jar one\n' > "$out/one.jar"
    printf 'jar two\n' > "$out/two.jar"
fi
if [[ -n "${MVN_ENV:-}" ]]; then
    printf 'JAVA_HOME=%s\n' "${JAVA_HOME:-}" > "$MVN_ENV"
fi
if [[ -n "${MVN_FAIL:-}" ]]; then exit 1; fi
exit 0
STUB
chmod +x "$stub_bin/mvn"

# run_stage DIR [extra env]  Run the stage script with the stubs.
run_stage() {
    local dir="$1"
    shift
    rm -f "$work/mvn-calls" "$work/mvn-env"
    out="$(env PATH="$stub_bin:$PATH" MVN_CALLS="$work/mvn-calls" \
        MVN_ENV="$work/mvn-env" \
        JAVA_ARGS="$work/stage-java-args" "$@" "$stager" "$dir" 2>&1)"
    status=$?
}

out="$(env PATH="$stub_bin:$PATH" MVN_CALLS="$work/mvn-calls" "$stager" 2>&1)"
status=$?
if [[ $status -eq 2 ]]; then
    ok 'stage_refuses_a_missing_argument'
else
    bad 'stage_refuses_a_missing_argument' "exit $status, output: $out"
fi

mkdir -p "$work/busy-stage"
printf 'keep\n' > "$work/busy-stage/keep"
run_stage "$work/busy-stage"
if [[ $status -eq 2 && "$(cat "$work/busy-stage/keep")" == 'keep' ]]; then
    ok 'stage_refuses_an_existing_stage'
else
    bad 'stage_refuses_an_existing_stage' "exit $status, output: $out"
fi

# A minimal PATH holds bash and no mvn. The script names the missing command.
out="$(env PATH=/usr/bin:/bin "$stager" "$work/no-mvn-stage" 2>&1)"
status=$?
if [[ $status -eq 2 && "$out" == *'mvn'* && ! -e "$work/no-mvn-stage" ]]; then
    ok 'stage_refuses_an_absent_mvn'
else
    bad 'stage_refuses_an_absent_mvn' "exit $status, output: $out"
fi

run_stage "$work/stage-one"
header_case='ok'
if [[ $status -ne 0 ]]; then
    header_case="exit $status, output: $out"
elif [[ "$(sed -n '1p' "$work/stage-one/shell-cli.options")" != '# key-type uuid' ]]; then
    header_case="line 1 is $(sed -n '1p' "$work/stage-one/shell-cli.options")"
elif [[ "$(sed -n '2p' "$work/stage-one/shell-cli.options")" != '# node-id 9' ]]; then
    header_case="line 2 is $(sed -n '2p' "$work/stage-one/shell-cli.options")"
elif ! grep -qx -- '-Dapp.rootkeys.consume.source=http://127.0.0.1:6891' \
    "$work/stage-one/shell-cli.options"; then
    header_case='the root key URL does not use node 9 management port'
elif [[ ! -f "$work/stage-one/one.jar" ]]; then
    header_case='the stage holds no jar'
fi
if [[ "$header_case" == 'ok' ]]; then
    ok 'stage_writes_the_options_header'
else
    bad 'stage_writes_the_options_header' "$header_case"
fi

if [[ "$out" == *'jars'* && "$out" == *'bytes'* ]]; then
    ok 'stage_names_its_jar_count'
else
    bad 'stage_names_its_jar_count' "output: $out"
fi

if [[ ! -f "$work/stage-one/shell-cli.options" ]]; then
    bad 'stage_writes_no_identity' 'the stage holds no options file'
elif grep -qE "$dev_names" "$work/stage-one/shell-cli.options"; then
    bad 'stage_writes_no_identity' \
        "the options name $(grep -oE "$dev_names" "$work/stage-one/shell-cli.options" | sort -u | tr '\n' ' ')"
else
    ok 'stage_writes_no_identity'
fi

# A fake JDK and a fake JRE. Neither one starts a JVM. The stage script reads
# the path of the named java, and it never runs it. The install does run it for
# one version line, so the java prints that line. See CHAT-bjqzvsoe.
fake_jdk="$work/fake-jdk"
fake_jre="$work/fake-jre"
mkdir -p "$fake_jdk/bin" "$fake_jre/bin"
cat > "$fake_jdk/bin/java" <<'STUB'
#!/usr/bin/env bash
printf 'openjdk version "25.0.1" 2026-01-01\n' >&2
exit 0
STUB
cp "$fake_jdk/bin/java" "$fake_jre/bin/java"
printf '#!/usr/bin/env bash\nexit 0\n' > "$fake_jdk/bin/javac"
chmod +x "$fake_jdk/bin/java" "$fake_jdk/bin/javac" "$fake_jre/bin/java"

# NODE9_JAVA must select the JDK of the build. Maven reads JAVA_HOME, so the
# stage script gives it the home of the named java.
run_stage "$work/stage-jdk" NODE9_JAVA="$fake_jdk/bin/java"
if [[ $status -ne 0 ]]; then
    bad 'stage_gives_the_named_jdk_to_maven' "exit $status, output: $out"
elif [[ "$(cat "$work/mvn-env" 2> /dev/null)" != "JAVA_HOME=$fake_jdk" ]]; then
    bad 'stage_gives_the_named_jdk_to_maven' \
        "maven saw $(cat "$work/mvn-env" 2> /dev/null), and the JDK is $fake_jdk"
else
    ok 'stage_gives_the_named_jdk_to_maven'
fi

# A java with no javac beside it is no JDK. The build keeps the JAVA_HOME of
# the caller, and the script names the limit on standard error.
run_stage "$work/stage-jre" NODE9_JAVA="$fake_jre/bin/java" \
    JAVA_HOME="$work/caller-java-home"
if [[ $status -ne 0 ]]; then
    bad 'stage_keeps_java_home_without_a_jdk' "exit $status, output: $out"
elif [[ "$(cat "$work/mvn-env" 2> /dev/null)" != "JAVA_HOME=$work/caller-java-home" ]]; then
    bad 'stage_keeps_java_home_without_a_jdk' \
        "maven saw $(cat "$work/mvn-env" 2> /dev/null)"
elif [[ "$out" != *'javac'* ]]; then
    bad 'stage_keeps_java_home_without_a_jdk' "the output names no limit: $out"
else
    ok 'stage_keeps_java_home_without_a_jdk'
fi

# A failed build leaves no stage directory.
run_stage "$work/stage-fail" MVN_FAIL=1
if [[ $status -ne 0 && ! -e "$work/stage-fail" ]]; then
    ok 'stage_leaves_nothing_after_a_failed_build'
else
    bad 'stage_leaves_nothing_after_a_failed_build' \
        "exit $status, output: $out"
fi

# -- The install ---------------------------------------------------------

installer="$repo_root/node-9/install"

# A java stub for the install check. It prints one version line, as java
# prints it, on standard error.
cat > "$stub_bin/java-version-stub" <<'STUB'
#!/usr/bin/env bash
printf 'openjdk version "%s" 2026-01-01\n' "${JAVA_FAKE_VERSION:-25.0.1}" >&2
printf 'OpenJDK Runtime Environment\n' >&2
exit 0
STUB
chmod +x "$stub_bin/java-version-stub"

install_bin="$work/install-bin"
mkdir -p "$install_bin"
cp "$stub_bin/mvn" "$install_bin/mvn"
cp "$stub_bin/java-version-stub" "$install_bin/java"

# run_install PREFIX [env assignments...] [-- installer arguments...]
run_install() {
    local prefix="$1"
    shift
    local env_pairs=()
    local args=()
    local seen_dash=false
    local word
    for word in "$@"; do
        if [[ "$word" == '--' ]]; then
            seen_dash=true
            continue
        fi
        if [[ "$seen_dash" == 'true' ]]; then
            args+=("$word")
        else
            env_pairs+=("$word")
        fi
    done
    rm -f "$work/mvn-calls"
    out="$(env PATH="$install_bin:/usr/bin:/bin" MVN_CALLS="$work/mvn-calls" \
        "${env_pairs[@]}" bash "$installer" --prefix "$prefix" \
        "${args[@]+"${args[@]}"}" 2>&1)"
    status=$?
}

# macOS always holds /usr/bin/java, so the case names an absent java instead
# of emptying the path.
run_install "$work/home-no-java" NODE9_JAVA="$work/no-such-java"
if [[ $status -eq 2 && "$out" == *'java'* \
      && ! -f "$work/home-no-java/.installed" ]]; then
    ok 'install_refuses_an_absent_java'
else
    bad 'install_refuses_an_absent_java' "exit $status, output: $out"
fi

run_install "$work/home-old-java" JAVA_FAKE_VERSION=21.0.5
if [[ $status -eq 2 && "$out" == *'21'* && "$out" == *'25'* \
      && ! -f "$work/home-old-java/.installed" ]]; then
    ok 'install_refuses_an_old_java'
else
    bad 'install_refuses_an_old_java' "exit $status, output: $out"
fi

run_install "$work/home-one"
lib_case='ok'
if [[ $status -ne 0 ]]; then
    lib_case="exit $status, output: $out"
elif [[ ! -f "$work/home-one/lib/one.jar" ]]; then
    lib_case='the home holds no lib/one.jar'
elif [[ ! -f "$work/home-one/lib/shell-cli.options" ]]; then
    lib_case='the home holds no frozen options'
elif [[ ! -x "$work/home-one/bin/chat-shell-cli" ]]; then
    lib_case='the home holds no executable shell CLI launcher'
elif [[ ! -f "$work/home-one/.installed" ]]; then
    lib_case='the home holds no marker'
fi
if [[ "$lib_case" == 'ok' ]]; then
    ok 'install_builds_the_cli_lib'
else
    bad 'install_builds_the_cli_lib' "$lib_case"
fi

printf 'stale\n' > "$work/home-one/lib/stale.jar"
printf 'keep\n' > "$work/home-one/data/marker"
printf 'keep\n' > "$work/home-one/secrets.env"
run_install "$work/home-one"
replace_case='ok'
if [[ $status -ne 0 ]]; then
    replace_case="exit $status, output: $out"
elif [[ -e "$work/home-one/lib/stale.jar" ]]; then
    replace_case='the stale jar stayed'
elif [[ "$(cat "$work/home-one/data/marker")" != 'keep' ]]; then
    replace_case='the data file changed'
elif [[ "$(cat "$work/home-one/secrets.env")" != 'keep' ]]; then
    replace_case='the secrets file changed'
fi
if [[ "$replace_case" == 'ok' ]]; then
    ok 'install_replaces_the_lib_whole'
else
    bad 'install_replaces_the_lib_whole' "$replace_case"
fi

run_install "$work/home-dry" -- --dry-run
if [[ $status -eq 0 && "$out" == *'lib:'* && "$out" == *'java:'* \
      && ! -s "$work/mvn-calls" && ! -e "$work/home-dry" ]]; then
    ok 'install_dry_run_builds_nothing'
else
    bad 'install_dry_run_builds_nothing' \
        "exit $status, mvn calls $(grep -c . "$work/mvn-calls" 2> /dev/null), output: $out"
fi

run_install "$work/home-skip" -- --skip-cli
skip_case='ok'
if [[ $status -ne 0 ]]; then
    skip_case="exit $status, output: $out"
elif [[ -e "$work/home-skip/lib" ]]; then
    skip_case='the home holds a lib directory'
elif [[ ! -f "$work/home-skip/.installed" ]]; then
    skip_case='the home holds no marker'
elif ! grep -q 'cli: skipped' "$work/home-skip/.installed"; then
    skip_case='the marker does not record the skip'
fi
if [[ "$skip_case" == 'ok' ]]; then
    ok 'install_skip_cli_installs_no_lib'
else
    bad 'install_skip_cli_installs_no_lib' "$skip_case"
fi

run_install "$work/home-build-fails" MVN_FAIL=1
if [[ $status -ne 0 && ! -e "$work/home-build-fails/lib" \
      && ! -f "$work/home-build-fails/.installed" ]]; then
    ok 'install_writes_no_marker_without_a_lib'
else
    bad 'install_writes_no_marker_without_a_lib' "exit $status, output: $out"
fi

# A complete home, then a failed build. The home keeps every file.
run_install "$work/home-keep"
printf 'keep\n' > "$work/home-keep/data/marker"
before_marker="$(cat "$work/home-keep/.installed")"
before_jars="$(find "$work/home-keep/lib" -type f | sort | tr '\n' ' ')"
run_install "$work/home-keep" MVN_FAIL=1
keep_case='ok'
if [[ $status -eq 0 ]]; then
    keep_case='the second install reported success'
elif [[ "$(cat "$work/home-keep/.installed")" != "$before_marker" ]]; then
    keep_case='the marker changed'
elif [[ "$(find "$work/home-keep/lib" -type f | sort | tr '\n' ' ')" \
        != "$before_jars" ]]; then
    keep_case='the jar set changed'
elif [[ "$(cat "$work/home-keep/data/marker")" != 'keep' ]]; then
    keep_case='the data file changed'
fi
if [[ "$keep_case" == 'ok' ]]; then
    ok 'install_keeps_the_previous_home_when_the_build_fails'
else
    bad 'install_keeps_the_previous_home_when_the_build_fails' "$keep_case"
fi

# No shipped file of the installed runtime names a development identity.
leak_case='ok'
# The files of this task. node-9/install holds older references, and
# CHAT-sufkkqon owns that repair, so it stays out of this case.
while IFS= read -r file; do
    if grep -qE "$dev_names" "$file"; then
        leak_case="$file names $(grep -oE "$dev_names" "$file" | sort -u | tr '\n' ' ')"
        break
    fi
done < <(printf '%s\n' "$repo_root/shell-scripts/build-shell-cli-lib" \
    "$repo_root/shell-scripts/chat-shell-cli" \
    "$work/home-one/lib/shell-cli.options")
if [[ "$leak_case" == 'ok' ]]; then
    ok 'install_holds_no_development_identity'
else
    bad 'install_holds_no_development_identity' "$leak_case"
fi

# -- The Sigma review of 2026-10-22 --------------------------------------

# NODE9_JAVA and NODE9_MVN must reach the build and the launcher. A check of
# one command and a call of another proves nothing.
mkdir -p "$work/alt-tools"
cat > "$work/alt-tools/alt-mvn" <<'STUB'
#!/usr/bin/env bash
set -uo pipefail
printf 'ALT %s\n' "$*" >> "${MVN_CALLS:?Set MVN_CALLS}"
out=''
for word in "$@"; do
    case "$word" in
        -DoutputDirectory=*) out="${word#-DoutputDirectory=}" ;;
    esac
done
if [[ -n "$out" ]]; then
    mkdir -p "$out"
    stage_tmp="$(mktemp -d)"
    mkdir -p "$stage_tmp/com/demo/chat"
    : > "$stage_tmp/com/demo/chat/ShellCliKt.class"
    ( cd "$stage_tmp" && zip -q -r "$out/chat-shell-0.0.1.jar" com )
    rm -rf "$stage_tmp"
    printf 'jar one\n' > "$out/one.jar"
fi
exit 0
STUB
cp "$stub_bin/java-version-stub" "$work/alt-tools/alt-java"
chmod +x "$work/alt-tools/alt-mvn" "$work/alt-tools/alt-java"

# The install runs with no mvn and no java on the path at all. Only the two
# names point at a working pair, so a call through the path cannot succeed.
rm -f "$work/mvn-calls"
out="$(env PATH="/usr/bin:/bin" MVN_CALLS="$work/mvn-calls" \
    NODE9_MVN="$work/alt-tools/alt-mvn" NODE9_JAVA="$work/alt-tools/alt-java" \
    bash "$installer" --prefix "$work/home-alt-tools" 2>&1)"
status=$?
alt_case='ok'
if [[ $status -ne 0 ]]; then
    alt_case="exit $status, output: $out"
elif [[ ! -s "$work/mvn-calls" ]]; then
    alt_case='the named mvn made no call'
elif ! grep -q '^ALT ' "$work/mvn-calls"; then
    alt_case='the build called another mvn than the named one'
elif [[ ! -f "$work/home-alt-tools/lib/shell-cli.options" ]]; then
    alt_case='the home holds no frozen options'
fi
if [[ "$alt_case" == 'ok' ]]; then
    ok 'install_uses_the_named_java_and_mvn'
else
    bad 'install_uses_the_named_java_and_mvn' "$alt_case"
fi

# The install hands the named java to the build as well, so Maven compiles
# with that JDK. See CHAT-bjqzvsoe.
run_install "$work/home-jdk" NODE9_JAVA="$fake_jdk/bin/java" \
    MVN_ENV="$work/install-mvn-env"
if [[ $status -ne 0 ]]; then
    bad 'install_gives_the_named_jdk_to_maven' "exit $status, output: $out"
elif [[ "$(cat "$work/install-mvn-env" 2> /dev/null)" != "JAVA_HOME=$fake_jdk" ]]; then
    bad 'install_gives_the_named_jdk_to_maven' \
        "maven saw $(cat "$work/install-mvn-env" 2> /dev/null), and the JDK is $fake_jdk"
else
    ok 'install_gives_the_named_jdk_to_maven'
fi

# The replacement of lib/ leaves no gap. A failed move keeps the earlier jar
# set, so the home never stands without a runtime.
run_install "$work/home-gap"
printf 'old\n' > "$work/home-gap/lib/old-marker"
out="$(env PATH="$install_bin:/usr/bin:/bin" MVN_CALLS="$work/mvn-calls" \
    NODE9_INSTALL_FAIL_LIB_MOVE=1 bash "$installer" \
    --prefix "$work/home-gap" 2>&1)"
status=$?
gap_case='ok'
if [[ $status -eq 0 ]]; then
    gap_case='the install reported success after a failed move'
elif [[ ! -d "$work/home-gap/lib" ]]; then
    gap_case='the home stands without a lib directory'
elif [[ ! -f "$work/home-gap/lib/old-marker" ]]; then
    gap_case='the earlier jar set is gone'
fi
if [[ "$gap_case" == 'ok' ]]; then
    ok 'install_keeps_a_lib_through_a_failed_move'
else
    bad 'install_keeps_a_lib_through_a_failed_move' "$gap_case"
fi

# --skip-cli on an existing home keeps the earlier jar set. The marker must
# say so, because a reader of cli: skipped expects no runtime.
run_install "$work/home-keep-lib"
run_install "$work/home-keep-lib" -- --skip-cli
marker_case='ok'
if [[ $status -ne 0 ]]; then
    marker_case="exit $status, output: $out"
elif [[ ! -f "$work/home-keep-lib/lib/shell-cli.options" ]]; then
    marker_case='the earlier jar set is gone'
elif grep -q 'cli: skipped' "$work/home-keep-lib/.installed"; then
    marker_case='the marker says skipped while the home holds a jar set'
elif ! grep -q 'cli: lib' "$work/home-keep-lib/.installed"; then
    marker_case="the marker records $(grep '^cli:' "$work/home-keep-lib/.installed")"
fi
if [[ "$marker_case" == 'ok' ]]; then
    ok 'skip_cli_marker_tells_the_truth_about_an_existing_lib'
else
    bad 'skip_cli_marker_tells_the_truth_about_an_existing_lib' "$marker_case"
fi

if ((failures > 0)); then
    echo "$failures case(s) failed"
    exit 1
fi
echo 'all shell CLI runtime cases passed'
