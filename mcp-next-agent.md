# Theta handoff: Demo Chat MCP adapter

This worktree stages the first MCP implementation phase for Theta.

## Branch and base

- Branch: `chat-mcp-impl`.
- Base: master `4e1955bc`.
- MCP design commit: `69a50d70`.
- Worktree: `.worktrees/mcp-impl`.
- Nothing is pushed.
- No pull request is open.
- Do not merge without explicit approval.

## Goal

Build a standalone Kotlin MCP adapter for Demo Chat.

The first version supports these operations:

- Read configured topics.
- Read messages.
- Search messages inside one topic.
- Send messages when the operator enables sending.

Exclude room administration, membership administration, grant administration, global search, credentials, raw indexes, subscriptions, attachments and encrypted payload processing.

Read the complete design at `docs/superpowers/specs/2026-09-27-demo-chat-mcp-design.md`.

## Architecture

- Use a standalone stdio adapter over authenticated Demo Chat REST routes.
- Do not add database credentials or direct store access.
- Keep stdout for MCP protocol messages.
- Send diagnostics to stderr.
- Use the official Kotlin MCP SDK when its protocol support is compatible.
- Verify protocol revision `2026-07-28` before implementation.
- Compile the distributable with GraalVM Native Image.
- Keep JVM execution for development and tests.

## First bounded phase

Start with feasibility work.

1. Verify the Kotlin SDK version and protocol support.
2. Add the smallest `chat-mcp` module.
3. Add one native stdio server.
4. Add authenticated REST configuration.
5. Implement `chat_list_topics`.
6. Implement `chat_get_topic`.
7. Test MCP discovery and tool calls with a real client.
8. Test stdout protocol purity and clean stdin shutdown.

Keep search and send behind later tasks until the first tools work.

## Configuration contract

Required settings:

- Fixed backend HTTPS origin.
- Operator-controlled credential file.
- Key type `long` or `uuid`.
- One to 100 canonical topic IDs.
- `enableSend`, default false.
- `enableSearch`, default false.

Do not accept backend URLs, credentials, sender IDs, roots or partitions from tool arguments.

## Identity rules

- Encode IDs as JSON strings.
- Accept canonical Long text within range.
- Accept canonical lowercase UUID text.
- Reject zero IDs, fractions, exponent notation, whitespace, plus signs, leading zeros and overflow.
- Resolve domains through backend routes.
- Do not decode tokens as verified identity.
- Do not synthesize roots.

## Existing route map

- Topic lookup: `GET /topic/id/{id}`.
- Message lookup: `GET /message/id/{id}`.
- Topic recall: `POST /message/recall/topic`.
- Send: `POST /message/send/{id}` with `text/plain`.

Verify each JSON envelope against the running backend.

## Security boundary

`CHAT-znprrzhn` remains open for deployed route enforcement.

The MCP adapter must not claim permission enforcement until the backend route or service boundary is intercepted and tested.

`CHAT-xcmpudyb` remains open because Cassandra message sending fails on timestamp mapping.

Do not use Cassandra send as the first end-to-end feasibility target.

The adapter allowlist narrows scope. It does not replace backend authorization.

## Required error behavior

- Use SDK protocol errors for malformed MCP envelopes.
- Use stable application codes for backend failures.
- Do not expose raw backend exceptions.
- Do not reveal hidden object names, counts, IDs or content.
- Do not retry sends automatically.
- Return an unknown outcome when dispatch may have occurred before connection loss.

## Verification

- Run focused tests with the complete Maven reactor.
- Include upstream modules with `-am` when downstream modules consume them.
- Verify native compilation before claiming a usable adapter.
- Use a real MCP client for discovery and tool calls.
- Test Long IDs above `2^53`.
- Test canonical UUID IDs.
- Test invalid IDs before backend dispatch.
- Test stdout protocol purity.
- Run `drift check` for bound documents.
- Run `git diff --check`.

## Review boundary

Stop after the first bounded feasibility phase.

Report SDK compatibility, native compilation, route decoding and authentication evidence.

Do not push, open a pull request or merge without review approval.
