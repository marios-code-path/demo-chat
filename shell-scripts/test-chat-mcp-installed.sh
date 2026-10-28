#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
fixture="$(mktemp -d)"
trap 'rm -rf "$fixture"' EXIT
mkdir -p "$fixture/bin" "$fixture/lib/mcp"
cp "$repo_root/shell-scripts/chat-mcp-stdio" "$fixture/bin/chat-mcp-stdio"
: > "$fixture/.installed"
: > "$fixture/lib/mcp/chat-mcp-test.jar"
: > "$fixture/lib/mcp/dependency-test.jar"

output="$(CHAT_MCP_JAVA=/bin/echo "$fixture/bin/chat-mcp-stdio" --config "$fixture/config.properties")"
[[ "$output" == *"$fixture/lib/mcp/chat-mcp-test.jar"* ]]
[[ "$output" == *"$fixture/lib/mcp/dependency-test.jar"* ]]
[[ "$output" == *'com.demo.chat.mcp.McpAdapterMainKt'* ]]
printf 'installed MCP launcher passed\n'
