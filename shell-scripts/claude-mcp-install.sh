#!/usr/bin/env bash
# Create portal properties and register the MCP server with Claude Code.
#
# Usage: claude-mcp-install.sh AGENT PROJECT REGISTER_NAME JAVA [SCOPE]
#
# SCOPE is local, user, or project. The default is local, which is private to
# one user in one project. The server name carries the agent, because one host
# can run a portal for each agent.
set -euo pipefail

if [[ "$#" -lt 4 || "$#" -gt 5 ]]; then
    printf 'Usage: %s AGENT PROJECT REGISTER_NAME JAVA [SCOPE]\n' "$0" >&2
    exit 2
fi

agent="$1"
project="$2"
register_name="$3"
java="$4"
scope="${5:-local}"
script_dir="$(cd -- "$(dirname -- "$0")" && pwd)"
repo_root="$(cd -- "$script_dir/.." && pwd)"

# shellcheck source=mcp-install-lib.sh
source "$script_dir/mcp-install-lib.sh"
mcp_install_resolve "$repo_root"
register="$MCP_REGISTER"
launcher="$MCP_LAUNCHER"

case "$scope" in
    local | user | project) ;;
    *)
        printf 'Error: SCOPE must be local, user, or project.\n' >&2
        exit 2
        ;;
esac

if ! command -v claude >/dev/null 2>&1; then
    printf 'Error: claude is not available in PATH.\n' >&2
    exit 127
fi

if [[ ! -x "$java" ]]; then
    printf 'Error: JAVA must name an executable file.\n' >&2
    exit 2
fi

if [[ ! -x "$register" || ! -x "$launcher" ]]; then
    printf 'Error: the repository register command or MCP launcher is not executable.\n' >&2
    exit 2
fi

config="$(mcp_install_config "$agent" "$project" "$register_name")"

exec claude mcp add "$register_name-$agent" --scope "$scope" \
    --env "CHAT_MCP_JAVA=$java" \
    -- "$launcher" --config "$config"
