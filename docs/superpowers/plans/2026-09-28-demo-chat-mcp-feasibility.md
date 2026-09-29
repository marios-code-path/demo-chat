# Demo Chat MCP adapter: first feasibility phase

FP: `CHAT-ylvoiixm`.
Date: 2026-09-28.
Branch: `chat-mcp-impl`.
Base: master `4e1955bc`.
Design: `docs/superpowers/specs/2026-09-27-demo-chat-mcp-design.md`.

**Goal.** Build a standalone stdio MCP adapter for Demo Chat. Prove that an MCP
client can discover the adapter and call two read tools against a running
backend. Prove that the distributable compiles with GraalVM Native Image.

**Scope limits.**

- This phase carries two tools. `chat_list_topics` and `chat_get_topic`.
- Search and send are a later phase. Section "Later phases" holds them.
- The adapter has no database credentials and no direct store access.
- This work does not close `CHAT-znprrzhn`. Deployed route enforcement stays
  separate. The adapter must not claim permission enforcement.
- No deployment change lands in this phase. The backend prerequisite below
  names what an operator must run.
- This phase does not depend on Embabel. A later `chat-agent` module may
  consume these MCP tools through Embabel.

The Embabel boundary is deliberate. Embabel supports MCP clients and servers.
Its documented server integration is Spring Boot based, and it commonly uses
SSE or Streamable HTTP. That differs from this phase's standalone stdio and
Native Image boundary. Sources:
[Embabel MCP client support](https://github.com/embabel/embabel-agent#consuming-mcp-servers)
and [Embabel server integration](https://hub.embabel.com/reference/integrations).

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

### The launch recipe, measured on 2026-09-28

The profile list `-Pdeploy,expose-webflux` alone does not start. The context
fails with:

```
Parameter 0 of constructor in com.demo.chat.controller.webflux.ChatTopicServiceController
required a bean of type 'com.demo.chat.config.CompositeServiceBeans' that could not be found.
```

The cause is `@ConditionalOnProperty("app.service.composite")` on
`CompositeServiceBeansConfiguration` in `chat-service-composite`. That module
is on the runtime classpath. The property is what selects the bean.

So the launch carries the golden `core-memory-init` flag set and adds the
webflux profile. The load-bearing flags are:

- `-Dapp.service.composite` and `-Dapp.service.composite.auth`
- `-Dapp.service.core.key=memory`, `persistence=memory`, `pubsub=memory`,
  `secrets=memory` and `index=lucene`
- `-Dapp.key.type=long`, `-Dapp.nodeid=0`, `-Dapp.primary=core-service`
- `-Dspring.config.additional-location=...` for `userinit.yml`, which supplies
  the initial users and the shipped grants
- `-Dserver.port=6791`, so the actuator port and the application port agree

The memory module declares `chat-persistence-memory`, `chat-messaging-memory`,
`chat-index-lucene` and `chat-service-composite` at compile scope, so no
profile supplies them.

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

**Acceptance criteria.**

- `mvn -o -B -pl chat-mcp -am test` passes.
- The module builds no image.
- `shell-scripts/build-health.sh --integration` exits 0. A focused module test
  is not sufficient here, because the root `pom.xml` changes.

### Measured during Task 1

Four facts. Each one was measured and none was read.

1. **The SDK needs a higher kotlinx line than Boot manages.**
   `kotlin-sdk-core-jvm` 0.15.0 requires `kotlinx-serialization-json-jvm`
   1.11.0 and `kotlinx-coroutines-core-jvm` 1.11.0. Boot 4.0.8 imports both
   BOMs at 1.9.0 and 1.10.2. Maven applies dependency management to a
   transitive dependency, so the SDK requirement is forced down and
   `requireUpperBoundDeps` fails. **The parent raises the two Boot
   properties.** One artifact pin would mix two versions of one library.

2. **Three paths request `kotlinx-io-core-jvm`.** The SDK wants 0.9.1, ktor
   3.5.1 wants 0.9.0 and `kotlinx-serialization-json-io-jvm` wants 0.6.0.
   `dependencyConvergence` fails on the three. One parent entry settles them.
   **The `-jvm` artifact needs its own entry**, because a dependencyManagement
   entry matches one artifactId alone. The first attempt managed the
   multiplatform name and the enforcer named the miss.

3. **`Server.onClose` runs from `Server.close()` alone.** An end of file on
   stdin does not reach it. The transport callback fires instead.
   `Server.createSession` registers a session close handler that removes the
   session from the registry. It does not call the server callback. The
   shutdown path hooks `transport.onClose` for that reason.
   `McpAdapterStdioTests` pins both readings.

4. **An offline `--integration` run needs a primed local repository.** The
   raised kotlinx versions are absent from a cache built before this change.
   Without priming, the reactor stops in `chat-core` with
   `kotlinx-coroutines-reactive:jar:1.11.0 (absent)`, and every later module
   reports as skipped. **The message names an absent artifact and not a code
   defect.** Prime once with an online `mvn -B -DskipTests package`.

### Task 1 gate results

Measured on 2026-09-28 on branch `chat-mcp-impl`.

| Gate | Result |
|---|---|
| `mvn -o -B -pl chat-mcp -am test` | exit 0. 5 tests, 0 failures, 0 errors, 0 skipped. |
| `mvn -o -B -pl chat-mcp package -DskipTests` | exit 0. One 16 KB jar and no image. |
| `shell-scripts/build-health.sh --integration` | exit 0. 29 modules ran 1521 tests, 0 failures, 0 errors, 59 skipped. The run reports that reality matches `docs/BUILD-HEALTH.md`. |

The reactor holds 37 modules while `chat-mcp` is in it. The five module tests
ran inside the gate, so the third result covers the new module.

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

**Acceptance criteria.**

- One test per rejection rule, and one per accept rule.
- A Long above `2^53` round-trips without loss.
- A fractional or numeric JSON id is refused before any backend call.

### Decisions taken during Task 2

The task named six keys and nine rules. It did not name where the keys come
from. Six decisions were needed, and the owner may overrule any of them.

1. **The configuration is a Java `Properties` file.** Its path arrives as
   `--config <path>` or as `CHAT_MCP_CONFIG`. An unknown argument is refused,
   so a misplaced argument cannot change behaviour in silence. This was a gap
   in the plan, not a reading of it.
2. **A relative `credentialFile` resolves against the configuration file
   directory.** The process working directory belongs to the client, so it
   must not decide which file holds the credential.
3. **A negative Long id is accepted.** Rule 7 lists the values it rejects and
   the list is closed. It names zero, a fraction, exponent notation,
   whitespace, a leading plus sign, a leading zero and overflow. It does not
   name a negative value. So `-7` is canonical text and it is accepted. **The
   owner decides whether that is correct.**
4. **The separator whitespace of `topicIds` is removed.** `1, 2` and `1,2`
   load the same list. Whitespace inside one id is still refused, so `1 2` is
   not an id.
5. **An unknown key in the file fails the start.** This mirrors
   `UserInitConfigBindingTests`, which reads the shipped `userinit.yml` for the
   same reason. Spring ignores an unknown key, and a typo would then have no
   effect and no error.
6. **The six keys stay six.** The HTTP loopback rule is a value rule on
   `backendBaseUrl`, not a seventh opt-in flag.

**The credential is read at startup and discarded.** `main` reads it so that a
missing or empty file fails the start, before any client connects. Only the
client reads it again, at the moment of use. `AdapterConfig` never holds it, so
no log line and no `toString` can carry it.

**The JSON boundary rejects every non-string primitive.** `parseIdElement`
refuses a JSON number, a boolean and a non-primitive. So no `Double` or `Float`
ever holds an id, and rule 8 of Task 3 is already satisfied at the boundary.

### Measured during Task 2

1. **`java.net.URI.getHost()` returns an IPv6 literal with its brackets.** The
   v6 loopback test failed first with `expected: <::1> but was: <[::1]>`. Both
   sides of an origin comparison pass through `originOf`, so both carry the
   brackets and the comparison holds. `isLoopbackHost` strips them before it
   looks at the name. **A reader who compares a bare `::1` to a parsed host
   will not match.**

### Task 2 gate results

Measured on 2026-09-28 on branch `chat-mcp-impl`.

| Gate | Result |
|---|---|
| `mvn -o -B -pl chat-mcp -am test` | exit 0. 85 tests, 0 failures, 0 errors, 0 skipped. That is the 5 Task 1 tests and 80 new ones. |
| `shell-scripts/build-health.sh --integration` | exit 0. 29 modules ran 1601 tests, 0 failures, 0 errors, 59 skipped. The run reports that reality matches `docs/BUILD-HEALTH.md`. |
| `git diff --check` | exit 0. No whitespace error. |

The integration count moved from 1521 to 1601, which is the 80 new tests. The
run is offline. **The offline prerequisite is unchanged and it still applies.**
A local repository that predates Task 1 holds no raised kotlinx version, so an
offline run stops in `chat-core` with
`kotlinx-coroutines-reactive:jar:1.11.0 (absent)`. Prime once with an online
`mvn -B -DskipTests package`. The Task 1 run primed this machine, so no second
prime was needed.

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

**Acceptance criteria.**

- A contract test decodes a real topic response from a running deployment.
- The test fails when the wrapper is removed from the fixture.

### The captured envelope, measured on 2026-09-28

The deployment answers `GET /topic/id/1554361326074068992` with 122 bytes and
no trailing newline:

```
{"keyValue":{"data":"mcpcontracttopic","key":{"key":{"id":1554361326074068992,"root":1554361143634427905,"empty":false}}}}
```

The bytes are the fixture at
`chat-mcp/src/test/resources/topic-response.json`. The fixture is byte-identical
to the response, proven with `cmp`.

Two wrappers are present. The outer `keyValue` wrapper comes from
`KeyValuePair`. The inner `key` wrapper comes from `Key`. `MessageTopic` carries
no annotation of its own and inherits the outer one. Field order is not part of
the contract.

Both ids are JSON numbers above 2^53. **This is the load-bearing measurement of
Task 3.** `1554361143634427905` sits one below a value that a `Double` can
represent, so a Double read answers `1554361143634427904`. The root is the id
of the domain root key. It comes from the response, and the adapter never
derives one.

### Decisions taken during Task 3

1. **The JSON shape of an id follows the key type.** A `long` deployment sends
   a JSON number. A `uuid` deployment sends a JSON string. The other shape is
   refused. So a backend shape change fails at the boundary rather than later.
2. **The exact literal text is read from the document.** `JsonPrimitive.content`
   returns the text as the lexer read it. No `Double` and no `Float` holds an
   id. A fractional or exponent form is refused by the Task 2 canonical rule, so
   a loss of precision cannot pass in silence.
3. **A JSON `null` is refused before its text is read.** `JsonNull` is a
   `JsonPrimitive` whose `content` is the four character text `null`. This is
   the defect class of `CHAT-auglbxrm`. The adapter refuses the value at the
   primitive step.
4. **The returned root is checked against the id rules.** An absent, blank, or
   non-canonical root is refused. The adapter does not synthesize one and does
   not accept a placeholder.
5. **The credential is read from its file at each request.** `TopicClient`
   reads it per call, so a rotated file takes effect without a restart. The
   types hold no token.
6. **The transport never follows a redirect itself.** `followRedirects(NEVER)`
   is set. Each hop is checked against the configured origin before a request
   goes out, so no credential reaches another origin.
7. **One call deadline covers every hop, and the body too.** The 30 second
   deadline is measured from the start of the call. Each hop receives the
   remaining time, so a chain of redirects cannot extend it.
8. **A watchdog closes a stalled response body.** The HTTP request timeout
   covers the connect step and the response headers. It does not cover a body
   that arrives slowly. One daemon thread closes the stream when the deadline
   passes. Measured: with the watchdog the call fails at 400 ms against a
   server that stalls for five seconds. Without it the same call runs 5013 ms.
9. **The limits are constructor parameters with the agreed defaults.** A test
   sets a small deadline or a small response limit, so the rule is exercised
   without a slow test.
10. **The body of a redirect response is closed before the target is resolved.**
    A refused redirect therefore leaks no connection. A close that ran after
    the resolution would not run at all on the refusal path.

### Measured during Task 3

- `java.net.URI.getHost()` returns an IPv6 literal with its brackets. Both
  sides of an origin comparison pass through `originOf`, so the brackets agree.
  Recorded under Task 2.
- `com.sun.net.httpserver.HttpServer` is available to this module's tests. The
  transport tests use it, so the transport rules run through the JDK client and
  not through a stub.
- A `Location` header that names another scheme, such as `mailto:`, resolves to
  an absolute URI with no host. `originOf` answers an empty origin and the
  transport refuses it.

### Task 3 gate results

Measured on 2026-09-28 on branch `chat-mcp-impl`.

| Gate | Result |
|---|---|
| `mvn -o -B -pl chat-mcp -am -Dtest=TopicEnvelopeTests,BackendHttpTests,TopicClientTests -Dsurefire.failIfNoSpecifiedTests=false test` | exit 0. 42 tests, 0 failures, 0 errors, 0 skipped. |
| `mvn -o -B -pl chat-mcp test` | exit 0. 127 tests, 0 failures, 0 errors, 0 skipped. |
| M7, wrapper removed from the fixture | `TopicEnvelopeTests` fails. 2 errors, both `ClientException: the response holds no 'keyValue' field`. |
| M3, the id read through a `Double` | `TopicEnvelopeTests` fails 3 of 20. The root reads `1554361143634427904` where `1554361143634427905` is the captured value. Both form refusals stop firing. |
| The body watchdog disabled | `BackendHttpTests` fails. `the call ran for 5013 ms`, against a server that stalls for five seconds and a 400 ms deadline. |
| `shell-scripts/build-health.sh --integration` | exit 0. 29 modules ran 1643 tests, 0 failures, 0 errors, 59 skipped. The run reports that reality matches `docs/BUILD-HEALTH.md`. |

M3 and M7 were applied and restored by absolute path. `cmp` proves each
restore, and `git status` shows the file as a new untracked file and not as a
modification.

The integration gate ran last, on the final source. An earlier gate run began
before the redirect body close of decision 10. That run was stopped, because
its reading would have described a superseded tree. The two module rows above
were then measured again, and the gate ran once on the tree that is committed.

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

**Acceptance criteria.**

- The two tools answer over stdio.
- A denied topic is absent from the list.
- Its name appears in no response and in no stderr line.

## Task 5: a pinned MCP client and stdout purity

**Files.** New test sources under `chat-mcp/src/test/kotlin/`. A pinned client
harness under `chat-mcp/src/test/client/`. The operator procedure goes in
`docs/MCP-ADAPTER.md`.

1. Pin the client harness to `@modelcontextprotocol/sdk` 1.31.0. That release
   declares `2025-11-25`, which is the revision this adapter serves.
2. Run the harness with Node. Record the Node version in the test output.
3. Connect the harness to the adapter over stdio.
4. Complete discovery. List the tools.
5. Call each tool. Compare each answer against the deployed backend.
6. Assert that stdout carries valid protocol frames and nothing else.
7. Assert that one diagnostic line reaches stderr for each call.
8. Close stdin. Assert that the process exits within the bound.
9. Run the same calls on the JVM build. Record that this is not the native
   acceptance test.
10. Record a Claude Code session as optional confirmation. It is not the
    required proof.

**Acceptance criteria.**

- A recorded harness transcript shows discovery and two tool calls on the
  pinned client version.
- A stdout capture holds no diagnostic text.
- The harness runs with no manual step. A second machine reproduces it.

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
6. Run the Task 5 harness transcript against the native executable.
7. Report a failure as a feasibility finding. Do not hide it behind the JVM
   path.

**Acceptance criteria.**

- The native executable completes discovery and both tool calls.
- Its startup time is recorded.
- A reachability failure is reported, not suppressed.

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
2. `shell-scripts/build-health.sh --integration`. The root reactor changed, so
   this gate is required and not optional.
3. `shell-scripts/check-dependency-versions.sh`.
4. `git diff --check`.
5. `drift check` for each bound document.

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
| M7 | Remove the `keyValue` wrapper from the captured fixture | The Task 3 contract test |

## Documents

- `docs/MCP-ADAPTER.md`: operator configuration, launch, the accepted protocol
  revision, and the pinned client harness.
- `forward-register.md`: the D1 decision, the SDK revision measurement, and the
  Embabel boundary.
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
5. **The Embabel consumer.** A `chat-agent` module may consume these tools.
   That work needs its own design, because Embabel server integration is Spring
   Boot based and uses SSE or Streamable HTTP.