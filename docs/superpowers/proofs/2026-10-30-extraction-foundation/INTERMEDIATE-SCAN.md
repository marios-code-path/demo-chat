# Intermediate lexical inventory

Basis: `f35dc407175a9558a984131fbb2ca46e8fbb0892`.
This report counts matching lines, not variable names or individual occurrences.
The tracked scan finds 1271 matching lines in 98 files.
The scan excludes the deployment subtree and nested worktrees.
It uses only the three approved case-insensitive patterns.
No match receives a permanent exception from this report.
CHAT-gdddeqkb remains pending.

```bash
git grep -n -i -E 'node-9|node9|node_9' f35dc407 -- . \
    ':(exclude)node-9/**' ':(exclude).worktrees/**' \
    ':(exclude).claude/worktrees/**' ':(exclude).codex/worktrees/**'
```

| Group | Matching lines | Remaining responsibility |
| --- | ---: | --- |
| Plans, specifications, and proof records beneath `docs/superpowers`. | 911 | Classify current instructions separately from historical evidence during extraction. |
| Frozen register history and its archives. | 13 | Preserve frozen history and report it separately. |
| Other tracked paths below. | 347 | Apply the extraction move map and neutral-reference changes. |

The table below lists every matching path outside the first two groups.
The temporary reader and its tests remain necessary until final deployment adapters exist.
The original deployment bodies and deployment tests remain until their transfer.
Compiled comments, active documentation, recipes, and CI still require the approved extraction changes.
General builders retain two transitional prose references.
This foundation does not establish source independence for the full repository.

| Matching lines | Path |
| ---: | --- |
| 4 | `.github/workflows/maven.yml` |
| 1 | `CHAT_MCP_AGENTS.md` |
| 1 | `README.md` |
| 2 | `chat-mcp/src/main/kotlin/com/demo/chat/mcp/portal/PortalToolRegistration.kt` |
| 1 | `chat-shell/src/main/kotlin/com/demo/chat/shell/commands/PubSubCommands.kt` |
| 1 | `chat-shell/src/main/kotlin/com/demo/chat/shell/portal/PortalWriteRules.kt` |
| 2 | `chat-shell/src/main/kotlin/com/demo/chat/shell/portal/PortalWriteService.kt` |
| 1 | `chat-shell/src/test/kotlin/com/demo/chat/test/commands/PubSubRegisterReadTests.kt` |
| 2 | `docs/MCP-ADAPTER.md` |
| 3 | `docs/SHELL-COMMAND-CONTRACT.md` |
| 29 | `docs/SHELL-OPERATOR.md` |
| 18 | `justfile` |
| 21 | `shell-scripts/build-node-9-image` |
| 2 | `shell-scripts/build-shell-cli-lib` |
| 1 | `shell-scripts/generate-agent-instructions` |
| 25 | `shell-scripts/legacy-node-inputs.sh` |
| 9 | `shell-scripts/test-build-node-9-image.sh` |
| 27 | `shell-scripts/test-legacy-node-inputs.sh` |
| 1 | `shell-scripts/test-mcp-install.sh` |
| 8 | `shell-scripts/test-node-9-bin-only.sh` |
| 15 | `shell-scripts/test-node-9-install-secrets.sh` |
| 18 | `shell-scripts/test-node-9-register.sh` |
| 3 | `shell-scripts/test-node-9-remote.sh` |
| 17 | `shell-scripts/test-node-9-secrets.sh` |
| 2 | `shell-scripts/test-node-9-tunnel.sh` |
| 104 | `shell-scripts/test-node-9.sh` |
| 2 | `shell-scripts/test-profile-mcp-paths.sh` |
| 2 | `shell-scripts/test-profile-tunnel.sh` |
| 4 | `shell-scripts/test-register-host-install.sh` |
| 1 | `shell-scripts/test-register-source-independence.sh` |
| 4 | `shell-scripts/test-setup-portal.sh` |
| 16 | `shell-scripts/test-shell-cli-lib.sh` |
