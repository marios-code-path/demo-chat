# Demo Chat MCP adapter: first feasibility phase

FP: `CHAT-ylvoiixm`.
Date: 2026-09-28.
Branch: `chat-mcp-impl`.
Base: master `4e1955bc`.
Design: `docs/superpowers/specs/2026-09-27-demo-chat-mcp-design.md`.

**Goal.** Build a standalone stdio MCP adapter for Demo Chat. Prove that a real
MCP client can discover the adapter and call two read tools against a running
backend. Prove that the distributable compiles with GraalVM Native Image.

**Scope limits.**

- This phase carries two tools. `chat_list_topics` and `chat_get_topic`.
- Search and send are a later phase. Section "Later phases" holds them.
- The adapter has no database credentials and no direct store access.
- This work does not close `CHAT-znprrzhn`. Deployed route enforcement stays
  separate. The adapter must not claim permission enforcement.
- No deployment change lands in this phase. The backend prerequisite below
  names what an operator must run.

## Decision D1: the protocol revision

**Decided by the owner on 2026-09-28: `2025-11-25`.**

The design proposed revision `2026-07-28`. Neither official SDK implements it.
Both measurements were read from the compiled artifacts, not from documentation.

| SDK | Latest release | `LATEST_PROTOCOL_VERSION` | Supported list |
|---|---|---|---|
| Kotlin `io.modelcontextprotocol:kotlin-sdk` | 0.15.0, published 2026-07-28 | `2025-11-25` | 2025-11-25, 2025-06-18, 2025-03-26, 2024-11-05 |
| TypeScript `@modelcontextprotocol/sdk` | 1.31.0, published 2026-09-28 | `2025-11-25` | 2025-11-25, 2025-06-18, 2025-03-26, 2024-11-05, 2024-10-07 |

Sources: `kotlin-sdk-core-jvm-0.15.0.jar`, the `CommonKt` static initializer.
And `dist/esm/types.js` in the TypeScript package.

The specification site reports `2026-07-28` as the current revision. The Kotlin
SDK `main` branch carries `server/discover` types for it, marked
`@ExperimentalMcpApi`. Its supported list still stops at `2025-11-25`.

The design forbids a handwritten protocol compatibility layer. This plan does
not add one. A later phase moves the revision when the SDK releases support.

**Consequence.** The adapter declares `2025-11-25`. A client that offers only a
newer revision receives the SDK version error. That error is correct behaviour
and is not a defect.

## Measured facts at base `4e1955bc`

1. **The topic route always exists.** `ChatTopicServiceController` carries no
   conditional. Its prefix is `/topic`. `restGetRoom` maps `/id/{id}`. So
   `GET /topic/id/{id}` is live in every webflux deployment.
2. **The message route is conditional.** `ChatMessageServiceController` carries
   `@ConditionalOnProperty(prefix = "app.controller", name = ["message"])`. Its
   prefix is `/message`. `restMessageById` maps `/id/{id}`.
3. **The recall route is conditional.** `ChatMessageRecallController` carries
   `@ConditionalOnProperty(prefix = "app.controller", name = ["recall"])`. Its
   prefix is `/message/recall`. `recallInTopic` maps `/topic`.
4. **No deployment yml sets either property.** Both are set by tests only.
   `docs/VECTOR-RECALL-API.md:118` and `docs/EMBEDDING-PROVIDERS.md:181` show
   the launch flags an operator uses.
5. **Path ids resolve through the registry.** Each route parameter carries
   `@Resolved(ChatDomain.MESSAGE_TOPIC)` or `@Resolved(ChatDomain.MESSAGE)`.
   An unknown id fails before the service runs.
6. **The backend wire shapes are wrapped.** `Key<T>` carries
   `@JsonTypeInfo(As.WRAPPER_OBJECT)` with `@JsonTypeName("key")`. `Message<T,E>`
   carries `WRAPPER_OBJECT` with `@JsonTypeName("message")`. `MessageTopic<T>`
   extends `KeyValuePair`, which carries `WRAPPER_OBJECT` with
   `@JsonTypeName("keyValue")`. So a topic response is not a flat object.
7. **A recall hit carries no message text.** `MessageRecallHit<T>` holds `key`
   and `score` alone. The message projection needs a second read. The score is
   nullable.
8. **`RequestResponse<T>` is sealed with `As.PROPERTY`.** Its discriminator is
   the property `type`. So `TopicRecallRequest` serializes flat, with a `type`
   member. This differs from fact 6, and the design asks for it to be tested.
9. **The stdio transport exists in the SDK.** `StdioServerTransport` and its
   `Builder` are present in `kotlin-sdk-server-jvm-0.15.0.jar`.
10. **A native profile precedent exists.** `chat-deploy/pom.xml` holds a
    `native` profile. It names `mainClass` and binds `compile-no-fork` to the
    `package` phase.

## Prerequisite: a reachable backend

The adapter needs a deployment that exposes `/message` and `/message/recall`.
No committed configuration does that today.

The first phase needs one route only, `GET /topic/id/{id}`, because the two
tools in scope read topics. The operator starts a webflux deployment with
`app.controller.topic`. The message routes are a later-phase prerequisite.

`CHAT-znprrzhn` stays open. No route enforces authorization in a deployment
today. The adapter narrows scope. It does not add enforcement.

## Task 1: the `chat-mcp` module and the stdio server

**Files.** New `chat-mcp/pom.xml`. New main class under
`chat-mcp/src/main/kotlin/com/demo/chat/mcp/`. Root `pom.xml` module entry.

1. Add the module to the root reactor, after `chat-core`.
2. Declare `io.modelcontextprotocol:kotlin-sdk-server:0.15.0` and
   `kotlin-sdk-core:0.15.0`. Use the JVM variants.
3. Declare `kotlin-stdlib` and `kotlinx-serialization-json` explicitly. The
   module does not depend on `chat-core`, so it inherits no Kotlin runtime.
4. Add no `chat-core` dependency. The adapter speaks REST only.
5. Build a `Server` with name and version. Register it on
   `StdioServerTransport`.
6. Route every diagnostic to stderr. Keep stdout for protocol frames alone.
7. Stop when stdin closes. Bound the shutdown.

**Done.** `mvn -o -B -pl chat-mcp -am test` passes. The module builds no image.

## Task 2: configuration and identity rules

**Files.** New `chat-mcp/src/main/kotlin/com/demo/chat/mcp/config/`.

1. Read `backendBaseUrl`, `credentialFile`, `keyType`, `topicIds`,
   `enableSend` and `enableSearch`.
2. Require a fixed HTTPS origin. Permit HTTP only in loopback development mode.
3. Read the credential from the file. Never accept a credential argument.
4. Require `keyType` to be exactly `long` or `uuid`. Refuse any other value.
5. Require one to 100 unique canonical topic ids.
6. Default `enableSend` and `enableSearch` to false.
7. Parse a Long id as canonical base-10 text. Reject zero, a fraction, exponent
   notation, whitespace, a leading plus sign, a leading zero and overflow.
8. Parse a uuid id as canonical lowercase text. Reject the zero uuid.
9. Refuse a redirect to another origin. Never send a credential across one.

**Done.** One test per rejection rule, and one per accept rule. A Long above
`2^53` round-trips without loss. A fractional or numeric JSON id is refused
before any backend call.

## Task 3: the REST client and the envelope proof

**Files.** New `chat-mcp/src/main/kotlin/com/demo/chat/mcp/client/`.

1. Call `GET /topic/id/{id}` with the configured credential.
2. Decode the real response envelope. Fact 6 says it is wrapped. Pin the exact
   shape with a test against a running backend.
3. Never convert an id through `Double` or `Float`. Encode a Long as an exact
   integer where a body needs a number.
4. Reject a returned key that is empty, or that lacks an id or a root.
5. Do not synthesize a root. Do not accept a placeholder.
6. Apply a five second connect timeout and a 30 second call deadline.
7. Allow at most four concurrent backend requests.
8. Limit each response to one MiB of UTF-8 JSON. Fail above the limit.

**Done.** A contract test decodes a real topic response from a running
deployment. The test fails when the wrapper is removed from the fixture.

## Task 4: the first two tools

**Files.** New `chat-mcp/src/main/kotlin/com/demo/chat/mcp/tool/`.

1. Implement `chat_list_topics`. Input is an empty object with
   `additionalProperties: false`. Output is `{topics: Topic[]}`.
2. Obtain each configured topic through its id endpoint. Do not call the
   unbounded list endpoint.
3. Omit an unavailable or denied topic. Report no name and no count for it.
4. Fail the whole list on an authentication, transport or backend failure.
5. Implement `chat_get_topic`. Input is `{topicId: Id}`. Output is `{topic: Topic}`.
6. Refuse a topic argument that is absent from the allowlist.
7. Emit every id as a JSON string, including a Long id.
8. Declare `readOnlyHint=true` and `idempotentHint=true` on both tools.
9. Declare a JSON Schema for the input and the output of each tool.

**Done.** The two tools answer over stdio. A denied topic is absent from the
list, and its name never appears in the response or in stderr.

## Task 5: a real MCP client and stdout purity

**Files.** New test sources under `chat-mcp/src/test/kotlin/`. The operator
procedure goes in `docs/MCP-ADAPTER.md`.

1. Connect a real MCP client to the adapter over stdio. Claude Code is one
   such client.
2. Complete discovery. List the tools.
3. Call each tool. Compare each answer against the deployed backend.
4. Assert that stdout carries valid protocol frames and nothing else.
5. Assert that one diagnostic line reaches stderr for each call.
6. Close stdin. Assert that the process exits within the bound.
7. Run the same calls on the JVM build. Record that this is not the native
   acceptance test.

**Done.** A recorded transcript shows discovery and two tool calls. A stdout
capture holds no diagnostic text.

## Task 6: GraalVM Native Image

**Files.** `chat-mcp/pom.xml`. Reachability metadata under
`chat-mcp/src/main/resources/META-INF/native-image/`.

1. Add a `native` profile. Model it on `chat-deploy/pom.xml` (fact 10).
2. Bind `compile-no-fork` to the `package` phase. Without the binding the
   profile builds no image.
3. Name the main class explicitly.
4. Build the image. Record every reachability error.
5. Add reachability metadata for Ktor and for kotlinx-serialization where the
   agent cannot infer it.
6. Run the Task 5 transcript against the native executable.
7. Report a failure as a feasibility finding. Do not hide it behind the JVM
   path.

**Done.** The native executable completes discovery and both tool calls. Its
startup time is recorded.

## Task 7: errors, and focused gates

**Files.** New `chat-mcp/src/main/kotlin/com/demo/chat/mcp/error/`.

1. Use SDK protocol errors for a malformed MCP envelope.
2. Use a tool error result for an application failure. Carry `code`, `message`
   and `retryable`.
3. Use the codes `AUTHENTICATION_REQUIRED`, `NOT_AVAILABLE`,
   `FEATURE_UNAVAILABLE`, `BACKEND_UNAVAILABLE`, `LIMIT_EXCEEDED` and
   `OUTCOME_UNKNOWN`.
4. Expose no raw backend exception text. Do not separate a hidden object from
   an absent one.
5. Log the tool name, a correlation id, the duration, the result code and the
   backend status to stderr.
6. Log no token and no message text.
7. Run the gates below. Keep each output in a log file.

**Gates.**

1. `mvn -o -B -pl chat-mcp -am test`.
2. `shell-scripts/check-dependency-versions.sh`.
3. `git diff --check`.
4. `drift check` for each bound document.

## Mutation proofs

Each mutation is applied, measured and restored by absolute path. `git status`
proves each restore.

| Id | Mutation | Expected failure |
|---|---|---|
| M1 | Accept a numeric JSON id where a string is required | Task 2 rejection tests |
| M2 | Accept a fractional id | Task 2 rejection tests |
| M3 | Convert an id through `Double` | The `2^53` round-trip test |
| M4 | Emit a denied topic in `chat_list_topics` | Task 4 |
| M5 | Route a diagnostic line to stdout | Task 5 purity test |
| M6 | Accept a topic argument outside the allowlist | Task 4 |

## Documents

- `docs/MCP-ADAPTER.md`: operator configuration, launch, and the accepted
  protocol revision.
- `forward-register.md`: the D1 decision, and the SDK revision measurement.
- `docs/BUILD.md`: the `chat-mcp` build and native commands, if the module
  needs one.

## Closure

`CHAT-ylvoiixm` closes when tasks 1 to 7 pass. Its closing comment names each
test, each mutation result and each gate result. It also records the SDK
revision limit, because that limit outlives this phase.

Do not push, open a pull request or merge without owner approval.

## Later phases

Search and send follow this phase. Each carries a prerequisite.

1. **`chat_get_message`.** Needs a deployment with `app.controller.message`.
2. **`chat_search_messages`.** Needs `app.controller.recall` and a vector
   selector pair. A recall hit carries no text (fact 7), so the tool resolves
   each hit through the message route. An incomplete empty result keeps
   `indexComplete=false`.
3. **`chat_send_message`.** Needs `app.controller.message`. It is gated on
   `enableSend`. A connection loss after dispatch answers `OUTCOME_UNKNOWN`
   with no retry.
4. **The revision move.** When the Kotlin SDK releases `2026-07-28` support,
   raise the declared revision and re-run the client transcript.
