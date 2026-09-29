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

### Decisions taken during Task 4

1. **The rules live in `TopicToolService`, and it holds no MCP type.** The
   allowlist rule, the omit rule and the fail rule sit in one class with no
   server and no transport. `TopicToolRegistration` supplies the schema, the
   annotations and the argument checks. A test drives each layer alone.
2. **The list reads each configured id through its id endpoint.** Rule 2 holds
   by construction. `TopicToolService.listTopics` maps over `config.topicIds`
   and calls `readTopic` once per id. A test asserts that the backend sees each
   configured id exactly once.
3. **`NOT_AVAILABLE` covers a denial and an absence.** The Task 3 client maps
   403 and 404 to one reason, so the omit rule has one branch. A 401, a 5xx and
   a transport failure each keep their own reason and fail the whole list.
4. **Rule 1 cannot be declared through the schema, so the adapter enforces it
   in code.** See decision 11 below. `refuseUnknownArguments` runs before any
   backend call, so no unknown argument reaches a read.
5. **The allowlist check runs after the id rules.** A malformed value fails as
   an id and names the id rule. A well formed id outside the list fails as a
   topic. The order decides which sentence the caller reads.
6. **The blocking backend call runs on the IO dispatcher.** A call on the
   session dispatcher would stall every other frame.
7. **A refusal carries one sentence and no payload.** `ToolException`,
   `ClientException` and `ConfigException` each become an error result whose
   text is the adapter's own sentence. No backend text and no payload reaches
   the client.
8. **The output schema of each tool requires its one field.** The list requires
   `topics`. The single read requires `topic`. An output schema that declares a
   property and does not require it states a weaker contract than the tool
   keeps. The two field names live in `TOPICS_FIELD` and `TOPIC_FIELD`, and the
   tool body and the schema both read them, so the two cannot drift.

### Decision 11: `additionalProperties` at a tool schema root

Rule 1 asks for `additionalProperties: false` on the list tool input. **SDK
0.15.0 cannot declare it there.**

`ToolSchema` is a serializable data class. Its serializer element list is
exactly `$schema`, `properties`, `required`, `$defs`, `type`, read from the
`ToolSchema$$serializer` descriptor. There is no slot for
`additionalProperties`, and `Server.addTool` offers no raw `JsonObject`
overload.

Measured on 2026-09-28 over stdio. The wire declaration of the list tool input
is exactly:

```json
{"properties":{},"required":[],"type":"object"}
```

Four routes were considered and three were refused.

- **A raw JSON schema object.** No SDK entry point takes one.
- **A `properties` entry that names `additionalProperties`.** It would declare
  a property of that name and would not set the keyword.
- **A custom serializer for `ToolSchema`.** It would replace a type the SDK
  owns and would break on the next SDK revision.

The chosen route declares what the SDK can express and enforces the rule in
code. **This is consistent with the owner's Task 2 note that unknown arguments
and properties fail.** The nested per-topic schema **can** carry the keyword,
and the wire test pins it there.

**This limit outlives the phase.** The closure comment of `CHAT-ylvoiixm` must
record it.

### Measured during Task 4

- **The SDK handler is a receiver lambda with one parameter.**
  `suspend ClientConnection.(CallToolRequest) -> CallToolResult`. A two
  parameter form fails to compile.
- **`ToolSchema.properties` and `.required` are nullable in Kotlin.** A test
  that reads them needs `!!`.
- **The wire schema of each tool is exact.** A stdio test pins both input
  schemas and both output schemas as literal JSON text.
- **A denied topic leaves no trace.** The backend answers the denial with a
  body that carries the topic name. The test reads every response frame, the
  structured content, the text content, and stderr. The name appears in none.
  It also asserts that the credential does not reach stderr.

### Task 4 gate results

Measured on 2026-09-28 on branch `chat-mcp-impl`.

| Gate | Result |
|---|---|
| `mvn -o -B -pl chat-mcp -am test` | exit 0. 154 tests, 0 failures, 0 errors, 0 skipped. |
| M4, a denied topic emitted in `chat_list_topics` | `McpAdapterToolStdioTests` fails 1 of 10 and `TopicToolServiceTests` fails 3 of 12. The stdio case reads `expected: <1> but was: <2>`. |
| M6, a topic argument outside the allowlist accepted | `McpAdapterToolStdioTests` fails 1 of 10 and `TopicToolServiceTests` fails 1 of 12. Both name the allowlist case. |
| `shell-scripts/check-dependency-versions.sh` | exit 0. No module declares a third-party version. |
| `git diff --check` | exit 0. |
| `drift check` | exit 0. |
| `shell-scripts/build-health.sh --integration` | exit 0. 29 modules ran 1670 tests, 0 failures, 0 errors, 59 skipped. The run reports that reality matches `docs/BUILD-HEALTH.md`. |

M4 and M6 were applied and restored by absolute path. The service file is
untracked, so `git status` cannot tell a mutated file from a restored one. Each
restore is proved by a `shasum -a 256` match against the value recorded before
the mutation: `76f59e484c0161c3443b29c6b34d132b6be32ac17411188f72282b87f01adcc5`.

The integration gate ran last, on the final source.

### Owner review of Task 4, and the one defect it found

The owner reviewed commit `7ae1cea6` on 2026-09-28. The tool behaviour and the
failure mapping passed. The 403 and 404 omission rule passed.

**One defect blocked. Both output schemas omitted their top-level required
field.** The list tool declared `topics` and did not require it. The single
read declared `topic` and did not require it. The SDK can express both through
`ToolSchema.required`. Decision 8 above is the repair.

The defect was invisible to the earlier tests, because they read
`outputSchema.properties` and never read `outputSchema.required`. The Task 3
decisions did not name the output `required` list either, so nothing at any
layer asked for it. **A schema test that reads one field of a schema proves
nothing about the other fields.**

Measured on 2026-09-29, after the repair:

| Gate | Result |
|---|---|
| `mvn -o -B -pl chat-mcp -am test` | exit 0. 154 tests, 0 failures, 0 errors, 0 skipped. |
| M8, the list output `required` list emptied | `McpAdapterServerTests` fails 1 of 8 and `McpAdapterToolStdioTests` fails 1 of 10. The wire pin and the Kotlin check each catch it. |
| `shell-scripts/check-dependency-versions.sh` | exit 0. |
| `git diff --check` | exit 0. |
| `drift check` | exit 0. |
| `shell-scripts/build-health.sh --integration` | exit 0. 29 modules ran 1670 tests, 0 failures, 0 errors, 59 skipped. The run reports that reality matches `docs/BUILD-HEALTH.md`. |

M8 was applied and restored by absolute path. The service file is tracked, so
the restore is proved by a `shasum -a 256` match against
`0de2f0c40af28eb211608b6607a8e93461251161c3f9a2b5a058f007e3dd9975`, the value
recorded before the mutation and read again after it.

**The integration gate ran on the frozen tree.** An earlier gate run began
before the last two edits to `TopicToolRegistration.kt`, which moved the
`topics` literal to `TOPICS_FIELD` beside it. That run was stopped, because its
reading would have described a superseded tree. The module row, the three fast
gates and the integration gate were then measured on the tree that is
committed, and M8 was proved on that same content.

### Carried forward from the same review

**The watchdog executor of `JdkBackendHttp` has no shutdown path.**
`TopicClient` owns the executor through its transport, and it exposes no close
lifecycle. Task 7 owns it. The item is recorded on `CHAT-oqrifndu`.

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

### Task 5, complete

**What exists.**

| Path | What |
|---|---|
| `chat-mcp/src/test/client/package.json` | The pin. `@modelcontextprotocol/sdk` 1.31.0. |
| `chat-mcp/src/test/client/package-lock.json` | The committed lock file. A test fails when it is absent. |
| `chat-mcp/src/test/client/harness.mjs` | The harness. A Node program with a teeing transport. |
| `chat-mcp/src/test/kotlin/com/demo/chat/mcp/McpAdapterHarnessTests.kt` | Three tests. Every assertion of rules 1 to 9. |
| `docs/MCP-ADAPTER.md` | The operator procedure. |

### What this task proves, and what it does not

**The backend of the automated test is `FakeTopicBackend`.** That is a local
`com.sun.net.httpserver.HttpServer` inside the test JVM. It is not a deployed
Demo Chat server. The adapter process runs over real pipes, so the stdio
behaviour is real. The deployment behind it is not.

The task proves these five things.

1. MCP discovery. The pinned client completes the handshake and lists the tools.
2. Tool calls over stdio. Each call travels the real pipe pair.
3. Protocol framing. Every stdout line is a JSON-RPC 2.0 message.
4. Stream separation. Diagnostics reach stderr, and stdout carries frames alone.
5. JVM process shutdown. The process exits within the bound after stdin closes.

The task does not prove these three things.

1. Production REST authentication. The fake backend checks no credential.
2. Production route behaviour. The fake backend serves two fixed bodies. A real
   deployment maps a route, a key type and an index.
3. Deployed authorization. The fake backend answers 403 for one id by
   construction. No grant and no access broker took part.

**A reader must not read this task as end-to-end MCP support.** Task 8 carries
the real-deployment acceptance step. Authorization closes later, under
`CHAT-pgpmsgvr`.

### Decision 12: the harness needs a custom transport

The shipped `StdioClientTransport` exposes the child stderr and no raw stdout.
Rule 6 needs the exact bytes the adapter wrote. So the harness implements the
`Transport` interface itself. It spawns the child with `child_process.spawn`,
records every stdout line before the protocol reads it, and records the stderr
text.

**A custom transport is the only way to read raw stdout.** The shipped one
consumes the stream.

### Decision 13: the harness asserts nothing

The harness prints one JSON transcript and makes no assertion. The Kotlin test
reads the transcript and decides. So a reader can keep a transcript as evidence
without running the JVM test.

### A production defect that rule 6 found

**`kotlin-logging` 8.0.4 wrote its startup banner to stdout.** The line is
`kotlin-logging: initializing... active logger factory: Slf4jLoggerFactory`. It
is not a protocol frame, so it broke the stdio contract of the adapter.

The library arrives through the MCP SDK, at runtime scope. The adapter never
logs through it. It prints that one line when its configuration class
initializes.

The adapter now sets the library property `kotlin-logging.logStartupMessage` to
`false`. `main` calls that before every other statement, because the library
reads the property once, at class initialization.

**The property is set in code, not in a launch script.** Every launch path
inherits it. This repository has met the other shape before, under
`CHAT-gkwqnnxn`, where one launch path defaulted a value and the other did not.

The mutation M5b proves the guard. Removing the call puts the line back on
stdout and the purity test names it.

### The recorded transcript

Measured on 2026-09-29 against the memory deployment of the prerequisite
section, at `http://127.0.0.1:6791`. The topic is `mcpcontracttopic`.

```
node v26.7.0 | sdk 1.31.0 | declared 2025-11-25 | negotiated 2025-11-25
server {"name":"demo-chat-mcp","version":"0.0.1"} | caps {"tools":{"listChanged":true}}
tools: chat_list_topics, chat_get_topic
call chat_list_topics {} isError=false
  => {"topics":[{"id":"1554361326074068992","root":"1554361143634427905","name":"mcpcontracttopic"}]}
call chat_get_topic {"topicId":"1554361326074068992"} isError=false
  => {"topic":{"id":"1554361326074068992","root":"1554361143634427905","name":"mcpcontracttopic"}}
call chat_get_topic {"topicId":"1"} isError=true => "the backend answered 404"
stdout lines 5 | parse failures 0
stderr ["chat-mcp: configured for http://127.0.0.1:6791, 2 topic ids, key type LONG",
        "SLF4J(W): No SLF4J providers were found.",
        "SLF4J(W): Defaulting to no-operation (NOP) logger implementation",
        "SLF4J(W): See https://www.slf4j.org/codes.html#noProviders for further details.",
        "chat-mcp: ready, protocol revision is chosen by the SDK",
        "chat-mcp: chat_list_topics answered",
        "chat-mcp: chat_get_topic answered",
        "chat-mcp: chat_get_topic refused: the backend answered 404",
        "chat-mcp: stdin closed, exiting"]
exit {"withinBound":true,"millis":325,"code":0,"signal":null} | connectError null
```

Three readings.

1. **The pinned client negotiates the served revision.** The client declares
   `2025-11-25` and the adapter answered the same value. Decision D1 holds on a
   real client.
2. **Five stdout lines carry the whole session, and nothing else.** One is the
   initialize answer, one is the discovery answer, and three are the call
   answers. The test pins the count, so a stray line fails it.
3. **The adapter ships no SLF4J provider.** The SDK logging is a no-op, and the
   three `SLF4J(W)` lines reach stderr alone.

**Four limits of this transcript.** It was run by hand. It is not a gate, and
no test repeats it.

1. **It is one manual run.** A gate repeats. This ran once.
2. **It reads one route.** The transcript reads a topic by id. It does not
   exercise a write, a search or a denial from a real grant.
3. **The deployment checks no credential.** `WebFluxSecurity` permits every
   exchange and adds no authentication, as `forward-register.md` records. So
   the transcript does not prove REST authentication. It proves that an
   unauthenticated read reaches a topic.
4. **It proves one real backend answer, and it is a 404.** The configuration
   named two ids, `1554361326074068992` and `1`. Both passed the allowlist, so
   both reached the deployment. The deployment served the first and answered
   `404` for the second. So the refusal text on the third line is the
   deployment's own answer, mapped by the adapter. That is the strongest part
   of the evidence here, and it is also its whole extent.

One observation follows from the same run. `chat_list_topics` answered one
topic although the allowlist held two. The second id was left out with no name
and no count, which is the documented behaviour for a topic the deployment does
not serve.

The transcript is useful confirmation. It is not end-to-end acceptance.

### Rule 10

Optional confirmation by a Claude Code session was not recorded. It is not the
required proof.

### What the harness needs from the machine

- **Node on the PATH.** The test fails with a sentence that names Node when it
  is absent. It does not skip.
- **`npm ci` once.** The lock file is committed and `node_modules/` is ignored.
  A cold machine needs the network for that step.

### Task 5 gate results

Measured on 2026-09-29 on branch `chat-mcp-impl`.

| Gate | Result |
|---|---|
| `mvn -o -B -pl chat-mcp -am test` | exit 0. 157 tests, 0 failures, 0 errors, 0 skipped. |
| M5, a diagnostic line routed to stdout | `McpAdapterHarnessTests` fails 1 of 3. The purity test names five offending lines. |
| M5b, the logging banner suppression removed | `McpAdapterHarnessTests` fails 1 of 3. The purity test names `kotlin-logging: initializing... active logger factory: Slf4jLoggerFactory`. |
| `shell-scripts/check-dependency-versions.sh` | exit 0. No module declares a third-party version. |
| `git diff --check` | exit 0. |
| `drift check` | exit 0. |
| `shell-scripts/build-health.sh --integration` | exit 0. 29 modules ran 1673 tests, 0 failures, 0 errors, 59 skipped. Reality matches `docs/BUILD-HEALTH.md`. |

The test count moved from 1670 to 1673, which is the three tests this task adds.

M5 and M5b were applied and restored by absolute path. Each restore is proved
by a `shasum -a 256` match against the value recorded before the mutation. All
four frozen files match.

The integration gate ran last, on the final source.

### Two limits of this task, recorded

1. **A cold machine needs one manual step.** It runs `npm ci` in
   `chat-mcp/src/test/client/` before the test can pass. The lock file is
   committed, so the step is reproducible. It is still a step. The test names
   the missing package when `node_modules` is absent, because it prints the
   harness error text.
2. **The acceptance test is not the native test.** Every call in this task ran
   on the JVM build. Task 6 runs the same harness against the native
   executable, and that run is the native proof.

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

### Task 7, complete

Run on 2026-09-29 on branch `chat-mcp-impl`. Issue `CHAT-oqrifndu`.

`chat-mcp/src/main/kotlin/com/demo/chat/mcp/error/ToolError.kt` holds the code
set and the classifier. `TopicToolRegistration.answer` holds the one dispatcher
that every tool answers through.

| Rule | What carries it |
|---|---|
| 1. Protocol error for a malformed envelope | The SDK. `McpAdapterToolStdioTests` reads the JSON-RPC `error` and asserts no `result` |
| 2. A tool error result carries three fields | `ToolError.toMeta` writes `code`, `message` and `retryable` flat |
| 3. The six codes | `ToolErrorCode` is closed. `ToolErrorTests` proves the table covers `FailureReason.entries` |
| 4. No raw backend text, and no hidden object | `ToolError.messageOf` gives every code one fixed sentence, and it takes the code alone. `ToolError.of` reads `failure.reason` alone. The caller `TopicToolRegistration.answer` reads `failure.status` separately for the stderr line. No path reads `failure.message`. A 403 and a 404 answer one sentence alike |
| 5. Five fields on stderr | `TopicToolRegistration.report` writes tool, correlation id, duration, code and status |
| 6. No token and no message text | `McpAdapterHarnessTests` asserts no topic name and no credential on either stream |

**Two codes have no producer, and that is recorded.** `FEATURE_UNAVAILABLE` and
`OUTCOME_UNKNOWN` wait for the recall and send tools. No path invents one.

**`retryable` is true for a transport failure alone.** A failed send must always
answer false, because the adapter holds no deduplication contract. No send tool
exists yet, so that rule binds the tool that adds one.

**A cancellation is rethrown and not answered.** `CancellationException` is a
kind of `Exception`, so the last branch would otherwise answer a cancelled call
as a backend failure. Mutation M7a proves the guard.

### The transport ownership decision

The owner comment on `CHAT-oqrifndu` asked whether the adapter owns one
transport for the process, or one for each client.

**The adapter owns one transport for the process.** The design sets
`MAX_CONCURRENT_REQUESTS` to 4 per adapter process. A transport for each client
would make that limit belong to one client, so four clients would hold sixteen
requests and the bound would mean nothing.

`BackendHttp` extends `AutoCloseable`. `serveStdio` closes the transport in a
`finally` block, so the watchdog executor and its thread are released on every
path. `runStdioAdapter` is the process wrapper, and it is the only caller that
exits.

**The close sits in `serveStdio` and not in `runStdioAdapter`**, because a test
must drive the serve path without ending the JVM. Mutation M7f proves the
close.

### Two defects the tests found

1. **The adapter separated a hidden object from an absent one.** The first
   `ToolError.of` passed the backend message through, so a 404 answered
   `the backend answered 404` and a 403 answered `the backend answered 403`.
   Task 7 rule 4 forbids that difference. The planned test
   `the backend status stays out of the application data` failed before it was
   ever green, and it earned its place. `NOT_AVAILABLE_MESSAGE` repairs it.
   Mutation M7b proves the guard. **The first repair covered `NOT_AVAILABLE`
   alone**, and the pass-through survived on the other five codes. See the
   repair section below.
2. **`_meta` is the wire name.** The SDK field is `meta` in Kotlin and `_meta`
   on the wire. `javap -v` on the SDK class shows the constant pool entry
   `value="_meta"`. The harness reads `result["_meta"]`, so the name is measured
   against a real client rather than assumed.

### The Task 7 repair, after the owner review

**Task 7 was rejected on 2026-09-29, and the reason was correct.**
`ToolError.messageFor` answered `failure.message` for every code except
`NOT_AVAILABLE`. A `ClientException` carries the message its throw site built.
A transport builds that message from the material it handled, so it can hold a
URL, a header, a stored value or a credential fragment. **That text reached
`_meta.message` and the tool content.** Rule 4 forbids it.

**The repair removes the path and not the sentences alone.** `messageOf(code)`
holds one fixed sentence for each code. It takes the code alone, so no failure
value is in scope. **`of` reads `failure.reason` alone**, because the reason is
what selects the code. A `when` with no `else` is the carrier, so a new code
fails the compile until a sentence exists for it. A map would answer a missing
key at run time.

**The two other fields of a `ClientException` are read elsewhere, and each
keeps its own purpose.**

| Field | Reader | Purpose |
|---|---|---|
| `reason` | `ToolError.of` | Selects the code, and decides `retryable` |
| `status` | `TopicToolRegistration.answer`, which passes it to `refused` | The `status=<s\|->` field of the stderr diagnostic line |
| `message` | **No production path** | A debugger and a stack trace alone |

**`of` does not read `status`.** The status must not reach a client, because a
403 and a 404 have to answer alike. It reaches stderr alone, and the caller
carries it there. An earlier version of this document said `of` read both
fields, which was false about the implementation and wrong about the design.

The five sentences that were not `NOT_AVAILABLE`:

| Code | Sentence |
|---|---|
| `AUTHENTICATION_REQUIRED` | `the backend refused the credential of this adapter` |
| `FEATURE_UNAVAILABLE` | `the backend does not offer a feature this call requires` |
| `BACKEND_UNAVAILABLE` | `the backend did not answer the call` |
| `LIMIT_EXCEEDED` | `the call passed a limit of this adapter` |
| `OUTCOME_UNKNOWN` | `the adapter cannot tell whether the call completed` |

**Two messages stay outside `messageOf`, and each is correct.** `refused`
carries the sentence of a `ToolException` or a `ConfigException`. The adapter
wrote that sentence, and it names the argument to correct. `internalFailure`
carries the sentence of an unplanned failure, which held no backend answer at
all. **The rule is one sentence, and its source is stated.** A message is the
fixed sentence of its code, or adapter prose for a failure the adapter raised.
No message is copied from a backend exception.

**`ClientException` now states where its message goes.** The KDoc reads that
the message is for a debugger and a stack trace alone, and that it reaches no
client and no log line.

Two regression tests carry the guard.

- `ToolErrorTests.no failure class repeats the backend exception text` runs
  every `FailureReason` with a distinguishing token in the message, and asserts
  the token reaches neither the message nor `toMeta()`.
- `ToolAnswerContractTests.a backend failure exposes no backend exception text`
  drives the handler, and asserts the token reaches neither the content nor the
  application data. That is the layer where both fields are shaped.

Two further tests hold the shape. `every failure code answers its own fixed
sentence` asserts six distinct non-blank sentences.
`the sentence of a failure does not depend on the exception text` asserts two
exceptions with different text and one reason answer one value.

**The stdio test changed with the repair.** `a backend failure answers an error`
asserted `contains("the backend answered 500")`, which was the leak. It now
asserts the fixed sentence and asserts that neither `500` nor the backend body
reaches the answer.

## Task 8: real-deployment acceptance

Task 5 proves the protocol against a fake backend. This task proves the adapter
against a deployment that actually runs. **No end-to-end claim is valid until
this task passes.**

**Files.** A recorded transcript under `docs/`. An operator procedure in
`docs/MCP-ADAPTER.md`.

1. Start a real deployment. Use the recipe in the prerequisite section.
2. Create a topic through the deployment. Record its id, its root and its name.
3. Write an operator configuration that names that id, and one id the
   deployment does not serve.
4. Run the pinned Task 5 harness against the adapter, over stdio, with the
   deployment behind it.
5. Confirm discovery lists both tools.
6. Confirm the served topic comes back with the id, the root and the name the
   deployment holds.
7. Confirm the unserved id answers a refusal. Confirm the refusal carries no
   backend exception text and no name.
8. Confirm stdout carries protocol frames alone and stderr carries one
   diagnostic line per call.
9. Confirm the process exits within the bound after stdin closes.
10. Record the transcript. State the deployment, the revision and the date.

**Acceptance criteria.**

- The transcript names a real deployment and a real topic.
- Discovery and both tool calls complete against that deployment.
- The unserved id refuses with no backend text.
- stdout purity holds on the real path.

**One boundary this task cannot cross.** No deployment enforces a credential
today. `WebFluxSecurity` permits every exchange and states that it adds no
authentication, and `RSocketServerConfiguration` carries `TODO: lock down!`.
Both are recorded in `forward-register.md`. So this task proves the adapter
against real routes, a real key type and a real index. **It does not prove
production REST authentication**, because no deployment asks for one yet. Do
not claim it. That proof waits for `CHAT-pgpmsgvr`, which enforces the
authentication boundary for every route the adapter uses. It depends on
`CHAT-znprrzhn`, which wires the authorization checks to a real bean.

### Task 8, complete

Run on 2026-09-29. Recorded in `docs/MCP-REAL-DEPLOYMENT-ACCEPTANCE.md`.

The deployment was `chat-deploy-memory`, started 2026-09-28 22:16:12 on port
6791. The topic `mcprealacceptance` was created through the deployment and
carries id `1554429686883287040` and root `1554361143634427905`.

| Step | Result |
|---|---|
| 1. Real deployment | Memory deployment, port 6791, OpenJDK 25 |
| 2. Topic created through it | id `1554429686883287040`, root `1554361143634427905`, name `mcprealacceptance` |
| 3. Configuration | `topicIds=1554429686883287040,1`, `backendBaseUrl=http://127.0.0.1:6791` |
| 4. Harness run over stdio | Exit 0, `connectError` null |
| 5. Discovery | `chat_list_topics`, `chat_get_topic` |
| 6. Served topic | id, root and name all match the deployment |
| 7. Unserved id | `isError=true`, code `NOT_AVAILABLE`. The backend sentence `Key 1 is not in the registry.` appears in no stream |
| 8. Streams | Five stdout frames, all JSON-RPC 2.0, zero parse failures. Six stderr lines, all prefixed `chat-mcp: `. Three of them are the call diagnostics, one per call |
| 9. Bounded exit | `withinBound=true`, 335 ms, code 0 |
| 10. Transcript | Recorded with the deployment, the revision and the date |

**The run was repeated after Task 7**, because Task 7 changed the refusal
sentence and the diagnostic line. The table above carries the second run. The
first transcript is superseded, and `docs/MCP-REAL-DEPLOYMENT-ACCEPTANCE.md`
records both side by side.

**Two documentation defects were found after the first run, and both are
repaired.** The unserved-id section said id `1` serves, where it is absent. The
operator document said every stderr line carries a `chat-mcp: ` prefix, and the
first transcript carried three `SLF4J(W):` lines. The adapter now suppresses
those lines, and the harness test asserts the prefix on every stderr line.
Mutation M5c proves that assertion.

**One reading worth keeping.** Both ids sat in `topicIds`. An allowlist
refusal never reaches a backend, so it would have proved nothing about the
deployment. The refusal in this run is the deployment's own 404, and the
adapter replaced its text.

**Two things this task did not prove, and both stay open.**

1. **Production REST authentication.** No deployment enforces a credential.
2. **A real denial.** The deployment holds no grant that denies, so no 403
   from a real store appears in this run. Task 4's stdio test is the only
   coverage of the denial path.

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
| M8 | Omit a top-level `required` list from an output schema | The Task 4 schema tests |
| M5b | Remove the logging banner suppression from `main` | Task 5 purity test |
| M5c | Remove the SLF4J verbosity suppression from `main` | Task 5 stderr purity test |
| M7a | Remove the `CancellationException` rethrow from the dispatcher | `ToolAnswerContractTests.a cancellation propagates and is not answered` |
| M7b | Keep the raw backend message for `NOT_AVAILABLE` | `ToolErrorTests.the not available sentence names no backend status` and `ToolErrorTests.a denied object and an absent object answer the same error` |
| M7c | Report `retryable = true` for every failure | `ToolErrorTests.only a transport failure is retryable` |
| M7d | Drop the final catch-all branch | `ToolAnswerContractTests.an unplanned failure exposes no exception text` |
| M7e | Drop the `status` field from the diagnostic line | `McpAdapterHarnessTests.stdout is pure, stderr is one line per call, and the exit is bounded` |
| M7f | Drop the transport close from `serveStdio` | `McpAdapterStdioTests.the stdio path closes the transport it owns` |
| M7g | Return `failure.message` from `ToolError.of` again | `ToolErrorTests.no failure class repeats the backend exception text`, `ToolErrorTests.the sentence of a failure does not depend on the exception text` and `ToolAnswerContractTests.a backend failure exposes no backend exception text` |

M7g was added on 2026-09-29, after the owner review of Task 7. It is the
mutation that proves the repair above. Measured: 8 failures over three classes,
and every named test is among them. The source line was read back before the
run, which is the M7e lesson applied.

M5b was added during Task 5. It was not in the original table, because nobody
knew the logging library wrote to stdout. See the Task 5 section.

M5c was added during Task 8, after the real run showed three `SLF4J(W):` lines
on stderr. The test names all three lines. See the correction section in
`docs/MCP-REAL-DEPLOYMENT-ACCEPTANCE.md`.

M5, M5b and M5c were measured on 2026-09-29. All restored, and `shasum -a 256`
proved each restore.

M7a to M7f were measured on 2026-09-29 during Task 7. Each mutation was
applied, its named test was run, and the file was restored by absolute path.
`shasum -a 256` proved every restore against the value recorded before it.

| File | SHA-256 |
|---|---|
| `chat-mcp/src/main/kotlin/com/demo/chat/mcp/tool/TopicToolRegistration.kt` | `2695e41b22f12858afaed8055c8b841290278467268434e605235aa7dd777de3` |
| `chat-mcp/src/main/kotlin/com/demo/chat/mcp/error/ToolError.kt` | `ac7d30a8d9ada2db9de0834069ce9239eaf51288a149f22b56dcc2da55ab6d03`, before the repair. After the repair and the M7g restore: `adbc94bc4047e8a5ce87df16aa747f0a5ccd31df8442d55ec578104578ff605d` |
| `chat-mcp/src/main/kotlin/com/demo/chat/mcp/McpAdapterMain.kt` | `6a0d0416c2ab1f8f1be39e046cd156b63ac162be2c91ad240955b9f83914324a` |

**One mutation taught a lesson about the mutation itself.** The first M7e
attempt changed nothing. A `perl` pattern did not match, maven exited 0 on
unchanged source, and a clean run read as a proven mutation. The second attempt
read the changed line back before it ran the test. **A mutation proof must show
the mutated source**, or it proves only that the test passes on the original.

## Documents

- `docs/MCP-ADAPTER.md`: operator configuration, launch, the accepted protocol
  revision, and the pinned client harness.
- `docs/MCP-REAL-DEPLOYMENT-ACCEPTANCE.md`: the Task 8 run, its transcript,
  and the boundary that stays open.
- `forward-register.md`: the D1 decision, the SDK revision measurement, and the
  Embabel boundary.
- `docs/BUILD.md`: the `chat-mcp` build and native commands, if the module
  needs one.

## Closure

`CHAT-ylvoiixm` closes when tasks 1 to 8 pass. Its closing comment names each
test, each mutation result and each gate result. It also records the SDK
revision limit, because that limit outlives this phase.

**Task 8 is the end-to-end gate.** Tasks 1 to 7 prove the adapter against a
fake backend. Task 8 proves it against a deployment. A closure comment that
claims end-to-end MCP support without a Task 8 transcript is wrong.

The authentication boundary stays open after this phase, because no deployment
enforces a credential. `CHAT-pgpmsgvr` closes it, and `CHAT-ylvoiixm` cannot
claim end-to-end MCP authorization until that issue passes. See Task 8.

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