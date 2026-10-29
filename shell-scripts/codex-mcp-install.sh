#!/usr/bin/env bash
# Create portal properties and register the MCP server with Codex.
#
# Usage: codex-mcp-install.sh AGENT PROJECT REGISTER_NAME JAVA
set -euo pipefail

if [[ "$#" -ne 4 ]]; then
    printf 'Usage: %s AGENT PROJECT REGISTER_NAME JAVA\n' "$0" >&2
    exit 2
fi

agent="$1"
project="$2"
register_name="$3"
java="$4"
script_dir="$(cd -- "$(dirname -- "$0")" && pwd)"
repo_root="$(cd -- "$script_dir/.." && pwd)"

# shellcheck source=mcp-install-lib.sh
source "$script_dir/mcp-install-lib.sh"
mcp_install_resolve "$repo_root"
register="$MCP_REGISTER"
launcher="$MCP_LAUNCHER"

if ! command -v codex >/dev/null 2>&1; then
    printf 'Error: codex is not available in PATH.\n' >&2
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

exec codex mcp add "$register_name" \
    --env "CHAT_MCP_JAVA=$java" \
    -- "$launcher" --config "$config"
