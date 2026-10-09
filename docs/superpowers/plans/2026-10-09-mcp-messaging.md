# MCP messaging implementation plan

> For agentic workers: use `superpowers:executing-plans` inline. `AGENTS.md` forbids subagent development. Track tasks in FP, without Markdown checkboxes.

**Goal:** let an authenticated agent read messages, submit with a stable request ID, and inspect backend progress through MCP.

**Architecture:** extend the existing standalone adapter through REST. Keep Stage 1 command semantics and existing topic contracts. Add no infrastructure.

**Tech stack:** Kotlin, JDK HTTP client, kotlinx JSON, existing Kotlin MCP SDK, JUnit, Spring test configuration, and the pinned Node MCP client.

**Authority:** `docs/superpowers/specs/2026-10-09-mcp-messaging-design.md`, approved at `5c5029f6`.

**Branch:** `chat-teujorxl-mcp-messaging`, based on `e8d9ed13`.

**Status:** complete. All seven tasks are complete. PR #205 merged as `8965daa0` on 2026-10-09.
The separate REST-profile baseline remains under `CHAT-gsddauhn`.

Evidence: `docs/superpowers/reviews/2026-10-09-mcp-messaging-profile-check.md`.

## Execution rules

Execute the seven tasks in order.
Claim each child issue before changing code.
Record the failing test, implementation, passing test, and commit on that issue.
Stage explicit paths only.
Run reactor builds without `install`, to preserve the shared Maven repository.
Run only one Maven build at a time in this worktree.
Write each build's output to a separate log file outside the repository.
Do not implement import, Kafka, search, pagination, or native compilation here.
Keep ordinary sends bound to the authenticated sender.
Do not investigate or repair unrelated Cassandra failures.

Use this focused command after each adapter change:

```sh
mvn -B -pl chat-mcp -am clean verify
```

For an individual test class, use this command with the class named by its task:

```sh
mvn -B -pl chat-mcp -am clean verify \
  -Dtest=BackendHttpTests -Dsurefire.failIfNoSpecifiedTests=false
```

The pinned client install profile runs `npm ci` when its dependencies are absent.
A missing Node client is a failure, not a reason to skip protocol tests.
Record actual counts instead of predicting test counts.

## Tasks and dependencies

| Task | FP issue | Depends on | Result |
|---|---|---|---|
| 1 | `CHAT-bejjrdse` | none | Bounded history and submission transport |
| 2 | `CHAT-qxssaxpc` | 1 | Validated message and command projections |
| 3 | `CHAT-xunlqacx` | 2 | Messaging service with input and scope checks |
| 4 | `CHAT-aorfnfme` | 3 | Four tools and exact error results |
| 5 | `CHAT-pmuosrab` | 4 | Pinned-client protocol evidence |
| 6 | `CHAT-jtzhmxrs` | 5 | Authenticated deployment evidence |
| 7 | `CHAT-aznpzpak` | 6 | Launch documentation and final gates |

## Spec coverage

| Verification cases | Tasks |
|---|---|
| 1, 2: discovery and optional sending | 4, 5 |
| 3, 4: submit, history, and message read | 2, 3, 5, 6 |
| 5: deterministic pending and later success | 6 |
| 6, 7: duplicate identity and conflict | 3, 5, 6 |
| 8, 9: authorization and returned-topic scope | 3, 5, 6 |
| 10, 19: unknown send and redirects | 1, 4, 5 |
| 11, 15: incomplete result and metadata | 2, 4, 5 |
| 12: oversized history | 1, 5 |
| 13: exact IDs | 2, 5 |
| 14: limits, output, shutdown, and redaction | 1, 4, 5 |
| 16: legacy and new invalid-input behavior | 3, 4, 5 |
| 17: accepted with empty backend map | 2, 4, 5 |
| 18, 20: command ID and text bounds | 3, 5 |
| 21: shared agent request namespace | 6 |

## Task 1: bounded HTTP operations

Issue: `CHAT-bejjrdse`.

Modify these files:

- `chat-mcp/src/main/kotlin/com/demo/chat/mcp/client/BackendHttp.kt`
- `chat-mcp/src/test/kotlin/com/demo/chat/mcp/client/BackendHttpTests.kt`
- `chat-mcp/src/test/kotlin/com/demo/chat/mcp/client/TopicClientTests.kt`
- `chat-mcp/src/test/kotlin/com/demo/chat/mcp/McpAdapterStdioTests.kt`

Create this file:

- `chat-mcp/src/main/kotlin/com/demo/chat/mcp/client/SubmissionUnknownException.kt`

1. Run the unchanged adapter test set and record the baseline.
2. Add transport tests with a loopback `HttpServer` and recorded requests.
3. Verify the new tests fail before implementing the operations.
4. Add this transport contract beside the existing `get` operation:

```kotlin
data class BackendResponse(val status: Int, val body: String)

interface BackendHttp : AutoCloseable {
    fun get(target: URI, credential: String): String
    fun history(target: URI, credential: String): String
    fun submit(
        target: URI,
        credential: String,
        requestId: String,
        text: String,
    ): BackendResponse
}

class SubmissionUnknownException(
    val requestId: String,
    val status: Int? = null,
) : RuntimeException("the submission outcome is unknown")
```

Keep `get` as the existing JSON read operation.
History sends `Accept: application/x-ndjson` and requires status 200.
Submit sends `Accept: application/json`, `Content-Type: text/plain`, and `Idempotency-Key`.
Use `BodyPublishers.ofString(text, UTF_8)` without trimming the text.

Use the existing semaphore and deadline across permit acquisition, connection, headers, and body reading.
Reuse the bounded body reader and watchdog.
Close every response body, including refused responses.
Cancel each watchdog task in `finally`.
Release the permit in `finally`.
Keep the configured-origin check before every request.
Keep read redirects unchanged.
Never follow or retry a submission in adapter code.

Return bounded bodies for statuses 201, 202, and 424 to the command decoder.
For 400, 409, 401, 403, and 404, close the response and return its status with an empty body.
Classify those refusals before decoding a response body.
Treat submission redirects, unexpected statuses, and lost responses as potentially executed.
Raise `SubmissionUnknownException` with the validated request ID for those outcomes.
Acquire the permit before entering the submission's unknown-outcome guard.
Do not turn a pre-dispatch permit refusal into an unknown submission.
Do not expose response bodies through exception messages or diagnostics.
Distinguish a local request-limit refusal from a response-body limit reached after dispatch.
The latter can hide an accepted command and must produce an unknown outcome.

The two existing fake implementations must implement the new methods.
Their `history` and `submit` methods must raise `AssertionError` when an old topic test calls them.
That assertion detects accidental routing changes without inventing canned submission behavior.

Test the following request and response matrix:

| Case | Required observation |
|---|---|
| History | NDJSON accept header, one GET, exact body |
| Submit | One POST, exact request ID, exact UTF-8 text, bearer header |
| 201, 202, 424 | Status and bounded body preserved |
| 301, 302, 307, 308 | No request reaches the redirect target |
| Body exceeds 1 MiB | Stream closes and permit becomes available |
| Body stalls | The shared deadline closes the stream |
| Connection closes after POST | No adapter resend |
| Wrong origin | No credential or request reaches that origin |
| Four occupied permits | Fifth request obeys the shared limit and deadline |
| Failure, then another request | No leaked permit or watchdog task |

5. Run `BackendHttpTests`, then the complete adapter test set.
6. Temporarily follow a submission redirect and verify the redirect test fails.
7. Restore the transport and verify the test passes.
8. Commit the five named files with the child issue ID.

## Task 2: response decoding and projection

Issue: `CHAT-qxssaxpc`.

Create these files:

- `chat-mcp/src/main/kotlin/com/demo/chat/mcp/client/MessageEnvelope.kt`
- `chat-mcp/src/main/kotlin/com/demo/chat/mcp/client/CommandEnvelope.kt`
- `chat-mcp/src/test/kotlin/com/demo/chat/mcp/client/MessageEnvelopeTests.kt`
- `chat-mcp/src/test/kotlin/com/demo/chat/mcp/client/CommandEnvelopeTests.kt`
- `chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/composite/MessagingRestFixtures.kt`

Modify these existing REST tests to verify the source fixtures:

- `chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/composite/MessageSubmitRestTests.kt`
- `chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/composite/ListMessagesRestTests.kt`
- `chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/composite/MessageRestAccessTests.kt`

1. Add independent fixture tests for message, NDJSON history, receipt, send result, and command status.
2. Include Long and UUID fixtures, null recovery times, and all declared backend states.
3. Verify failures before adding the decoders.
4. Implement these entry points with `JsonObject` projections:

```kotlin
fun decodeMessage(body: String, keyType: KeyType): JsonObject
fun decodeHistory(body: String, keyType: KeyType): List<JsonObject>
fun decodeSend(response: BackendResponse, keyType: KeyType): JsonObject
fun decodeStatus(body: String, keyType: KeyType, commandId: String): JsonObject
```

Each decoder validates the source shape before constructing a new output object.
`decodeSend` classifies confirmed HTTP refusals before parsing JSON.
It raises `ClientException` with status for those refusals, so Task 4 can map them without backend text.
Use the source annotations in `Message.kt`, `Keys.kt`, and `CommandTypes.kt` to locate wrappers and fields.
Verify those shapes through the named REST tests before treating fixtures as authoritative.
Do not introduce a permissive search for arbitrarily nested fields.
Do not add a production dependency from `chat-mcp` to `chat-core`.

Use the existing canonical ID rules with the primitive's exact text.
Never convert an ID through `Double`, `Float`, or JavaScript `Number`.
Normalize message timestamps to ISO-8601 UTC text after validating the actual REST timestamp representation.
Reject empty keys, missing roots, invalid timestamps, wrong scalar types, and invalid enum values.
Omit `owner` and raw backend `reason` from the projected status.
Preserve the numeric `version` without narrowing it to `Int`.
Verify the returned command ID matches the requested command ID.

Construct only these application fields:

```text
Key: id, root
Message: messageKey, senderId, topicId, text, timestamp
Receipt: commandId, messageKey
Backend: state, attempts, nextRecoveryAt
Send: receipt, outcome, backends
Status: commandId, requestId, receipt, backends, version
```

Require nonnegative integral attempts and version values.
Allow zero backend entries.
Do not require P, I, V, or U to appear in every response.
Validate the outcome against its HTTP status.
Accept `COMPLETED` only with 201, `ACCEPTED` or `PENDING` with 202, and `INCOMPLETE` with 424.

Parse NDJSON one nonblank record at a time after the entire bounded body arrives.
Retain server order and validate every record before exposing any result.
Fail the whole history on malformed records.
Do not return a valid prefix or silently shorten it.

Add these concrete assertions around a decoded send fixture:

```kotlin
val accepted = decodeSend(BackendResponse(202, acceptedBody), KeyType.LONG)
assertEquals("ACCEPTED", accepted.getValue("outcome").jsonPrimitive.content)
assertTrue(accepted.getValue("backends").jsonObject.isEmpty())
assertEquals(
    "1554361143634427905",
    accepted.getValue("receipt").jsonObject
        .getValue("messageKey").jsonObject.getValue("id").jsonPrimitive.content,
)
```

Store one fixture file per response shape under `chat-mcp/src/test/resources/messaging/`.
Use separate Long and UUID files for messages, history, receipts, send results, and command status.
Include separate files for each send outcome and each status shape used by the tests.
Read those files from the adapter test classpath instead of declaring fixture bodies in test classes.
Make each named REST test read the same fixture file and compare it with its real response.
Use `MessagingRestFixtures` to bind the production controller for each key type with the slice's configured Jackson codec.
These fixture comparisons check serialization, while existing slice tests retain their authorization checks.
Locate the repository root by walking parent directories until `chat-mcp/src/test/resources/messaging/` exists.
Resolve fixtures beneath that directory without copying them into another module.
Compare complete parsed JSON structures, including wrappers, scalar types, timestamps, and null fields.
Replace only generated identity, timestamp, and counter values with the corresponding fixture values before comparison.
Do not remove fields or change their scalar types during that replacement.
For history, compare each complete NDJSON record with its fixture record.
Add explicit UUID REST coverage alongside Long coverage in the named REST tests.
A changed server shape must fail a shared-fixture comparison for either key type.

5. Run the two decoder test classes.
6. Run the named REST classes with this command:

```sh
mvn -B -pl chat-webflux -am clean verify \
  -Dtest=MessageSubmitRestTests,ListMessagesRestTests,MessageRestAccessTests \
  -Dsurefire.failIfNoSpecifiedTests=false
```

7. Remove one required-field check and verify its fixture test fails.
8. Restore the check and run both focused sets.
9. Commit the eight named files and the shared fixtures with the child issue ID.

## Task 3: client, input validation, and room scope

Issue: `CHAT-xunlqacx`.

Create these files:

- `chat-mcp/src/main/kotlin/com/demo/chat/mcp/client/MessagingClient.kt`
- `chat-mcp/src/main/kotlin/com/demo/chat/mcp/tool/MessagingToolService.kt`
- `chat-mcp/src/test/kotlin/com/demo/chat/mcp/tool/MessagingToolServiceTests.kt`
- `chat-mcp/src/test/kotlin/com/demo/chat/mcp/tool/MessagingTestFixtures.kt`

1. Add tests for each validation and scope rule in the matrix below.
2. Use a loopback HTTP fixture with recorded method, path, headers, and body.
3. Verify the tests fail before adding the service.
4. Add the following service surface:

```kotlin
class MessagingToolService(
    private val config: AdapterConfig,
    private val client: MessagingClient,
) {
    fun listMessages(topicId: String): JsonObject
    fun getMessage(messageId: String): JsonObject
    fun sendMessage(topicId: String, text: String, requestId: String): JsonObject
    fun commandStatus(commandId: String): JsonObject
}
```

The block specifies method signatures, not a class to compile without method bodies.
Add `MessagingInputException` and `MessagingUnavailableException` beside the service.
Both extend `RuntimeException` and carry no input value or backend body.
Task 4 maps them to fixed client sentences.
Use this client constructor and method contract:

```text
MessagingClient(config: AdapterConfig, http: BackendHttp)
readHistory(topicId: String): List<JsonObject>
readMessage(messageId: String): JsonObject
submit(topicId: String, text: String, requestId: String): JsonObject
status(commandId: String): JsonObject
```

The methods return validated application objects from Task 2.
`listMessages` wraps the list as `messages`.
`getMessage` wraps the message as `message`.
Send and status return their application objects directly.

The client reads the credential file before each HTTP request.
Reuse `usableCredential` for header safety.
Build paths from the validated configured origin.
Encode a command ID as one path segment, even when it contains `/`, `?`, `#`, or `%`.
Never resolve it as a relative URI.
Use the caller's request ID unchanged.
Accept no sender, completion override, or publication flag.
`readHistory` calls `history` and `decodeHistory`.
`readMessage` calls `get` and `decodeMessage`.
`submit` calls the transport's `submit` and then `decodeSend`.
`status` calls `get` and `decodeStatus` with the requested command ID.
Convert decoder failures after a submission to `SubmissionUnknownException` unless they classify a confirmed HTTP refusal.

Use these validators for visible ASCII and text:

```kotlin
internal fun validVisibleId(value: String): Boolean =
    value.length in 1..128 && value.all { it in '!'..'~' }

internal fun validMessageText(value: String): Boolean =
    value.isNotBlank() && value.toByteArray(Charsets.UTF_8).size in 1..16_384
```

Treat invalid canonical object IDs as invalid input for the new tools.
Treat a valid topic ID outside `topicIds` as unavailable.
Apply the allowlist before sending a topic request.
For `getMessage`, check the returned `topicId` before constructing the result.
Apply the same returned-topic check to every history record.
An out-of-scope result must expose no content or identifiers through an error.
Do not assume Message GET performs a server room check.
Command status relies on the server owner check and has no extra topic-scope promise.

| Input or result | Expected result |
|---|---|
| Empty, spaced, control-character, or 129-character request ID | Invalid input, no HTTP request |
| Empty, spaced, control-character, or 129-character command ID | Invalid input, no HTTP request |
| Canonical command ID containing URI delimiters | Exactly one encoded path segment |
| Blank text | Invalid input, no HTTP request |
| 16,384 UTF-8 bytes | One exact submission |
| 16,385 UTF-8 bytes | Invalid input, no HTTP request |
| Valid topic outside the allowlist | Unavailable, no HTTP request |
| Readable message in another topic | Unavailable, no projected message |
| Mixed-scope history | Unavailable, no projected prefix |
| Same request ID on a second client | Same ID in the HTTP header |

5. Run `MessagingToolServiceTests`, then all adapter tests.
6. Remove the returned-topic check and verify the out-of-scope test fails.
7. Restore the check and verify the test passes.
8. Commit the four named files with the child issue ID.

## Task 4: schemas, registration, and error results

Issue: `CHAT-aorfnfme`.

Create these files:

- `chat-mcp/src/main/kotlin/com/demo/chat/mcp/tool/MessagingToolRegistration.kt`
- `chat-mcp/src/main/kotlin/com/demo/chat/mcp/tool/MessagingSchemas.kt`
- `chat-mcp/src/test/kotlin/com/demo/chat/mcp/tool/MessagingAnswerContractTests.kt`

Modify these files:

- `chat-mcp/src/main/kotlin/com/demo/chat/mcp/McpAdapterServer.kt`
- `chat-mcp/src/main/kotlin/com/demo/chat/mcp/error/ToolError.kt`
- `chat-mcp/src/main/kotlin/com/demo/chat/mcp/tool/TopicToolRegistration.kt`
- `chat-mcp/src/test/kotlin/com/demo/chat/mcp/error/ToolErrorTests.kt`
- `chat-mcp/src/test/kotlin/com/demo/chat/mcp/McpAdapterToolStdioTests.kt`
- `chat-mcp/src/test/kotlin/com/demo/chat/mcp/McpAdapterServerTests.kt`
- `chat-mcp/src/test/kotlin/com/demo/chat/mcp/McpAdapterHarnessTests.kt`

1. Add exact-shape tests for each result and failure before implementing registration.
2. Verify those tests fail for the missing messaging tools.
3. Register these names and required argument sets:

```text
chat_list_messages: topicId
chat_get_message: messageId
chat_send_message: topicId, text, requestId
chat_get_command_status: commandId
```

Reject extra arguments, missing arguments, nulls, and non-string values at the new tool boundary.
Use `INVALID_INPUT` for these new-tool refusals.
Preserve `NOT_AVAILABLE` and existing sentences for invalid input to the two original topic tools.
Keep output contracts for the two topic tools unchanged.
Their discovery tests must now permit three additional read tools.
Update the pinned harness's discovery assertion in this task so the complete adapter test set remains valid.
Share the existing diagnostic function and call counter through internal visibility without changing legacy behavior.
Expect five tools with sending disabled and six with sending enabled.

Register send only when `enableSend=true`.
Keep `enableSearch` from registering search tools.
Use the annotations specified by the approved spec.
The existing SDK cannot express `additionalProperties` at the input schema root.
Enforce the argument set in code and test that refusal over stdio.
Do not add a handwritten protocol layer to change the SDK schema type.

Use a separate messaging answer function so legacy answer behavior remains stable.
Keep one shared transport owned by the adapter.
Do not construct another HTTP client per tool.
Preserve coroutine cancellation rather than converting it into a tool error.
Catch `SubmissionUnknownException` before general client errors and expose only its validated request ID and optional diagnostic status.
Map `MessagingInputException` to `INVALID_INPUT` and `MessagingUnavailableException` to `NOT_AVAILABLE`.
Do not use their exception messages as client text.

Construct an incomplete response with this shape:

```kotlin
CallToolResult(
    content = listOf(TextContent("a required backend refused the command")),
    structuredContent = sendResult,
    isError = true,
    meta = buildJsonObject {
        put("code", "COMMAND_INCOMPLETE")
        put("message", "a required backend refused the command")
        put("retryable", false)
    },
)
```

Construct an unknown submission response with this shape:

```kotlin
val sentence = "the submission outcome is unknown. " +
    "Repeat only with the same request ID while the server process remains unchanged."
CallToolResult(
    content = listOf(TextContent(sentence)),
    isError = true,
    meta = buildJsonObject {
        put("code", "OUTCOME_UNKNOWN")
        put("message", sentence)
        put("retryable", false)
        put("requestId", requestId)
    },
)
```

`sendResult` is the validated object returned by Task 3.
`requestId` is the validated caller value, not an exception field or generated ID.
Other errors have exactly the existing three metadata fields.
Successful results have no application metadata and render their application object in both output forms.

Add the three new error codes and fixed sentences from the spec to `ToolError`.
Update its KDoc to describe `requestId` for an unknown submission.
Remove the stale claim that a failed send carries an `outcome` metadata field.
Do not add `requestId` to every error or to `ToolError.toMeta` unconditionally.

| Condition | Classification |
|---|---|
| New-tool validation or HTTP 400 | `INVALID_INPUT` |
| HTTP 409 | `REQUEST_CONFLICT` |
| HTTP 401 | `AUTHENTICATION_REQUIRED` |
| HTTP 403 or 404 | `NOT_AVAILABLE` |
| Validated 424 result | `COMMAND_INCOMPLETE`, with structured result |
| 3xx or unexpected submission status | `OUTCOME_UNKNOWN` |
| Lost, oversized, or malformed response after submission | `OUTCOME_UNKNOWN` |
| Adapter request limit before dispatch | `LIMIT_EXCEEDED` |
| Read transport or backend error | Existing read classification |

Keep every submission error's `retryable` value false.
Never echo raw backend reasons, exception text, response bodies, or credentials.
The unknown request ID belongs in protocol metadata only, not in diagnostics.
Each messaging call writes one diagnostic line with the existing prefix and field order.

4. Run the answer, error, server, and stdio contract tests.
5. Add an extra metadata field temporarily and verify an exact-shape test fails.
6. Restore the metadata and run all adapter tests.
7. Commit the ten named files and this plan correction with the child issue ID.

## Task 5: pinned-client protocol evidence

Issue: `CHAT-pmuosrab`.

Modify these files:

- `chat-mcp/src/test/client/harness.mjs`
- `chat-mcp/src/test/kotlin/com/demo/chat/mcp/McpAdapterHarnessTests.kt`
- `chat-mcp/src/test/kotlin/com/demo/chat/mcp/tool/MessagingTestFixtures.kt`

Create this file:

- `chat-mcp/src/test/kotlin/com/demo/chat/mcp/McpMessagingHarnessTests.kt`

1. Add failing harness tests for the messaging scenarios below.
2. Add an optional `--calls-file` mode to the existing Node harness.
3. Keep its existing flags and topic scenario working.

The call file is a JSON array of objects with `name` and `arguments`.
Validate its shape in the harness before starting requests.
Execute calls sequentially through the pinned `Client.callTool` method.
Record tool discovery, results, raw protocol lines, stderr, and bounded exit in the transcript.
Preserve the existing transcript's `calls` entries and field names.
Use a 35-second call bound in messaging mode, above the adapter's 30-second deadline.
Keep the original topic mode's 15-second call bound.
Bound the parent process wait to `10 + 35 * callCount` seconds in messaging mode.
Terminate and collect child processes after that bound expires.
Do not parse IDs through JavaScript `Number`.
The Kotlin test constructs a second call file when an earlier result supplies a message or command ID.
This avoids a new expression language for references between calls.

The core call loop is:

```javascript
for (const step of script) {
  const answer = await client.callTool(step, undefined, { timeout: 35000 });
  calls.push({
    name: step.name,
    arguments: step.arguments,
    isError: answer.isError === true,
    structuredContent: answer.structuredContent ?? null,
    meta: answer._meta ?? null,
    text: (answer.content ?? [])
      .filter((part) => part.type === "text")
      .map((part) => part.text)
      .join(""),
  });
}
```

Keep protocol output separate from the transcript that the harness prints.
Use temporary files for credentials, config, calls, and child output.
Close stdin and wait for the existing five-second exit bound.
Delete credential files in cleanup, including after test failure.
Keep the existing pinned package and lock file versions.

Test each approved tool with a real adapter child process and loopback HTTP fixture.
The fake backend may control responses, but the MCP client and adapter are real.
This proves protocol behavior, not deployed authorization.

| Scenario | Required proof |
|---|---|
| Sending disabled and enabled | Five or six discovered tools |
| Message and history reads | Correct projection, exact IDs, no partial history |
| Completed, pending, accepted | Normal structured result, no metadata |
| Incomplete | Error flag, receipt retained, exactly three metadata fields |
| Unknown response or redirect | Exactly four metadata fields, no receipt, no resend |
| Conflict | Fixed sentence and non-retryable conflict code |
| Legacy bad input | Original topic-tool behavior unchanged |
| New bad input | `INVALID_INPUT`, no backend call |
| Out-of-scope message | No hidden text or identifiers in any protocol result or diagnostic |
| Oversized history | `LIMIT_EXCEEDED`, no valid prefix |
| Sensitive backend error | No secret, content, URL, or raw reason escapes |
| EOF | Child exits within the bound |

4. Run `McpMessagingHarnessTests` and `McpAdapterHarnessTests`.
5. Remove the send opt-in guard and verify the disabled-send test fails.
6. Restore the guard and run all adapter tests.
7. Commit the four named files and this plan correction with the child issue ID.

## Task 6: authenticated deployment and deterministic pending

Issue: `CHAT-jtzhmxrs`.

Modify these files:

- `chat-deploy-memory/pom.xml`
- `shell-scripts/agent-http-gate.sh`

Create these files:

- `chat-deploy-memory/src/test/kotlin/com/demo/chat/test/deploy/memory/McpMessagingDeploymentTests.kt`
- `chat-deploy-memory/src/test/kotlin/com/demo/chat/test/deploy/memory/McpMessageStoreGate.kt`

1. Add a test-scoped `chat-mcp` dependency to `chat-deploy-memory`.
2. Run the module's complete test set under both transport profiles before writing the deployment test.
3. Check that both runs include passing Maven enforcer checks.
4. Add the deployment tests before changing any production behavior.
5. Run with `-Pexpose-webflux` and verify the missing fixture or behavior fails explicitly.

The dependency changes every test's classpath in this module.
Run these commands sequentially without a test filter:

```sh
mvn -B -pl chat-deploy-memory -am -Pexpose-rsocket clean verify > /tmp/chat-teujorxl-task6-rsocket.log 2>&1
mvn -B -pl chat-deploy-memory -am -Pexpose-webflux clean verify > /tmp/chat-teujorxl-task6-webflux.log 2>&1
```

Record each exit code, test count, skipped count, and enforcer result before creating the deployment test.

Add this dependency without changing managed library versions:

```xml
<dependency>
    <groupId>com.demo</groupId>
    <artifactId>chat-mcp</artifactId>
    <version>0.0.1</version>
    <scope>test</scope>
</dependency>
```

Use the same authenticated REST launch properties as `RestAgentSelectionTests`.
Use `AgentTestTokens.createKey` and `AgentTestTokens.mint` from that module.
Configure both Agent and Claude client mappings for owner-isolation tests.
Keep memory key, persistence, and Pubsub providers with Lucene indexing.
Enable the message controller, composite service, and `app.command.bus=memory`.
Use a random HTTP port and the configured issuer key.
Use `@DirtiesContext` and release every child process and temporary credential file.
The test must fail when Node or the pinned client is absent.

Resolve the client harness from the repository root's `chat-mcp/src/test/client/harness.mjs`.
Start the adapter with the deployment test's Surefire classpath and `McpAdapterMainKt`.
The test-scoped dependency puts the real adapter classes and SDK on that classpath.
Do not copy the adapter code into the deployment module.

Room setup uses real HTTP requests with the agent token:

```text
POST /topic/new
Content-Type: application/json
Body: {"type":"ByNameRequest","name":"mcpmessagingroom"}

PUT /topic/join/{roomId}

GET /message/list/{roomId}
Accept: application/x-ndjson

POST /message/submit/{roomId}
Content-Type: text/plain
Idempotency-Key: setup:<unique-test-id>
Body: setup probe
```

Create a new room or join an existing fixture room through the named route.
Verify setup through a successful history read and submission.
Poll the setup command until P and I succeed before testing history content.
Do not attempt to inspect grant rows through an agent route.
Assert against returned IDs and distinguish the setup probe from the message under test.
Do not log the token or real credentials.

### Persistence seam

The real command handlers are not Spring beans.
`MessageCommandConfiguration.handlers` constructs them privately.
Do not replace that configuration or introduce a production delay property.

`MemoryPersistenceServices.messagePersistence` is a named Spring bean.
Its configuration implements `PersistenceServiceBeans` and returns that intercepted bean.
Wrap the bean named `messagePersistence` with a test-only `BeanPostProcessor`.
Delegate every operation except `add` to the real store.
The unchanged real P handler will call the wrapped store.

Use this nonblocking gate and wrapper in `McpMessageStoreGate.kt`:

```kotlin
class MessageStoreGate {
    private val release = AtomicReference<Sinks.One<Void>?>(null)
    private val entered = AtomicReference(CountDownLatch(1))

    fun arm() {
        entered.set(CountDownLatch(1))
        check(release.compareAndSet(null, Sinks.one<Void>()))
    }

    fun beforeAdd(): Mono<Void> = Mono.defer {
        val current = release.get()
        if (current == null) Mono.empty()
        else {
            entered.get().countDown()
            current.asMono()
        }
    }

    fun awaitEntry(): Boolean = entered.get().await(10, TimeUnit.SECONDS)

    fun open() {
        release.getAndSet(null)?.tryEmitEmpty()
    }
}

@TestConfiguration(proxyBeanMethods = false)
class McpMessageStoreGateConfiguration {
    companion object {
        @Bean
        @JvmStatic
        fun messageStoreGate(): MessageStoreGate = MessageStoreGate()

        @Bean
        @JvmStatic
        fun gatedMessageStore(gate: MessageStoreGate): BeanPostProcessor =
            object : BeanPostProcessor {
                @Suppress("UNCHECKED_CAST")
                override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
                    if (beanName != "messagePersistence") return bean
                    val real = bean as MessagePersistence<Long, String>
                    return object : MessagePersistence<Long, String> by real {
                        override fun add(ent: Message<Long, String>): Mono<Void> =
                            gate.beforeAdd().then(Mono.defer { real.add(ent) })
                    }
                }
            }
    }
}
```

Import this configuration only into the dedicated test context.
The child uses the deployment test classpath, which includes Logback.
Give that child a temporary Logback configuration with the root level `OFF`.
The normal adapter classpath has no logging provider.
This test setting preserves protocol-only stdout without changing production logging.
Use `MessagePersistence`, `Message`, Reactor, concurrency, and Spring test imports shown by the declared types.
Obtain the gate from the test context.
Set `app.command.completion.timeout=100ms` for this context and requirement `P,I`.
Keep the gate open during startup and setup probes.
Use unique rooms and request IDs for every test.

For deterministic pending, perform this sequence:

1. Complete room setup and its behavioral probes.
2. Arm the gate before starting the MCP submission.
3. Submit through the Node client and real adapter process.
4. Assert the returned outcome is `PENDING` and retain its receipt.
5. Assert `awaitEntry()` succeeds, proving the real P handler reached the wrapped store.
6. Open the gate and poll the command through MCP until P and I succeed.
7. Read the stored message through both MCP read tools.
8. Open the gate again in `finally` and `@AfterEach` cleanup.

The attempt watchdog changes a held P attempt to `UNCERTAIN` after 30 seconds.
Node and adapter startup consume part of that interval.
Open the gate promptly after observing `PENDING`.
Allow a backend state of `UNCERTAIN` before release, followed by `SUCCEEDED` after release.
Do not require P to remain in its initial state while the gate holds it.

Do not use `Thread.sleep` to force pending.
Use a bounded polling deadline for eventual status and history assertions.
Do not run this class's tests concurrently because they share one gate.

Provide at least these six deployment tests:

| Test | Required evidence |
|---|---|
| Submit and read | Real JWT, MCP, REST, handlers, persistence, and index |
| Join existing room | Join route followed by successful history and submit probes |
| Pending then success | Store gate, real handler, pending receipt, later successful P and I |
| Repeat and conflict | Two adapter processes with one identity return one command, then changed content conflicts |
| Refused room and message scope | History and send denied by server, readable hidden message blocked by adapter |
| Another owner's status | A second configured agent receives the same unavailable error as a missing command |

The hidden-message test must first prove a raw message-by-ID GET succeeds under the shipped policy.
Then prove MCP returns `NOT_AVAILABLE` without its content or identifiers.
This distinguishes adapter scope evidence from server authorization evidence.
Create the denied room with the second agent.
Include that room in the first agent adapter's `topicIds` for the history and send refusal tests.
Otherwise, the adapter refuses those calls before the server checks access.

6. Run the deployment class explicitly:

```sh
mvn -B -pl chat-deploy-memory -am -Pexpose-webflux clean verify \
  -Dtest=McpMessagingDeploymentTests -Dsurefire.failIfNoSpecifiedTests=false
```

7. Add the class to the existing `expose-webflux` run in `agent-http-gate.sh`.
8. Require at least six tests, zero failures, zero errors, and zero skipped tests for the new class.
9. Temporarily omit the gate wrapper and verify the pending test fails.
10. Restore it and run the deployment class and `shell-scripts/agent-http-gate.sh`.
11. Commit the four named files with the child issue ID.

## Task 7: operator documentation and final verification

Issue: `CHAT-aznpzpak`.

Modify these documentation files:

- `docs/MCP-ADAPTER.md`
- `docs/MCP-CLIENT-COMPONENTS.md`
- `docs/MCP-CREDENTIAL-ISSUANCE.md`
- `docs/MCP-REAL-DEPLOYMENT-ACCEPTANCE.md`
- `docs/superpowers/specs/2026-09-27-demo-chat-mcp-design.md`
- `forward-register.md`

Update `docs/BUILD-HEALTH.md` only with measurements from the final gates.
Final review added a changed-room conflict assertion to the Task 6 deployment test at `e2c77a7d`.
Task 7 also places the transport KDoc on `BackendHttp` and gives `BackendResponse` its own description.
Those comment changes alter no runtime behavior.
Final review found that Task 4 declared `idempotentHint=true` for sending.
The approved spec requires `false`, so Task 7 corrects the annotation and its test.

1. Check drift bindings before editing each document.
2. Document five read tools and optional sending, with exact argument and result examples.
3. Replace the old send contract with caller-owned request IDs and Stage 1 command outcomes.
4. Document the shared agent namespace and process-lifetime retry limit.
5. Document the adapter-only room restriction for message-by-ID reads.
6. Document the 16,384-byte adapter limit and explicit oversized-history failure.
7. Name `PUT /topic/join/{id}` and the two behavioral setup probes.
8. Require the documented server completion timeout to be `5s`, below the adapter's `30s` deadline.
9. Explain that transport deadlines can still produce an unknown result.
10. Record the new acceptance evidence without replacing earlier historical measurements.
11. Update `ToolError` documentation references to the exact metadata shapes.
12. Run these final gates:

```sh
shell-scripts/build-partial.sh --modules chat-mcp --mode unit
shell-scripts/agent-http-gate.sh
shell-scripts/build-health.sh
shell-scripts/build-health.sh --ci
drift check
git diff --check master...HEAD
```

Use an empty `DOCKER_CONFIG` only if the existing credential helper prevents the container gate.
Record that environment difference beside the result.
Report a known Cassandra failure separately and preserve the owner's no-repair boundary.
Do not claim a full gate passed if it failed or skipped required acceptance tests.
If the final gate reaches Cassandra and fails, report the exact class and failure before any further Cassandra measurement.

13. Review the complete diff against all 21 spec cases.
14. Verify no production test gate, sender override, implicit retry, or new infrastructure exists.
15. Verify the normal adapter runtime still has no Spring or chat-core dependency.
16. Review prose before refreshing any drift provenance.
17. Commit the named documentation files and measured build-health update.
18. Record the tested source revision, documentation revision, counts, and remaining limits on the parent issue.

Do not push, open a PR, or merge during plan execution without the owner's instruction.
Request plan review before Task 1.
Execution will remain inline after approval.
