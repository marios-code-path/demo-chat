#!/usr/bin/env bash
# Check the complete-read rule of chat_libs.sh against a stub adapter.
#
# The stub adapter answers one canned result line, so the check needs no
# portal, no socket, and no core. See CHAT-hmbigvmf.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
libs="$repo_root/shell-scripts/chat_libs.sh"
fixture="$(mktemp -d)"
trap 'rm -rf "$fixture"' EXIT

failures=0
ok() { printf 'ok   %s\n' "$1"; }
bad() {
    printf 'FAIL %s\n' "$1" >&2
    failures=$(( failures + 1 ))
}

config="$fixture/adapter.properties"
printf 'mode=portal-only\nportalSocket=%s/p\nregisterName=register-index\n' "$fixture" > "$config"

# make_launcher RESULT  Write a launcher that answers RESULT to tools/call.
make_launcher() {
    local result="$1"
    cat > "$fixture/adapter" <<LAUNCHER
#!/usr/bin/env bash
while IFS= read -r line; do
    case "\$line" in
        *'"id":1,'*) printf '%s\n' '{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-11-25","capabilities":{"tools":{}},"serverInfo":{"name":"stub","version":"1"}}}' ;;
        *'"id":2,'*) printf '%s\n' '$result' ;;
    esac
done
LAUNCHER
    chmod +x "$fixture/adapter"
}

# run_read  Call chat_read_register in a subshell. Print its return code.
run_read() {
    local code=0
    CHAT_MCP_CONFIG="$config" CHAT_MCP_STDIO="$fixture/adapter" \
        CHAT_MCP_TIMEOUT_SECONDS=5 \
        bash -c 'source "$1"; chat_read_register >"$2" 2>"$3"; exit $?' \
        _ "$libs" "$fixture/stdout" "$fixture/stderr" || code=$?
    printf '%s' "$code"
}

expect() {
    local name="$1" result="$2" wanted="$3" code
    make_launcher "$result"
    code="$(run_read)"
    if [[ "$code" == "$wanted" ]]; then
        ok "$name"
    else
        bad "$name (returned $code, wanted $wanted)"
    fi
}

expect 'a complete read with status 0 is accepted' \
    '{"jsonrpc":"2.0","id":2,"result":{"isError":false,"structuredContent":{"status":0,"complete":true,"lines":[]}}}' 0

expect 'a complete read with no status is refused' \
    '{"jsonrpc":"2.0","id":2,"result":{"isError":false,"structuredContent":{"complete":true,"lines":[]}}}' 1

expect 'a complete read with a nonzero status is refused' \
    '{"jsonrpc":"2.0","id":2,"result":{"isError":false,"structuredContent":{"status":124,"complete":true,"lines":[]}}}' 1

expect 'an error result is refused' \
    '{"jsonrpc":"2.0","id":2,"result":{"isError":true,"structuredContent":{"status":0,"complete":true,"lines":[]}}}' 1

expect 'an incomplete read is refused' \
    '{"jsonrpc":"2.0","id":2,"result":{"isError":false,"structuredContent":{"status":0,"complete":false,"lines":[]}}}' 1

if (( failures > 0 )); then
    printf '%d case(s) failed\n' "$failures" >&2
    exit 1
fi
printf 'chat_libs complete-read rule passed\n'
