#!/usr/bin/env bash
# Shared path rules for the MCP client installers.
#
# The installers register one MCP server with one client. They need two paths.
# A source tree holds neither the MCP runtime nor a register command that
# runs, so an installed home wins when one exists.
#
# **The installed home is permanent:** /opt/demo-chat/node-9 on every host.
# Node 9 serves every project from that one home, and the project enters only
# the config name and the server name. NODE9_HOME names another home.
#
# Call mcp_install_resolve with the repository root. It sets MCP_REGISTER and
# MCP_LAUNCHER.

# mcp_install_resolve REPO_ROOT  Set the register command and the launcher.
mcp_install_resolve() {
    local repo_root="$1"
    local home="${NODE9_HOME:-/opt/demo-chat/node-9}"
    if [[ ! -f "$home/.installed" ]]; then
        home="$repo_root/node-9"
    fi
    MCP_REGISTER="$home/bin/register"
    if [[ -x "$home/bin/chat-mcp-stdio" ]]; then
        MCP_LAUNCHER="$home/bin/chat-mcp-stdio"
    else
        MCP_LAUNCHER="$repo_root/shell-scripts/chat-mcp-stdio"
    fi
}

# mcp_install_config AGENT PROJECT REGISTER_NAME  Write one config, print its path.
#
# `register config` makes no shell call, so a source tree may write it. That
# tree refuses every other command. See CHAT-lmwuxiiq.
mcp_install_config() {
    NODE9_ALLOW_SOURCE_TREE=1 "$MCP_REGISTER" config \
        --agent "$1" --project "$2" --register "$3"
}
