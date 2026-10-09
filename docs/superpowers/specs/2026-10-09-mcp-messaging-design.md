# MCP messaging before Kafka

Date: 2026-10-09.
Issue: `CHAT-teujorxl`.
Status: scope approved. The detailed contracts below await spec review.

## Goal

An authenticated agent can read room history, submit a message, and inspect command progress through MCP.
This milestone uses the Stage 1 memory command bus.
It adds no durability guarantee.

## Architecture

Extend `chat-mcp` through existing REST routes.
Keep the current Kotlin SDK, stdio transport, protocol revision, and credential file.
Keep the two existing topic tools and their output contracts.
The adapter holds no store credential and opens no RSocket connection.

Legacy send would require less response handling, but it creates a new request identity for every call.
A general agent framework would add components without helping this milestone.
Use the submit route with a caller-owned request ID.

## Tool surface

| Tool | Required input | REST operation |
|---|---|---|
| `chat_list_messages` | `topicId` | `GET /message/list/{id}` |
| `chat_get_message` | `messageId` | `GET /message/id/{id}` |
| `chat_send_message` | `topicId`, `text`, `requestId` | `POST /message/submit/{id}` |
| `chat_get_command_status` | `commandId` | `GET /message/command/{commandId}` |

Each input is an object with string fields.
Reject missing fields, unknown fields, and incorrect field types.
Validate object IDs through the existing `keyType` rules.
Encode command IDs as path segments, without treating them as object IDs.
Declare input and output schemas for each tool.

The first two tools return `{messages: Message[]}` and `{message: Message}` respectively.
A message contains `messageKey`, `senderId`, `topicId`, `text`, and `timestamp`.
A message key contains `id` and `root`.
All IDs remain strings, including numeric IDs above `2^53`.
Timestamps use ISO-8601 UTC text.
Preserve message text without trimming or rewriting it.

The send result contains `receipt`, `outcome`, and `backends`.
A receipt contains `commandId` and `messageKey`.
Backend map keys use `PERSISTENCE`, `INDEX`, `VECTOR`, and `PUBSUB`.
Each backend value contains `state`, `attempts`, and nullable `nextRecoveryAt`.
States use `PENDING`, `SUCCEEDED`, `FAILED`, and `UNCERTAIN`.
Do not expose raw backend reason strings.

The status result contains `commandId`, `requestId`, `receipt`, `backends`, and `version`.
It reports backend progress without inventing a stored caller outcome.
The command ID in the result must match the requested command ID.

## Submission and outcomes

Register `chat_send_message` only when `enableSend=true`.
The default remains `false`.
Register the new read tools independently of `enableSend`.
Do not activate search through this milestone.

Read tools declare `readOnlyHint=true` and `idempotentHint=true`.
Send declares `readOnlyHint=false`, `destructiveHint=false`, and `idempotentHint=false`.
The process-lifetime request mapping does not justify an unconditional idempotency hint.

Send text must be nonblank and contain 1 to 16,384 UTF-8 bytes.
A request ID must contain 1 to 128 visible ASCII characters.
The caller chooses one request ID for each intentional message.
The caller retains that ID for repeated submissions.
Do not derive it from an MCP invocation ID.
Send the ID in the `Idempotency-Key` header.
Send the text as `text/plain`.
The server derives the sender from authentication.
Accept no sender override.

| Outcome | REST status | MCP meaning |
|---|---|---|
| `COMPLETED` | 201 | All backends in the server completion requirement succeeded. |
| `ACCEPTED` | 202 | Admission committed with completion requirement `none`. |
| `PENDING` | 202 | The caller wait ended. Execution continues. |
| `INCOMPLETE` | 424 | A required backend failed. Preserve the receipt and backend states. |

Return the first three outcomes as structured tool results with `isError=false`.
Return `INCOMPLETE` with `isError=true`, while retaining its structured command result.
Validate the typed response before reporting a command outcome.
Do not interpret HTTP 202 alone as proof of completion.
Completion follows server configuration, which defaults to `P,I`.
This tool accepts no completion override.

Repeated submissions recover the same identity only during one server process lifetime.
Changing the room or content under the same authenticated request identity produces a conflict.
Restarting the server loses request mappings, command status, and unresolved commands.
Do not advertise retry safety across server restarts.

## Authorization

Validate every topic argument against `topicIds` before a backend request.
Check each returned message belongs to a configured topic before exposing it.
Fail a history result containing an out-of-scope message without returning partial content.
The server remains responsible for authorization before content leaves the backend.
The local topic check does not replace server authorization.

The server restricts command status to its authenticated owner.
The status tool does not promise an additional topic allowlist check.
Read-only tool annotations do not authorize any request.
Keep 403 and 404 indistinguishable in client error messages.

## Transport and limits

Extend the existing bounded HTTP client rather than adding another client.
Preserve the credential origin check and the shared request limit.
Preserve the existing 30-second call deadline and 1 MiB response bound.
Read the credential file for each request.
Perform no automatic submission retry and follow no submission redirect.

History uses the finite stored-history route, not the live listener route.
The backend route has no pagination.
Preserve its returned message order without claiming a stable paging boundary.
An oversized response produces `LIMIT_EXCEEDED`.
Do not silently truncate history.
Large-room pagination remains separate work.

## Error contract

Keep existing error codes and fixed client sentences.
Add `INVALID_INPUT` for invalid tool input or a backend submission refusal with status 400.
Add `REQUEST_CONFLICT` for status 409.
Add `COMMAND_INCOMPLETE` for a validated `INCOMPLETE` result.
These codes require fixed client sentences and error-contract tests.

A submission without a reliable response can have executed.
Return `OUTCOME_UNKNOWN` with the caller request ID and no invented receipt.
Include no raw backend response or exception text.
Keep `retryable=false` for submission errors in this milestone.
The client must not treat this flag as permission to create another request ID.
Document the process-lifetime limit beside repeated-submission guidance.

Malformed responses after submission also produce an unknown outcome when admission cannot be established.
A valid receipt with `PENDING` remains a normal command result.
Keep authorization errors separate from pending or unknown execution.

Keep stdout limited to MCP protocol frames.
Keep diagnostics on stderr with the existing `chat-mcp:` prefix.
Log no credentials, message content, argument values, or raw backend reason strings.

## Verification

1. Discover the existing tools and the four new tools with sending enabled.
2. Confirm sending is absent when `enableSend` is false.
3. Submit through a real authenticated stdio MCP client.
4. Read the message through history and by its ID.
5. Observe a pending command and its later backend success.
6. Repeat one request identity and verify one command identity and one message identity.
7. Change content or room under that identity and verify a conflict.
8. Refuse unauthorized reads, sends, and another owner's command status.
9. Refuse out-of-scope message results before exposing their content.
10. Lose a submission response and verify no automatic second submission occurs.
11. Preserve a validated 424 command result and its receipt.
12. Reject oversized history without partial output.
13. Preserve numeric IDs above `2^53` exactly.
14. Verify protocol output, bounded requests, shutdown, and diagnostic redaction.

Use focused tests for contracts and HTTP behavior.
Use the real MCP client harness for protocol evidence.
Use an authenticated deployment for end-to-end evidence.
No mocked result proves deployed authorization.
Do not investigate or repair unrelated Cassandra failures.

## Delivery boundary

The approved scope permits optional MCP sending for the initial deployment.
This replaces the read-only MCP assumption in `CHAT-aqpcacwv` when this milestone is delivered.
It does not claim that sending exists before delivery.

The implementation plan will cover transport, tools, verification, and launch documentation.
Update the existing MCP design, adapter documentation, and relevant register guidance with the implementation.
Review bound prose before changing drift provenance.

Kafka, replication, live subscriptions, search, vector recall, attachments, and impersonation remain outside this milestone.
History pagination, native compilation, and automatic token refresh also remain outside it.
The implementation starts only after spec review and implementation-plan review.
