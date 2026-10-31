#!/usr/bin/env bash
# Resolve general MCP tools without deployment discovery.

mcp_install_resolve() {
    local repo_root="$1" tools_dir selected_bin
    tools_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
    if [[ -f "$tools_dir/node-inputs.sh" ]]; then
        . "$tools_dir/node-inputs.sh"
        chat_node_inputs || return $?
    fi
    if [[ "${CHAT_DEPLOYMENT_HOME+x}" ]]; then
        [[ "$CHAT_DEPLOYMENT_HOME" == /* && -d "$CHAT_DEPLOYMENT_HOME" ]] \
            || { printf 'mcp-install: select an existing absolute CHAT_DEPLOYMENT_HOME.\n' >&2; return 2; }
    fi
    if [[ "${CHAT_DEPLOYMENT_BIN+x}" ]]; then
        [[ "$CHAT_DEPLOYMENT_BIN" == /* && -d "$CHAT_DEPLOYMENT_BIN" ]] \
            || { printf 'mcp-install: select an existing absolute CHAT_DEPLOYMENT_BIN.\n' >&2; return 2; }
        selected_bin="$CHAT_DEPLOYMENT_BIN"
    elif [[ "${CHAT_DEPLOYMENT_HOME+x}" ]]; then
        selected_bin="$CHAT_DEPLOYMENT_HOME/bin"
    else
        selected_bin="$tools_dir"
    fi
    MCP_REGISTER="${CHAT_REGISTER_COMMAND-$selected_bin/register}"
    MCP_LAUNCHER="${CHAT_MCP_STDIO-$selected_bin/chat-mcp-stdio}"
    if [[ ! "${CHAT_MCP_STDIO+x}" && ! -x "$MCP_LAUNCHER" ]]; then
        MCP_LAUNCHER="$tools_dir/chat-mcp-stdio"
    fi
    [[ -x "$MCP_REGISTER" && -x "$MCP_LAUNCHER" ]] \
        || { printf 'mcp-install: the selected register or adapter is not executable.\n' >&2; return 2; }
}

mcp_install_config() {
    if declare -F chat_legacy_mcp_config > /dev/null; then
        chat_legacy_mcp_config "$MCP_REGISTER" "$@"
    else
        CHAT_ALLOW_SOURCE_TREE=1 "$MCP_REGISTER" config \
            --agent "$1" --project "$2" --register "$3"
    fi
}
