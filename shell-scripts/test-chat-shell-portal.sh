#!/usr/bin/env bash
# Checks the portal launch options and the portal script. The installed
# fixture must hold one portal mode option and no one-shot mode option. The
# source plan must do the same. See CHAT-hmbigvmf.
set -uo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
failures=0
T="$(mktemp -d)"
trap 'rm -rf "$T"' EXIT

ok() { echo "ok   $1"; }
fail() { echo "FAIL $1: $2"; failures=$((failures + 1)); }
count() { local text="$1" needle="$2"; grep -cF -- "$needle" <<<"$text" || true; }

mkdir -p "$T/shell-scripts" "$T/chat-deploy/target" "$T/lib" "$T/portal/shell-scripts"
cp "$here/chat-shell-cli" "$T/shell-scripts/chat-shell-cli"
echo "unused.jar" > "$T/chat-deploy/target/shell-cli.classpath"

# The chat-build stub prints one plan. It holds a one-shot mode option, which
# the launcher must remove.
printf '#!/usr/bin/env bash\nprintf "  -Dapp.key.type=uuid\\n  -Dchat.shell.mode=one-shot\\n"\n' \
    > "$T/shell-scripts/chat-build"
chmod +x "$T/shell-scripts/chat-build"

cat > "$T/lib/shell-cli.options" <<'EOF'
# key-type uuid
# node-id 9
# java java
-Dapp.key.type=uuid
-Dchat.shell.mode=one-shot
-Dspring.shell.interactive.enabled=false
-Dspring.main.web-application-type=none
EOF

# The portal script runs a stub launcher. The stub prints the mode and the
# arguments, so the test sees what the script set.
cat > "$T/portal/shell-scripts/chat-shell-cli" <<'EOF'
#!/usr/bin/env bash
printf 'mode=%s args=%s\n' "${CHAT_SHELL_MODE:-}" "$*"
EOF
chmod +x "$T/portal/shell-scripts/chat-shell-cli"
cp "$here/chat-shell-portal" "$T/portal/shell-scripts/chat-shell-portal" 2>/dev/null || true

check_portal_options() {
    local name="$1" text="$2" socket="$3"
    local portal one_shot socket_count
    portal="$(count "$text" '-Dchat.shell.mode=portal')"
    one_shot="$(count "$text" '-Dchat.shell.mode=one-shot')"
    socket_count="$(count "$text" "-Dchat.shell.portal.socket=$socket")"
    [[ "$portal" == 1 ]] || fail "$name" "expected one portal mode option, got $portal"
    [[ "$one_shot" == 0 ]] || fail "$name" "expected no one-shot mode option, got $one_shot"
    [[ "$socket_count" == 1 ]] || fail "$name" "expected one portal socket option, got $socket_count"
}

socket="$T/portal.sock"

installed="$(CHAT_SHELL_LIB="$T/lib" CHAT_SHELL_MODE=portal \
    CHAT_SHELL_PORTAL_SOCKET="$socket" CHAT_SHELL_CLI_PRINT_OPTIONS=1 \
    "$T/shell-scripts/chat-shell-cli")"
begin=$failures
check_portal_options "installed options in portal mode" "$installed" "$socket"
(( failures == begin )) && ok "installed options hold one portal mode option"

installed_default="$(CHAT_SHELL_LIB="$T/lib" CHAT_SHELL_CLI_PRINT_OPTIONS=1 \
    "$T/shell-scripts/chat-shell-cli")"
[[ "$(count "$installed_default" '-Dchat.shell.mode=one-shot')" == 1 ]] \
    || fail "installed default mode" "expected one one-shot mode option"
[[ "$(count "$installed_default" '-Dchat.shell.mode=portal')" == 0 ]] \
    || fail "installed default mode" "expected no portal mode option"
ok "installed options keep one-shot as the default"

source_opts="$(CHAT_SHELL_KEY_TYPE=uuid CHAT_SHELL_MODE=portal \
    CHAT_SHELL_PORTAL_SOCKET="$socket" CHAT_SHELL_CLI_PRINT_OPTIONS=1 \
    "$T/shell-scripts/chat-shell-cli")"
begin=$failures
check_portal_options "source plan in portal mode" "$source_opts" "$socket"
(( failures == begin )) && ok "source plan holds one portal mode option"

source_default="$(CHAT_SHELL_KEY_TYPE=uuid CHAT_SHELL_CLI_PRINT_OPTIONS=1 \
    "$T/shell-scripts/chat-shell-cli")"
[[ "$(count "$source_default" '-Dchat.shell.mode=one-shot')" == 1 ]] \
    || fail "source default mode" "expected one one-shot mode option"
[[ "$(count "$source_default" '-Dchat.shell.mode=portal')" == 0 ]] \
    || fail "source default mode" "expected no portal mode option"
ok "source plan keeps one-shot as the default"

begin=$failures
for missing in CHAT_SHELL_USER CHAT_SHELL_PASSWORD_FILE CHAT_SHELL_PORTAL_SOCKET; do
    (
        unset CHAT_SHELL_USER CHAT_SHELL_PASSWORD_FILE CHAT_SHELL_PORTAL_SOCKET
        export CHAT_SHELL_USER=Admin
        export CHAT_SHELL_PASSWORD_FILE=/tmp/password
        export CHAT_SHELL_PORTAL_SOCKET=/tmp/portal.sock
        unset "$missing"
        "$T/portal/shell-scripts/chat-shell-portal" > /dev/null 2> "$T/portal/err"
    )
    status=$?
    [[ "$status" == 2 ]] || fail "portal script without $missing" "exit $status, expected 2"
    grep -q "$missing" "$T/portal/err" || fail "portal script without $missing" "the error does not name $missing"
done
(( failures == begin )) && ok "the portal script requires its three variables"

(
    export CHAT_SHELL_USER=Admin
    export CHAT_SHELL_PASSWORD_FILE=/tmp/password
    export CHAT_SHELL_PORTAL_SOCKET=/tmp/portal.sock
    "$T/portal/shell-scripts/chat-shell-portal" alpha beta > "$T/portal/out" 2> "$T/portal/err"
begin=$failures
)
status=$?
[[ "$status" == 0 ]] || fail "portal script launch" "exit $status, expected 0"
grep -q 'mode=portal' "$T/portal/out" || fail "portal script launch" "the launcher did not see portal mode"
grep -q 'args=alpha beta' "$T/portal/out" || fail "portal script launch" "the launcher did not see the arguments"
(( failures == begin )) && ok "the portal script selects portal mode and passes the arguments"

if (( failures > 0 )); then
    echo "$failures failure(s)"
    exit 1
fi
echo "all portal launch checks passed"
