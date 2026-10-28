#!/usr/bin/env bash
# Chat library helpers. Source this file. Do not run it.
#
# Requires bash 3.2 or later. Avoids bash 4 features.
#
#   source shell-scripts/chat_libs.sh
#   chat_read_register
#   chat_read_register Session
#
# Environment:
#   MY_AGENT_NAME    Portal login name. It selects the configuration file.
#                    Default: theta.
#   MY_PROJECT_NAME  Project label. It selects the configuration file.
#                    Default: the base name of MY_PROJECT_HOME.
#   MY_PROJECT_HOME  Project root. It holds shell-scripts/chat-mcp-stdio.
#                    Default: the parent of this file's directory.
#   CHAT_MCP_CONFIG  Full path to the adapter properties file.
#                    Overrides MY_AGENT_NAME and MY_PROJECT_NAME.
#   CHAT_MCP_STDIO   Full path to the adapter launcher.
#                    Overrides MY_PROJECT_HOME.
#   CHAT_MCP_TIMEOUT_SECONDS  Seconds to wait for the result. Default: 40.
#   CHAT_MCP_VERBOSE  Set to 1 to print every protocol line.
#
# chat_read_register prints the tool result on stdout.
# The adapter diagnostic line goes to stderr.
#
# Return codes for chat_read_register:
#   0  Complete read. status is 0 and isError is absent or false.
#   1  Incomplete read. status is nonzero, or isError is true.
#   2  Usage error.
#   3  The configuration file or the adapter launcher is missing.
#   4  The adapter wrote no parseable result.

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
    printf 'chat_libs.sh: source this file. Do not run it.\n' >&2
    exit 2
fi

if [[ -n "${CHAT_LIBS_SH_SOURCED:-}" ]]; then
    return 0
fi
CHAT_LIBS_SH_SOURCED=1

CHAT_LIBS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CHAT_PROJECT_ROOT="$(cd "$CHAT_LIBS_DIR/.." && pwd)"

_chat_libs_message() {
    printf 'chat_read_register: %s\n' "$1" >&2
}

# Read the topic register through the MCP adapter.
# The optional argument is one topic name.
chat_read_register() {
    local topic=''
    local project_home agent_name project_name config_dir legacy_config
    local config launcher arguments call response adapter_status
    local line result_line is_error reg_status complete

    if (( $# > 1 )); then
        _chat_libs_message 'use chat_read_register [topic]'
        return 2
    fi
    topic="${1:-}"

    if [[ -n "$topic" ]]; then
        if [[ ! "$topic" =~ ^[A-Za-z0-9][A-Za-z0-9-]{0,63}$ ]]; then
            _chat_libs_message "invalid topic: $topic"
            return 2
        fi
    fi

    project_home="${MY_PROJECT_HOME:-$CHAT_PROJECT_ROOT}"
    launcher="${CHAT_MCP_STDIO:-$project_home/shell-scripts/chat-mcp-stdio}"

    config="${CHAT_MCP_CONFIG:-}"
    if [[ -z "$config" ]]; then
        agent_name="${MY_AGENT_NAME:-theta}"
        project_name="${MY_PROJECT_NAME:-${project_home%/}}"
        project_name="${project_name##*/}"
        if [[ ! "$agent_name" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]]; then
            _chat_libs_message "invalid MY_AGENT_NAME: $agent_name"
            return 2
        fi
        if [[ ! "$project_name" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]]; then
            _chat_libs_message "invalid project name: $project_name"
            return 2
        fi
        agent_name="$(printf '%s' "$agent_name" | tr '[:upper:]' '[:lower:]')"
        project_name="$(printf '%s' "$project_name" | tr '[:upper:]' '[:lower:]')"
        config_dir="${XDG_CONFIG_HOME:-$HOME/.config}/demo-chat"
        config="$config_dir/mcp-portal-$agent_name-$project_name.properties"
        legacy_config="$config_dir/mcp-portal-$agent_name.properties"
        if [[ ! -f "$config" && -f "$legacy_config" ]]; then
            _chat_libs_message "using the shared configuration file: $legacy_config"
            config="$legacy_config"
        fi
    fi

    if [[ ! -f "$config" ]]; then
        _chat_libs_message "no configuration file: $config"
        return 3
    fi
    if [[ ! -x "$launcher" ]]; then
        _chat_libs_message "no adapter launcher: $launcher"
        return 3
    fi

    arguments='{}'
    if [[ -n "$topic" ]]; then
        arguments="{\"topic\":\"$topic\"}"
    fi
    call="{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"chat_read_register\",\"arguments\":$arguments}}"

    local tmpdir fifo outfile adapter_pid waited limit
    tmpdir="$(mktemp -d "${TMPDIR:-/tmp}/chat-libs.XXXXXX")" || {
        _chat_libs_message 'cannot create a temporary directory'
        return 4
    }
    fifo="$tmpdir/in"
    outfile="$tmpdir/out"

    if ! mkfifo "$fifo" 2>/dev/null; then
        rm -rf "$tmpdir"
        _chat_libs_message 'cannot create a named pipe'
        return 4
    fi

    "$launcher" --config "$config" < "$fifo" > "$outfile" &
    adapter_pid=$!

    (
        exec 3>"$fifo"
        printf '%s\n' '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"chat-libs","version":"1"}}}' >&3
        printf '%s\n' '{"jsonrpc":"2.0","method":"notifications/initialized"}' >&3
        printf '%s\n' "$call" >&3
        waited=0
        limit="${CHAT_MCP_TIMEOUT_SECONDS:-40}"
        while kill -0 "$adapter_pid" 2>/dev/null; do
            if grep -q '"id":2' "$outfile" 2>/dev/null; then
                break
            fi
            if (( waited >= limit * 10 )); then
                break
            fi
            sleep 0.1
            waited=$(( waited + 1 ))
        done
        exec 3>&-
    )

    if wait "$adapter_pid"; then
        adapter_status=0
    else
        adapter_status=$?
    fi

    response=''
    if [[ -f "$outfile" ]]; then
        response="$(cat "$outfile")"
    fi
    rm -rf "$tmpdir"

    result_line=''
    while IFS= read -r line; do
        case "$line" in
            *'"id":2'*) result_line="$line" ;;
        esac
    done <<< "$response"

    if [[ -z "$result_line" ]]; then
        _chat_libs_message "the adapter wrote no result. exit status is $adapter_status."
        return 4
    fi

    if [[ "${CHAT_MCP_VERBOSE:-0}" == 1 ]]; then
        printf '%s\n' "$response"
    else
        printf '%s\n' "$result_line"
    fi

    is_error=false
    reg_status=''
    complete=false

    if command -v jq >/dev/null 2>&1; then
        is_error="$(printf '%s\n' "$result_line" | jq -r 'if (.result.isError // false) then "true" else "false" end' 2>/dev/null)"
        reg_status="$(printf '%s\n' "$result_line" | jq -r '.result.structuredContent.status // empty' 2>/dev/null)"
        complete="$(printf '%s\n' "$result_line" | jq -r 'if (.result.structuredContent.complete // false) then "true" else "false" end' 2>/dev/null)"
    else
        case "$result_line" in
            *'"isError":true'*) is_error=true ;;
        esac
        case "$result_line" in
            *'"complete":true'*) complete=true ;;
        esac
        reg_status="$(printf '%s\n' "$result_line" | sed -n 's/.*"structuredContent":{"status":\([0-9][0-9]*\).*/\1/p')"
    fi

    if [[ "$is_error" == true ]]; then
        return 1
    fi
    # A complete read carries status 0. A missing status is not that value.
    if [[ "$reg_status" != 0 ]]; then
        return 1
    fi
    if [[ "$complete" != true ]]; then
        return 1
    fi
    return 0
}
