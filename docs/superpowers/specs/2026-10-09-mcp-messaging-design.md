# MCP messaging before Kafka

Date: 2026-10-09.
Issue: `CHAT-teujorxl`.
Status: approved at `5c5029f6` on 2026-10-09. Acceptance setup incorporates the subsequent review notes.

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
Require command IDs to contain 1 to 128 ASCII characters from `!` through `~`.
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
The `backends` schema permits an empty map.
The server returns that empty map for `ACCEPTED` with completion requirement `none`.
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
This text limit is an adapter rule. The current server does not enforce it.
A request ID must contain 1 to 128 ASCII characters from `!` through `~`.
The caller chooses one request ID for each intentional message.
The caller retains that ID for repeated submissions.
The namespace belongs to the authenticated agent identity, not to one adapter process.
Two adapters with the same agent identity share that namespace.
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

Return the first three outcomes in `structuredContent` with `isError=false` and no application `_meta`.
Return `INCOMPLETE` in `structuredContent` with `isError=true`.
Its `_meta` contains exactly `code`, `message`, and `retryable`, with code `COMMAND_INCOMPLETE`.
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
The server checks `SUBSCRIBE` for history and `SEND` for submission.
Message-by-ID reads check `GET` on the message key.
The shipped `{Anon, Message, GET}` grant permits that read for every caller.
Therefore, the server does not enforce a room limit for `chat_get_message`.
The adapter's returned-topic check is the only configured room limit for that tool.
An out-of-scope message produces `NOT_AVAILABLE` without message content or identifiers in the error.
Do not log its content while decoding or rejecting the response.
This milestone does not change server authorization policy.

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
A submission response with status 3xx produces `OUTCOME_UNKNOWN`.
Do not send its body or credential to the redirect target.
Existing read redirect rules remain unchanged.

The server completion timeout defaults to 5 seconds and has no upper bound in the current configuration parser.
The adapter's 30-second deadline includes connection and response processing.
Launch documentation must require a server completion timeout below that deadline, with time for transport and response processing.
Use `app.command.completion.timeout=5s` for the documented deployment.
The adapter cannot validate that remote property at startup through the existing API.
A longer server wait can produce `OUTCOME_UNKNOWN` instead of `PENDING` when the adapter deadline expires.
Even a shorter server wait cannot guarantee that a response arrives before the adapter deadline.

History uses the finite stored-history route, not the live listener route.
Request `application/x-ndjson` for history and `application/json` for the other routes.
Decode each history record under the response bound before exposing any message.
The backend route has no pagination.
Preserve its returned message order without claiming a stable paging boundary.
An oversized response produces `LIMIT_EXCEEDED`.
Do not silently truncate history.
Large-room pagination remains separate work.

## Error contract

Keep existing error codes and fixed client sentences.
Keep the two existing topic tools' invalid-input mapping to `NOT_AVAILABLE`.
Add `INVALID_INPUT` for invalid input to the four new tools or a backend submission refusal with status 400.
Use `NOT_AVAILABLE` for valid IDs outside the configured topic scope across all tools.
Add `REQUEST_CONFLICT` for status 409.
Add `COMMAND_INCOMPLETE` for a validated `INCOMPLETE` result.
These codes require fixed client sentences and error-contract tests.

A submission without a reliable response can have executed.
Return `OUTCOME_UNKNOWN` with `isError=true` and no `structuredContent` or invented receipt.
Its `_meta` contains exactly `code`, `message`, `retryable`, and `requestId`.
The request ID equals the validated caller input.
This fourth metadata field applies only to unknown submission outcomes.
Other error metadata keeps exactly the three existing fields.
Every error includes one text content item containing its fixed client sentence.
Success text content renders the same application object as `structuredContent`.
Include no raw backend response or exception text.
Keep `retryable=false` for submission errors in this milestone.
The client must not treat this flag as permission to create another request ID.
Document the process-lifetime limit beside repeated-submission guidance.

Use these fixed sentences for the new submission error behavior:

| Code | Sentence |
|---|---|
| `INVALID_INPUT` | `the tool input is not valid` |
| `REQUEST_CONFLICT` | `the request ID conflicts with an earlier submission` |
| `COMMAND_INCOMPLETE` | `a required backend refused the command` |
| `OUTCOME_UNKNOWN` | `the submission outcome is unknown. Repeat only with the same request ID while the server process remains unchanged.` |

The revised `OUTCOME_UNKNOWN` sentence applies when the new send tool activates that previously unused code.

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
9. Return `NOT_AVAILABLE` for a readable out-of-scope message ID, without exposing its content or identifiers.
10. Lose a submission response and verify no automatic second submission occurs.
11. Preserve a validated 424 command result and its receipt.
12. Reject oversized history without partial output.
13. Preserve numeric IDs above `2^53` exactly.
14. Verify protocol output, bounded requests, shutdown, and diagnostic redaction.
15. Verify exact error field locations for `OUTCOME_UNKNOWN` and `INCOMPLETE`.
16. Preserve existing topic invalid-input behavior and test the new tools' `INVALID_INPUT` behavior.
17. Accept an empty backend map for `ACCEPTED` and return a later status through the status tool.
18. Refuse invalid command IDs before any backend request.
19. Return `OUTCOME_UNKNOWN` for a submission redirect without following it.
20. Verify the adapter rejects oversized text before submission.
21. Verify two adapters using one identity recover the same command for the same request ID.

## Deployment acceptance setup

Start an authenticated memory deployment with the message controller enabled.
Set `app.command.bus=memory`, completion requirement `P,I`, and completion timeout `5s`.
Use the existing credential procedure for the configured agent.
Create a fixture room through `POST /topic/new` using that agent's token.
For an existing room, use `PUT /topic/join/{id}` with the agent token.
Verify permissions through a successful history read and submission before MCP acceptance calls.
The agent has no route to inspect its grants.
MCP adds no room creation, join, or grant tool.
Configure the fixture room in `topicIds` and enable sending.

Use a separate test deployment to produce a deterministic `PENDING` result.
A test-only wrapper holds the message persistence store behind a nonblocking latch.
The real persistence handler runs unchanged and calls the wrapped store.
Keep the other real handlers active and set completion requirement `P,I`.
Set the server completion timeout to `100ms` in that test deployment.
Submit through the real stdio MCP client while the latch remains closed.
Verify a `PENDING` receipt, then release the store gate and poll command status for backend success.
The wrapper must invoke the real store after release.
Release the latch during test cleanup, including after assertion failure.
Add no production control route or handler delay setting.

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
