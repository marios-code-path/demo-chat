# Portal Register Writes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The portal socket and the MCP adapter open and post register entries.

**Architecture:** The resident chat-shell portal grows two write operations. A new `PortalWriteService` applies the pointer, validation, readback, and confirmation rules of `node-9/bin/register`. The MCP adapter adds two tools over the same socket. Callers keep no pointer logic and the portal keeps its fixed register and login identity.

**Tech Stack:** Kotlin, Spring Shell, kotlinx.serialization, Jackson, JUnit 5, AssertJ.

**Spec:** No separate design document. The approved scope is this conversation. The rules come from `node-9/bin/register`.

## Global Constraints

- The portal request frame stays at most 32768 bytes, including the newline.
- The portal reply frame stays at most 4194304 bytes, including the newline.
- The register name rule: lowercase letters, digits, and dashes, starts with a letter or digit, at most 37 characters.
- The key rule: nonempty, at most 64 characters, no `|`, no backslash, no control character.
- The short name rule: `^[a-z0-9][a-z0-9-]*$`.
- The title rule: nonempty, no `|`, no control character.
- The title limit: 256 characters.
- The request id rule: `^[A-Za-z0-9._:-]+$`, and `_` is refused.
- The request id limit: 128 characters.
- The entry text limit: 3072 UTF-8 bytes.
- The MCP tool derives an ID when `requestId` is absent.
- The derived ID is `auto-` plus 32 SHA-256 hex characters.
- The hash input is the UTF-8 key, one `0x00` byte, and the UTF-8 text.
- An explicit `requestId` overrides the derived ID.
- The confirmation window: 10000 ms.
- The wrapper deadline: 30000 ms.
- A refused or uncertain write returns an error and no success line.
- The three new error codes are `WRITE_FAILED`, `NOT_OPEN`, and `UNCONFIRMED`.
- Register write tools need `registerName`. An id gives no topic prefix.

## Review Focus

- A write during a concurrent open of the same key. Expect one pointer and state `exists`.
- A retry of a post with the same request id. Expect no second entry.
- A denied register index. Expect a join and one retry, then a typed error.
- An entry text at 3072 bytes and at 3073 bytes. Expect accept and refuse.
- A post that the confirmation read never shows. Expect `UNCONFIRMED` with status 75.

---

### Task 1: Protocol Carries Write Operations

**Files:**
- Modify: `chat-shell/src/main/kotlin/com/demo/chat/shell/portal/PortalProtocol.kt:15-122`
- Test: `chat-shell/src/test/kotlin/com/demo/chat/test/portal/PortalProtocolTests.kt`

**Interfaces:**
- Produces: `PortalRequest(operation, index, topic, key, short, title, text, requestId)`. Each new field is nullable with default `null`.
- Produces: `const val OPEN_REGISTER_ENTRY: String = "open-register-entry"`.
- Produces: `const val POST_REGISTER_ENTRY: String = "post-register-entry"`.
- Produces: `PortalErrorCode.WRITE_FAILED`, `PortalErrorCode.NOT_OPEN`, `PortalErrorCode.UNCONFIRMED`.

- [ ] **Step 1: Write the failing tests**

Add these tests to `PortalProtocolTests`.

```kotlin
@Test
fun `an open request carries key short and title`() {
    val frame = """{"operation":"open-register-entry","index":"register-index","key":"CHAT-a","short":"snap","title":"Snapshot"}""" + "\n"
    val request = protocol.readRequest(frame.byteInputStream())
    assertThat(request.key).isEqualTo("CHAT-a")
    assertThat(request.short).isEqualTo("snap")
    assertThat(request.title).isEqualTo("Snapshot")
}

@Test
fun `a read request refuses write fields`() {
    val frame = """{"operation":"read-register","index":"register-index","key":"CHAT-a"}""" + "\n"
    assertThatThrownBy { protocol.readRequest(frame.byteInputStream()) }
        .isInstanceOf(PortalProtocolException::class.java)
}

@Test
fun `an open request needs exactly its three fields`() {
    val frame = """{"operation":"open-register-entry","index":"register-index","key":"CHAT-a","short":"snap"}""" + "\n"
    assertThatThrownBy { protocol.readRequest(frame.byteInputStream()) }
        .isInstanceOf(PortalProtocolException::class.java)
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :chat-shell:test --tests '*PortalProtocolTests*'`
Expected: FAIL. The `key` property does not resolve.

- [ ] **Step 3: Implement the protocol change**

Add the nullable fields to `PortalRequest`. Add both operation constants. Add the three error codes.

Replace the single-operation check with a per-operation field set.

```kotlin
private val REQUEST_FIELDS =
    setOf("operation", "index", "topic", "key", "short", "title", "text", "requestId")

private val READ_FIELDS = setOf("operation", "index", "topic")
private val OPEN_FIELDS = setOf("operation", "index", "key", "short", "title")
private val POST_FIELDS = setOf("operation", "index", "key", "text", "requestId")
```

Choose the allowed set from `operation`. Refuse any other field name with `INVALID_INPUT`. Refuse a missing required field with `INVALID_INPUT`. Read each write field as a textual value.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :chat-shell:test --tests '*PortalProtocolTests*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add chat-shell/src/main/kotlin/com/demo/chat/shell/portal/PortalProtocol.kt chat-shell/src/test/kotlin/com/demo/chat/test/portal/PortalProtocolTests.kt
git commit -m "feat(portal): carry open and post operations on the request frame"
```

---

### Task 2: Write Argument Rules

**Files:**
- Create: `chat-shell/src/main/kotlin/com/demo/chat/shell/portal/PortalWriteRules.kt`
- Test: `chat-shell/src/test/kotlin/com/demo/chat/test/portal/PortalWriteRulesTests.kt`

**Interfaces:**
- Produces: `object PortalWriteRules` with `requireKey`, `requireShort`, `requireTitle`, `requireRequestId`, `requireText`.
- Each function takes `String?` and answers a valid `String`. Each throws `PortalProtocolException(INVALID_INPUT)`.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test
fun `a key refuses the pipe and the backslash`() {
    assertThatThrownBy { PortalWriteRules.requireKey("a|b") }.isInstanceOf(PortalProtocolException::class.java)
    assertThatThrownBy { PortalWriteRules.requireKey("a\\b") }.isInstanceOf(PortalProtocolException::class.java)
}

@Test
fun `a short name refuses an uppercase letter`() {
    assertThatThrownBy { PortalWriteRules.requireShort("Snap") }.isInstanceOf(PortalProtocolException::class.java)
}

@Test
fun `a text of 3072 bytes is accepted and 3073 is refused`() {
    assertThat(PortalWriteRules.requireText("a".repeat(3072))).hasSize(3072)
    assertThatThrownBy { PortalWriteRules.requireText("a".repeat(3073)) }
        .isInstanceOf(PortalProtocolException::class.java)
}

@Test
fun `the request id underscore is refused`() {
    assertThatThrownBy { PortalWriteRules.requireRequestId("_") }.isInstanceOf(PortalProtocolException::class.java)
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :chat-shell:test --tests '*PortalWriteRulesTests*'`
Expected: FAIL. The object is absent.

- [ ] **Step 3: Implement `PortalWriteRules`**

`requireKey` checks the key rule. `requireShort` matches `^[a-z0-9][a-z0-9-]*$`.
`requireTitle` checks the title rule and its 256-character limit.
`requireRequestId` accepts `null`. For a value, it checks the request ID rule and its 128-character limit.
`requireText` refuses blank text and text over 3072 UTF-8 bytes.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :chat-shell:test --tests '*PortalWriteRulesTests*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add chat-shell/src/main/kotlin/com/demo/chat/shell/portal/PortalWriteRules.kt chat-shell/src/test/kotlin/com/demo/chat/test/portal/PortalWriteRulesTests.kt
git commit -m "feat(portal): add register write argument rules"
```

---

### Task 3: Open Path

**Files:**
- Create: `chat-shell/src/main/kotlin/com/demo/chat/shell/portal/RegisterGateway.kt`
- Create: `chat-shell/src/main/kotlin/com/demo/chat/shell/portal/PortalWriteService.kt`
- Test: `chat-shell/src/test/kotlin/com/demo/chat/test/portal/PortalWriteServiceTests.kt`

**Interfaces:**
- Produces: `interface RegisterGateway` with `readIndex(deadline: Duration): List<JsonNode>`, `join(topic: String)`, `addRoom(name: String)`, `post(topic: String, text: String, requestId: String): JsonNode`, `readMessages(topic: String): List<JsonNode>`.
- Produces: `class PortalWriteService(register: String, gateway: RegisterGateway, mapper: ObjectMapper, confirmWindow: Duration = Duration.ofSeconds(10), pause: (Long) -> Unit = { Thread.sleep(it) }, newRequestId: () -> String = { UUID.randomUUID().toString() })`.
- Produces: `fun open(request: PortalRequest, deadline: Duration): PortalReply`.
- Produces an open line: `{"type":"open","state":"created|exists","key":...,"topic":...,"pointer_id":...}`.

- [ ] **Step 1: Write the failing tests**

Use a fake gateway. Answer `readIndex` with typed JSON lines. Record each `post` body.

```kotlin
@Test
fun `open creates a topic joins it and posts one pointer`() {
    val gateway = FakeGateway(index = { listOf() })
    val reply = service(gateway).open(openRequest(key = "CHAT-a", short = "snap", title = "Snapshot"), deadline)
    assertThat(reply.error).isNull()
    assertThat(reply.lines.single()["state"].textValue()).isEqualTo("created")
    assertThat(gateway.calls).containsExactly("addRoom:register-index-fr-snap", "join:register-index-fr-snap", "post:register-index")
}

@Test
fun `open answers exists when the key already has a pointer`() {
    val gateway = FakeGateway(index = { listOf(pointer("CHAT-a", "register-index-fr-old", "t1", "1")) })
    val reply = service(gateway).open(openRequest(key = "CHAT-a", short = "snap", title = "Snapshot"), deadline)
    assertThat(reply.lines.single()["state"].textValue()).isEqualTo("exists")
    assertThat(gateway.calls).containsExactly("join:register-index-fr-old")
}

@Test
fun `open refuses a topic that another key owns`() {
    val gateway = FakeGateway(index = { listOf(pointer("CHAT-b", "register-index-fr-snap", "t1", "1")) })
    val reply = service(gateway).open(openRequest(key = "CHAT-a", short = "snap", title = "Snapshot"), deadline)
    assertThat(reply.error?.code).isEqualTo(PortalErrorCode.WRITE_FAILED)
    assertThat(reply.lines).isEmpty()
}

@Test
fun `open answers exists when a concurrent open wins the pointer post`() {
    // addRoom fails, and the second index read holds the pointer.
    val gateway = FakeGateway(index = { listOf(pointer("CHAT-a", "register-index-fr-snap", "t1", "1")) }, failAddRoom = true)
    val reply = service(gateway).open(openRequest(key = "CHAT-a", short = "snap", title = "Snapshot"), deadline)
    assertThat(reply.lines.single()["state"].textValue()).isEqualTo("exists")
}

@Test
fun `open joins the register once when the index is denied`() {
    val gateway = FakeGateway(index = { listOf(warning("denied-index")) }, denyAfterJoin = false)
    val reply = service(gateway).open(openRequest(key = "CHAT-a", short = "snap", title = "Snapshot"), deadline)
    assertThat(gateway.calls.first()).isEqualTo("join:register-index")
    assertThat(reply.lines.single()["state"].textValue()).isEqualTo("created")
}

@Test
fun `open answers write failed when the index stays denied`() {
    val gateway = FakeGateway(index = { listOf(warning("denied-index")) }, denyAfterJoin = true)
    val reply = service(gateway).open(openRequest(key = "CHAT-a", short = "snap", title = "Snapshot"), deadline)
    assertThat(reply.error?.code).isEqualTo(PortalErrorCode.WRITE_FAILED)
    assertThat(reply.lines).isEmpty()
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :chat-shell:test --tests '*PortalWriteServiceTests*'`
Expected: FAIL. `PortalWriteService` is absent.

- [ ] **Step 3: Implement the gateway interface and the open path**

`RegisterGateway` stays an interface with no Spring annotation.

`PortalWriteService.open`:

1. Parse the index lines into pointers. Keep records whose `topic` equals `register`.
2. If the index holds a `denied-index` warning, call `gateway.join(register)` once. Read the index again. Answer `WRITE_FAILED` when the warning remains.
3. Pick the earliest pointer for `key` by timestamp, then by id. Answer `exists` when one exists.
4. Build `newTopic = "$register-fr-$short"`. Refuse a topic over 64 characters with `INVALID_INPUT`.
5. Refuse `WRITE_FAILED` when an earlier pointer names `newTopic` for another key.
6. Call `addRoom`, then `join(newTopic)`, then `post(register, "$key | $newTopic | | $title", newRequestId())`.
7. When `addRoom` fails, read the index again. Answer `exists` when a pointer for `key` appeared. Otherwise answer the failure.
8. Read the index after the post. Answer `created` when a pointer for `key` holds the posted message id. Otherwise answer `exists` with the earliest pointer.
9. A pointer line that does not hold four fields, a key, and a topic answers `WRITE_FAILED`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :chat-shell:test --tests '*PortalWriteServiceTests*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add chat-shell/src/main/kotlin/com/demo/chat/shell/portal/RegisterGateway.kt chat-shell/src/main/kotlin/com/demo/chat/shell/portal/PortalWriteService.kt chat-shell/src/test/kotlin/com/demo/chat/test/portal/PortalWriteServiceTests.kt
git commit -m "feat(portal): open register entries from the portal service"
```

---

### Task 4: Post Path

**Files:**
- Modify: `chat-shell/src/main/kotlin/com/demo/chat/shell/portal/PortalWriteService.kt`
- Test: `chat-shell/src/test/kotlin/com/demo/chat/test/portal/PortalWriteServiceTests.kt`

**Interfaces:**
- Produces: `fun post(request: PortalRequest, deadline: Duration): PortalReply`.
- Produces a post line: `{"type":"post","key":...,"topic":...,"message_id":...,"command_id":...,"request_id":...}`.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test
fun `post joins the pointer topic and posts the text`() {
    val gateway = FakeGateway(index = { listOf(pointer("CHAT-a", "register-index-fr-snap", "t1", "1")) })
    gateway.receipt = receipt(messageId = "m2")
    gateway.messages = { listOf(record("m2", "register-index-fr-snap")) }
    val reply = service(gateway).post(postRequest(key = "CHAT-a", text = "hello"), deadline)
    assertThat(reply.lines.single()["message_id"].textValue()).isEqualTo("m2")
    assertThat(gateway.calls).containsExactly("join:register-index-fr-snap", "post:register-index-fr-snap")
}

@Test
fun `post refuses a key with no pointer`() {
    val gateway = FakeGateway(index = { listOf() })
    val reply = service(gateway).post(postRequest(key = "CHAT-a", text = "hello"), deadline)
    assertThat(reply.error?.code).isEqualTo(PortalErrorCode.NOT_OPEN)
    assertThat(reply.lines).isEmpty()
}

@Test
fun `post reuses a given request id so a retry adds no entry`() {
    val gateway = FakeGateway(index = { listOf(pointer("CHAT-a", "register-index-fr-snap", "t1", "1")) })
    gateway.receipt = receipt(messageId = "m2")
    gateway.messages = { listOf(record("m2", "register-index-fr-snap")) }
    val reply = service(gateway).post(postRequest(key = "CHAT-a", text = "hello", requestId = "r-1"), deadline)
    assertThat(gateway.postedRequestIds).containsExactly("r-1")
    assertThat(reply.lines.single()["request_id"].textValue()).isEqualTo("r-1")
}

@Test
fun `post answers unconfirmed when the read never shows the message`() {
    val gateway = FakeGateway(index = { listOf(pointer("CHAT-a", "register-index-fr-snap", "t1", "1")) })
    gateway.receipt = receipt(messageId = "m2")
    gateway.messages = { listOf() }
    val reply = service(gateway, pause = {}).post(postRequest(key = "CHAT-a", text = "hello"), deadline)
    assertThat(reply.error?.code).isEqualTo(PortalErrorCode.UNCONFIRMED)
    assertThat(reply.status).isEqualTo(75)
    assertThat(reply.lines).isEmpty()
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :chat-shell:test --tests '*PortalWriteServiceTests*'`
Expected: FAIL. `post` is absent.

- [ ] **Step 3: Implement `PortalWriteService.post`**

1. Resolve the request id. Generate one with `newRequestId()` when the request holds none.
2. Read the index with the denial rule from Task 3.
3. Pick the earliest pointer for `key`. Answer `NOT_OPEN` with status 1 when none exists.
4. Call `gateway.join(topic)`. Call `gateway.post(topic, text, requestId)`.
5. Read the receipt fields `messageId`, `commandId`, and `requestId`. Answer `WRITE_FAILED` with status 75 when `messageId` is absent.
6. Poll `gateway.readMessages(topic)` every 500 ms inside `confirmWindow`. Answer the post line when a record holds `messageId` and `topic`.
7. Answer `UNCONFIRMED` with status 75 when the window ends with no match.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :chat-shell:test --tests '*PortalWriteServiceTests*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add chat-shell/src/main/kotlin/com/demo/chat/shell/portal/PortalWriteService.kt chat-shell/src/test/kotlin/com/demo/chat/test/portal/PortalWriteServiceTests.kt
git commit -m "feat(portal): post register entries and confirm the readback"
```

---

### Task 5: Socket Dispatch and Spring Wiring

**Files:**
- Modify: `chat-shell/src/main/kotlin/com/demo/chat/shell/portal/PortalSocketServer.kt:54-62,161-191`
- Modify: `chat-shell/src/main/kotlin/com/demo/chat/shell/portal/PortalConfiguration.kt:50-73`
- Test: `chat-shell/src/test/kotlin/com/demo/chat/test/portal/PortalSocketServerTests.kt`

**Interfaces:**
- Consumes: `PortalWriteService.open` and `PortalWriteService.post`.
- Produces: `PortalSocketServer.handle: (PortalRequest, Duration) -> PortalReply`, which replaces the `read` parameter.
- Produces: `class ShellRegisterGateway(commands: PubSubCommands<*>, topics: TopicCommands<*>, register: String, user: String, mapper: ObjectMapper) : RegisterGateway`.

- [ ] **Step 1: Write the failing tests**

Add one socket test for each write operation. The test server passes a `handle` that answers an open line and a post line. Assert `status` 0 and the line type.

```kotlin
@Test
fun `the server serves an open request`() {
    val server = server(handle = { request, _ ->
        if (request.operation == OPEN_REGISTER_ENTRY) openReply("created") else invalidReply()
    })
    val reply = call(server, """{"operation":"open-register-entry","index":"register-index","key":"CHAT-a","short":"snap","title":"Snapshot"}""" + "\n")
    assertThat(reply["lines"][0]["type"].textValue()).isEqualTo("open")
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :chat-shell:test --tests '*PortalSocketServerTests*'`
Expected: FAIL. The `handle` parameter is absent.

- [ ] **Step 3: Replace the read parameter and wire the service**

In `PortalSocketServer`, rename the `read` parameter to `handle`. Call `handle(request, remainingSince(acceptedAt))`.

In `PortalConfiguration`, add a `portalWriteService` bean and a `shellRegisterGateway` bean. Pass `handle = { request, deadline -> when (request.operation) { OPEN_REGISTER_ENTRY -> write.open(request, deadline); POST_REGISTER_ENTRY -> write.post(request, deadline); else -> read.read(request, deadline) } }` to the server factory.

`ShellRegisterGateway` maps each interface call to the command object:

- `readIndex` to `commands.registerRead(register, REGISTER_MAX_AGE, deadline)`.
- `join` to `topics.join(user, topic)`.
- `addRoom` to `topics.addTopic(user, name)`. Throw when the answer is null.
- `post` to `commands.post(topic, text, requestId)`.
- `readMessages` to `commands.messages(topic, format = "records")`.

Each answer parses to `JsonNode` with the configuration `ObjectMapper`. A blank answer gives an empty list.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :chat-shell:test --tests '*Portal*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add chat-shell/src/main/kotlin/com/demo/chat/shell/portal/PortalSocketServer.kt chat-shell/src/main/kotlin/com/demo/chat/shell/portal/PortalConfiguration.kt chat-shell/src/test/kotlin/com/demo/chat/test/portal/PortalSocketServerTests.kt
git commit -m "feat(portal): dispatch open and post to the write service"
```

---

### Task 6: MCP Client Methods

**Files:**
- Modify: `chat-mcp/src/main/kotlin/com/demo/chat/mcp/portal/PortalClient.kt:29-231`
- Test: `chat-mcp/src/test/kotlin/com/demo/chat/mcp/portal/PortalClientTests.kt`

**Interfaces:**
- Renames: `PortalReadResult` to `PortalResult`.
- Produces: `fun open(key: String, short: String, title: String): PortalResult`.
- Produces: `fun post(key: String, text: String, requestId: String?): PortalResult`.
- The rename reaches every use of `PortalReadResult` in `PortalToolRegistration.kt` and the MCP tests.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test
fun `open sends the three write fields`() {
    val server = fakePortal { frame ->
        assertThat(frame["operation"]).isEqualTo("open-register-entry")
        assertThat(frame["key"]).isEqualTo("CHAT-a")
        """{"status":0,"lines":[],"complete":true}"""
    }
    val result = PortalClient(socket = server, selector = "register-index").open("CHAT-a", "snap", "Snapshot")
    assertThat(result.errorCode).isNull()
}

@Test
fun `post omits an absent request id`() {
    val server = fakePortal { frame ->
        assertThat(frame.containsKey("requestId")).isFalse()
        """{"status":0,"lines":[],"complete":true}"""
    }
    PortalClient(socket = server, selector = "register-index").post("CHAT-a", "hello", null)
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :chat-mcp:test --tests '*PortalClientTests*'`
Expected: FAIL. `open` and `post` are absent.

- [ ] **Step 3: Implement the client methods**

Generalise `requestFrame` to take a `Map<String, String>`. Add the operation constants `open-register-entry` and `post-register-entry`. Each new method calls the shared `exchange(topic = null, extra = fields)` path.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :chat-mcp:test --tests '*PortalClientTests*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add chat-mcp/src/main/kotlin/com/demo/chat/mcp/portal/PortalClient.kt chat-mcp/src/test/kotlin/com/demo/chat/mcp/portal/PortalClientTests.kt
git commit -m "feat(mcp): add portal open and post client calls"
```

---

### Task 7: MCP Tools

**Files:**
- Modify: `chat-mcp/src/main/kotlin/com/demo/chat/mcp/portal/PortalToolRegistration.kt:32-150`
- Test: `chat-mcp/src/test/kotlin/com/demo/chat/mcp/PortalOnlyToolTests.kt`, `chat-mcp/src/test/kotlin/com/demo/chat/mcp/portal/PortalToolTests.kt`

**Interfaces:**
- Produces: `const val OPEN_REGISTER_ENTRY_TOOL_NAME: String = "chat_open_register_entry"`.
- Produces: `const val POST_REGISTER_ENTRY_TOOL_NAME: String = "chat_post_register_entry"`.
- Consumes: `PortalClient.open` and `PortalClient.post`.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test
fun `portal only mode discovers three register tools`() {
    val names = toolNames()
    assertThat(names).contains(READ_REGISTER_TOOL_NAME, OPEN_REGISTER_ENTRY_TOOL_NAME, POST_REGISTER_ENTRY_TOOL_NAME)
}

@Test
fun `open refuses an unknown argument`() {
    val result = call(OPEN_REGISTER_ENTRY_TOOL_NAME, """{"key":"CHAT-a","short":"snap","title":"S","extra":"x"}""")
    assertThat(result.isError).isTrue()
}
```

Add one test that a server without `registerName` registers no write tool.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :chat-mcp:test --tests '*Portal*Tool*'`
Expected: FAIL. The tool names are absent.

- [ ] **Step 3: Register the two tools**

Add two `Tool` values with their schemas:

- `chat_open_register_entry` requires `key`, `short`, `title`.
- `chat_post_register_entry` requires `key` and `text`. `requestId` is optional.
  The MCP tool derives a stable ID when the caller omits it.

Each handler calls `refuseUnknownArguments`, checks the arguments locally, then calls the client. Each answers the same `status`, `lines`, `complete`, and `error` shape as the read tool.

The chat-mcp module does not import chat-shell classes. Repeat the four argument rules in this module:

- `key`: nonempty, at most 64 characters, no `|`, no backslash, no control character.
- `short`: matches `^[a-z0-9][a-z0-9-]*$`.
- `title`: nonempty, no `|`, no control character.
- `text`: nonblank, at most 3072 UTF-8 bytes.
- `requestId`: `^[A-Za-z0-9._:-]+$`, and `_` is refused.

A broken rule raises `ToolException` with a message that names the argument and the rule.

Register the write tools inside `registerPortalTool(server, config)` only when `config.portalSocket` and `config.registerName` are both present.

Set `readOnlyHint = false` and `openWorldHint = true` on both.
Set `idempotentHint = true` on open and post.
The MCP post tool derives a stable ID for repeated calls without an explicit ID.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :chat-mcp:test --tests '*Portal*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add chat-mcp/src/main/kotlin/com/demo/chat/mcp/portal/PortalToolRegistration.kt chat-mcp/src/test/kotlin/com/demo/chat/mcp/PortalOnlyToolTests.kt chat-mcp/src/test/kotlin/com/demo/chat/mcp/portal/PortalToolTests.kt
git commit -m "feat(mcp): add open and post register entry tools"
```

---

### Task 8: Documentation

**Files:**
- Modify: `docs/MCP-ADAPTER.md`
- Modify: `node-9/README.md`

- [ ] **Step 1: Document the tools**

In `docs/MCP-ADAPTER.md`, add each tool name, its arguments, its answer lines, its error codes, and the `registerName` requirement. State that a failed or uncertain write gives no success receipt.

In `node-9/README.md`, state that the portal serves the `open` and `post` rules. State that `node-9/bin/register` keeps the shell path and its rules match the portal.

- [ ] **Step 2: Check the drift bindings**

Run: `drift check`
Expected: PASS, or a report of only unrelated paths.

- [ ] **Step 3: Commit**

```bash
git add docs/MCP-ADAPTER.md node-9/README.md
git commit -m "docs: describe portal register write tools"
```

---

### Task 9: End-to-End Proof

**Files:**
- Modify: `chat-mcp/src/test/kotlin/com/demo/chat/mcp/PortalEndToEndStdioTests.kt`

- [ ] **Step 1: Write the failing test**

Start a fake portal server on a temporary socket. It answers the full open sequence. Call `chat_open_register_entry` through the stdio harness. Assert the open line reaches the client.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :chat-mcp:test --tests '*PortalEndToEndStdioTests*'`
Expected: FAIL at the tool call.

- [ ] **Step 3: Fix the harness wiring**

Update the harness call list and the expected answer. Change no production code unless the test shows a defect.

- [ ] **Step 4: Run the full check**

Run: `just check`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add chat-mcp/src/test/kotlin/com/demo/chat/mcp/PortalEndToEndStdioTests.kt
git commit -m "test(mcp): prove the open tool end to end"
```
