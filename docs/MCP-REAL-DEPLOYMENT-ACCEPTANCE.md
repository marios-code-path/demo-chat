# Real-deployment acceptance for the MCP adapter

**The historical runs through 2026-10-01 used single-agent keys.** Since
`CHAT-frcrctdp`, a launch names each agent with `app.security.agents[n]` and
`app.security.required-scope`. The old keys fail the start. See
`docs/MCP-CREDENTIAL-ISSUANCE.md`.

This document records dated acceptance runs against Demo Chat deployments.
The initial run used the earlier two-tool adapter. Issue `CHAT-spbwlamz` carries the work. Task 8 of
`docs/superpowers/plans/2026-09-28-demo-chat-mcp-feasibility.md` holds the step
list.

The automated test `McpAdapterHarnessTests` runs against `FakeTopicBackend`, a
local HTTP server inside the test JVM. This run replaces that backend with a
deployment.

## What this run proves, and what it does not

The run proves six things.

1. Discovery lists both tools against a real server.
2. Each tool call travels the real stdio pipe pair.
3. A real deployment serves the topic, and the answer carries its real id, root
   and name.
4. A real deployment answers 404 for an id it does not hold.
5. The adapter maps that 404 to its own sentence, and it leaks no backend text.
6. stdout carries protocol frames alone. The process exits within the bound.

**The 2026-09-29 run predates enforcement, and a second run closes that gap.**
`CHAT-pgpmsgvr` merged on 2026-09-30, and the chain now validates an agent token
on every route it owns. `RSocketServerConfiguration` stays outside that issue
and keeps `TODO: lock down!`.

The 2026-09-29 deployment started on 2026-09-28, before that merge. Its
`WebFluxSecurity` permitted every exchange. So its token came from a local mint
and that token was unconstrained: **any text in `credential.txt` would have
passed.** Read that run for its transport, route and mapping evidence, and not
for authentication.

**The 2026-09-30 run in this document closes both gaps.** It uses a token from
the authorization server, and it runs against a deployment that enforces. The
procedure that produces that token is in `docs/MCP-CREDENTIAL-ISSUANCE.md`.

Neither run proves deployed authorization. No deployment holds a denial to
observe, because no route enforces a grant. So every call in both runs was
permitted.

## The deployment

| Item | Value |
|---|---|
| Module | `chat-deploy-memory` |
| Started | 2026-09-28 22:16:12, by the prerequisite recipe in the plan |
| Application port | 6791, listening on `http://127.0.0.1:6791` |
| RSocket port | 6790 |
| Runtime | OpenJDK 25, GraalVM CE |
| Key type | `long` |
| Node id | 0 |
| Selectors | `key`, `persistence`, `pubsub` and `secrets` at `memory`. `index` at `lucene`. |
| Profile list | `-Pdeploy,expose-webflux` |
| Extra flags | The golden `core-memory-init` set, plus `-Dapp.controller.topic` |

The deployment started before the Task 5 commits. It does not contain
`chat-mcp`. The adapter is a separate program and reaches the deployment over
HTTP.

The deployment resolves its modules from the local repository. It reads
`chat-deploy-memory/target/classes` first, and the rest from `~/.m2`.

## The topic

The topic was created through the deployment on 2026-09-29.

```sh
TOKEN=$(shell-scripts/agent-token.py /abs/path/server_keycert.jwk <client-id> chat.mcp)
curl -H "Authorization: Bearer $TOKEN" \
  -X PUT http://127.0.0.1:6791/persist/topic/add \
  -H 'Content-Type: application/json' \
  -d '{"type":"ByNameRequest","name":"mcprealacceptance"}'
```

```
HTTP/1.1 201 Created
{"key":{"id":1554429686883287040,"root":1554361143634427905,"empty":false}}
```

| Field | Value |
|---|---|
| id | `1554429686883287040` |
| root | `1554361143634427905` |
| name | `mcprealacceptance` |

The name is one token. A hyphen splits the Lucene field, and two names that
share one token match one query. `CHAT-hajmhslp` holds that defect, so this run
avoids it.

The adapter reads through its own route, `GET /topic/id/{id}`. That route
answered 200 for the topic:

```
{"keyValue":{"data":"mcprealacceptance","key":{"key":{"id":1554429686883287040,"root":1554361143634427905,"empty":false}}}}
```

## The unserved id

The run needs one id the deployment does not hold. Id `1` is absent from the
deployment. It passes the adapter allowlist, so it reaches the deployment. The
deployment answers:

```
HTTP/1.1 404 Not Found
Key 1 is not in the registry.
```

That sentence is the backend text the adapter must not repeat. The adapter's
own refusal is `the backend does not serve this object, or it refuses this
caller`, and its code is `NOT_AVAILABLE`.

**The adapter gives a denied object the same answer.** A 403 from this
deployment would carry that same sentence and that same code. A reader that
could tell the two apart would learn that a hidden object exists. The backend
status is not lost, because the stderr diagnostic line carries it. Task 7 rule
4 requires this, and mutation M7b proves the guard.

## The configuration

```properties
backendBaseUrl=http://127.0.0.1:6791
credentialFile=credential.txt   (a locally minted token; the 2026-09-29 deployment read no header)
keyType=long
topicIds=1554429686883287040,1
```

Both ids are in `topicIds` on purpose. A refusal from the allowlist never
reaches a backend, so it would prove nothing about the deployment.

## The run

The adapter ran on the current source of branch `chat-mcp-impl`, from
`chat-mcp/target/classes`.

```sh
node harness.mjs --read-id 1554429686883287040 --refused-id 1 -- \
  java -cp <chat-mcp classes and dependencies> \
    com.demo.chat.mcp.McpAdapterMainKt --config adapter.properties
```

The harness exited 0 and wrote no error.

## The transcript

Measured on 2026-09-29. Node v26.7.0. Client `@modelcontextprotocol/sdk`
1.31.0.

```
node v26.7.0 | sdk 1.31.0 | declared 2025-11-25 | negotiated 2025-11-25
server {"name":"demo-chat-mcp","version":"0.0.1"} | caps {"tools":{"listChanged":true}}
tools: chat_list_topics, chat_get_topic
call chat_list_topics {} isError=false _meta=null
  => {"topics":[{"id":"1554429686883287040","root":"1554361143634427905","name":"mcprealacceptance"}]}
call chat_get_topic {"topicId":"1554429686883287040"} isError=false _meta=null
  => {"topic":{"id":"1554429686883287040","root":"1554361143634427905","name":"mcprealacceptance"}}
call chat_get_topic {"topicId":"1"} isError=true
  _meta {"code":"NOT_AVAILABLE","message":"the backend does not serve this object, or it refuses this caller","retryable":false}
stdout lines 5 | parse failures 0
stderr ["chat-mcp: configured for http://127.0.0.1:6791, 2 topic ids, key type LONG",
        "chat-mcp: ready, protocol revision is chosen by the SDK",
        "chat-mcp: chat_list_topics answered call=1 duration=51ms code=OK status=200",
        "chat-mcp: chat_get_topic answered call=2 duration=5ms code=OK status=200",
        "chat-mcp: chat_get_topic refused: the backend does not serve this object, or it refuses this caller call=3 duration=5ms code=NOT_AVAILABLE status=404",
        "chat-mcp: stdin closed, exiting"]
exit {"withinBound":true,"millis":335,"code":0,"signal":null} | connectError null
```

## The criteria, one by one

| Task 8 criterion | Reading |
|---|---|
| Discovery lists both tools | `tools: chat_list_topics, chat_get_topic` |
| The served topic carries the real id, root and name | All three match the deployment's answer |
| The unserved id answers a refusal | `isError=true`, code `NOT_AVAILABLE` |
| The refusal carries no backend text | The string `is not in the registry` appears in neither stream |
| The refusal carries no name | The refused call returns no name and no count |
| stdout carries protocol frames alone | Five lines, all JSON-RPC 2.0, zero parse failures |
| stderr carries adapter diagnostics alone | Six lines, and every one starts with `chat-mcp: ` |
| One diagnostic line per call | Three call lines in stderr, one per call |
| The process exits within the bound | `withinBound=true`, 335 ms, code 0 |

## Two further readings

1. **The negotiated revision is the served revision.** The client declared
   `2025-11-25` and the adapter answered the same value. Decision D1 holds
   against a real deployment, not only against the fake backend.
2. **A refused id is left out of the list answer with no trace.**
   `chat_list_topics` answered one topic although `topicIds` held two. The
   unserved id appears with no name and no count. That is the documented
   behaviour for a topic the deployment does not serve.

## The Task 7 rerun

Task 7 changed the failure contract and the diagnostic line. The transcript
above is the rerun against the same deployment, the same topic and the same
configuration, on the same day.

| Field | First run | This run |
|---|---|---|
| Refusal sentence | `the backend answered 404` | `the backend does not serve this object, or it refuses this caller` |
| Refusal code | none. No code existed | `NOT_AVAILABLE` on the wire, under `_meta` |
| `retryable` | none | `false` |
| A good answer | no error data | no `_meta` at all |
| Diagnostic line | `chat-mcp: chat_get_topic answered` | `chat-mcp: chat_get_topic answered call=2 duration=5ms code=OK status=200` |

**The status reaches stderr alone.** The refused call answered `status=404` on
its diagnostic line, and no backend status appears in the client answer.

**The first run is superseded.** A reader must not quote its refusal sentence.
The diagnostic shape in the first transcript is stale in the same way.

**The Task 7 repair changed no answer in this run, so the transcript stands.**
The repair gave every failure code a fixed sentence. The refusal here is
`NOT_AVAILABLE`, which already carried a fixed sentence before the repair. So
every line above still holds, and no rerun is needed. The transcript exercises
no other failure code. The repair is proved by `ToolErrorTests` and
`ToolAnswerContractTests`, and by mutation M7g in the plan.

## One correction this run forced

The first run carried three `SLF4J(W):` warning lines in stderr. The operator
document said every stderr line starts with `chat-mcp: `. That sentence was
false, and the transcript showed it.

`slf4j-api` 2.0.18 reaches the classpath through the MCP SDK and no provider
binds to it. The adapter now sets `slf4j.internal.verbosity` to `ERROR`, beside
the `kotlin-logging` suppression it already set. Both run before any other
class loads.

**The harness test guards the claim.** It asserts that every stderr line
carries the `chat-mcp: ` prefix. That assertion failed before the repair and
named all three lines. See mutation M5c in the plan.

## What a reader must not conclude

This run does not close `CHAT-ylvoiixm`. Three things stay open.

1. **Authentication, for the 2026-09-29 run.** `CHAT-pgpmsgvr` enforces the
   credential boundary for every route the adapter uses. That deployment
   predates the merge, so it read no header. The 2026-09-30 run closes this.
2. **The denial path.** The deployment holds no denial to observe. So neither
   run exercised a 403 from a real grant. Task 4's stdio test covers a 403
   against the fake backend. That test is the only coverage of the denial path.
3. **The credential origin, for the 2026-09-29 run.** That run minted a token
   locally from the trusted JWK. `CHAT-rvcrzxvw` closed this on 2026-09-30, and
   `docs/MCP-CREDENTIAL-ISSUANCE.md` holds the procedure.

## The 2026-09-30 run, with an issued credential

The first run used a local mint against a deployment that read no header. This
run uses a token from the authorization server, against a deployment that
enforces. Issue `CHAT-rvcrzxvw` carries the work.

The credential procedure is `docs/MCP-CREDENTIAL-ISSUANCE.md`. That document
carries the token request, the refusal matrix, the expiry rule and the limits.
This section records the acceptance reading alone.

### The deployment

| Item | Value |
|---|---|
| Module | `chat-deploy-memory`, from its `-exec` jar |
| Built | 2026-09-30, with the `expose-webflux` and `deploy` Maven profiles |
| Application port | 6892, listening on `http://127.0.0.1:6892` |
| Management port | 6893 |
| Runtime | OpenJDK 25, GraalVM CE |
| Key type | `long` |
| Node id | 1 |
| Spring profile | none. The default profile is active. |
| Selectors | `key`, `persistence`, `pubsub` and `secrets` at `memory`. `index` at `lucene`. |
| Agent account | `Admin`, resolved at startup through `ChatUserService` |
| Enforced scope | `chat.mcp` |
| Client id | `31649af5-0154-4be5-8695-fda9d18b7981` |

Two facts about that jar were read from it, not assumed. Its `BOOT-INF/lib`
holds `chat-webflux-0.0.1.jar`, so the `expose-webflux` profile was active. That
directory holds `chat-deploy-0.0.1.jar` at 73 KiB, so the exec classifier kept
the library small.

The flag list is in the appendix of `docs/MCP-CREDENTIAL-ISSUANCE.md`.

### The credential

The authorization server ran with the `memory` profile on port 9000, and it held
the private half of the key the deployment trusts. The token came from one
`client_credentials` request with `scope=chat.mcp`.

Measured token claims: `scope` is `["chat.mcp"]`, `client_id` equals the client
id above, `aud` equals it too, and the lifetime is 300 seconds. The header
carries `alg` `ES256`.

**The file was not the only variable.** A control run replaced the token with
junk text and ran the same harness again. Every call then answered
`AUTHENTICATION_REQUIRED` with `status=401`. So the 200 answers below came from
the credential, and not from a deployment that ignores it.

### The topic

The topic was created through the deployment on 2026-09-30, with the issued
token on the request.

```sh
curl -H "Authorization: Bearer $TOKEN" -X PUT \
  http://127.0.0.1:6892/persist/topic/add \
  -H 'Content-Type: application/json' -d '{"type":"ByNameRequest","name":"mcpcredential"}'
```

```
HTTP/1.1 201 Created
{"key":{"id":1555003941702340608,"root":1555003879521783809,"empty":false}}
```

| Field | Value |
|---|---|
| id | `1555003941702340608` |
| root | `1555003879521783809` |
| name | `mcpcredential` |

The name is one token, for the Lucene reason above.

### The transcript

Measured on 2026-09-30. Node v26.7.0. Client `@modelcontextprotocol/sdk`
1.31.0.

```
node v26.7.0 | sdk 1.31.0 | declared 2025-11-25 | negotiated 2025-11-25
server {"name":"demo-chat-mcp","version":"0.0.1"} | caps {"tools":{"listChanged":true}}
tools: chat_list_topics, chat_get_topic
call chat_list_topics {} isError=false _meta=null
  => {"topics":[{"id":"1555003941702340608","root":"1555003879521783809","name":"mcpcredential"}]}
call chat_get_topic {"topicId":"1555003941702340608"} isError=false _meta=null
  => {"topic":{"id":"1555003941702340608","root":"1555003879521783809","name":"mcpcredential"}}
call chat_get_topic {"topicId":"1"} isError=true
  _meta {"code":"NOT_AVAILABLE","message":"the backend does not serve this object, or it refuses this caller","retryable":false}
stdout lines 5 | parse failures 0
stderr ["chat-mcp: configured for http://127.0.0.1:6892, 2 topic ids, key type LONG",
        "chat-mcp: ready, protocol revision is chosen by the SDK",
        "chat-mcp: chat_list_topics answered call=1 duration=64ms code=OK status=200",
        "chat-mcp: chat_get_topic answered call=2 duration=8ms code=OK status=200",
        "chat-mcp: chat_get_topic refused: the backend does not serve this object, or it refuses this caller call=3 duration=7ms code=NOT_AVAILABLE status=404",
        "chat-mcp: stdin closed, exiting"]
exit {"withinBound":true,"millis":343,"code":0,"signal":null} | connectError null
```

**No call answered `AUTHENTICATION_REQUIRED`.** That code is absent from the
transcript, from the stderr lines and from the exit record.

The same criteria table applies, and every row reads the same way.

| Task 8 criterion | Reading |
|---|---|
| Discovery lists both tools | `tools: chat_list_topics, chat_get_topic` |
| The served topic carries the real id, root and name | All three match the deployment's answer |
| The unserved id answers a refusal | `isError=true`, code `NOT_AVAILABLE` |
| The refusal carries no backend text | The string `is not in the registry` appears in neither stream |
| The refusal carries no name | The refused call returns no name and no count |
| stdout carries protocol frames alone | Five lines, all JSON-RPC 2.0, zero parse failures |
| stderr carries adapter diagnostics alone | Six lines, and every one starts with `chat-mcp: ` |
| One diagnostic line per call | Three call lines in stderr, one per call |
| The process exits within the bound | `withinBound=true`, 343 ms, code 0 |
| The credential is judged, and not ignored | The control run answered 401 on all three calls |

## The 2026-10-01 run, with the dedicated agent account

The 2026-09-30 run named `Admin`. That account carries every right of the
deployment administrator, so the run proved the credential path and it proved
no narrowing. This run names `Agent`, a plain user that holds no administrator
reach. Issue `CHAT-werokcbb` carries the work.

Every criterion of the 2026-09-30 run was answered again. The appended readings
are the agent account and the refusal matrix.

### The deployment

| Item | Value |
|---|---|
| Module | `chat-deploy-memory`, from its `-exec` jar |
| Built | 2026-10-01, with the `expose-webflux` and `deploy` Maven profiles |
| Jar size | 290,609,693 bytes |
| Application port | 6892, listening on `http://127.0.0.1:6892` |
| Management port | 6893 |
| Runtime | OpenJDK 25, GraalVM CE |
| Key type | `long` |
| Node id | 1 |
| Spring profile | none. The default profile is active. |
| Selectors | `key`, `persistence`, `pubsub` and `secrets` at `memory`. `index` at `lucene`. |
| Agent account | `Agent`, resolved at startup through `ChatUserService` |
| Enforced scope | `chat.mcp` |
| Client id | `31649af5-0154-4be5-8695-fda9d18b7981` |

**The agent account carries a generated credential.** The list below reads the
startup output. One line holds a password, and no line names a resolution
failure.

```
Generated password for account 'Agent': <generated value>
```

**The count of `The agent username` in that log is 0.** The account resolved
exactly once, which is the rule `AgentIdentityLifecycle` enforces.

The flag list is in the appendix of `docs/MCP-CREDENTIAL-ISSUANCE.md`. It gained
`--spring.application.name` on this day, because a start without it fails with
`Could not resolve placeholder 'spring.application.name'`.

### The credential

The authorization server ran with the `memory` profile on port 9000. The token
came from one `client_credentials` request with `scope=chat.mcp`, and `jq` read
the field.

Measured token claims: `scope` is `["chat.mcp"]`, `client_id`, `sub` and `aud`
all equal the client id above, and the lifetime is 300 seconds. The header
carries `alg` `ES256`.

### The topic

The topic was created through the deployment on 2026-10-01, with the issued
token on the request. The agent holds `{User, MessageTopic, NEW}`, so the
composite check permits it.

```sh
curl -H "Authorization: Bearer $TOKEN" -X PUT \
  http://127.0.0.1:6892/persist/topic/add \
  -H 'Content-Type: application/json' -d '{"type":"ByNameRequest","name":"mcpagent"}'
```

```
HTTP/1.1 201 Created
{"key":{"id":1555400854075346944,"root":1555400828490092545,"empty":false}}
```

| Field | Value |
|---|---|
| id | `1555400854075346944` |
| root | `1555400828490092545` |
| name | `mcpagent` |

### The transcript

Measured on 2026-10-01. Node v26.7.0. Client `@modelcontextprotocol/sdk`
1.31.0.

```
node v26.7.0 | sdk 1.31.0 | declared 2025-11-25 | negotiated 2025-11-25
server {"name":"demo-chat-mcp","version":"0.0.1"} | caps {"tools":{"listChanged":true}}
tools: chat_list_topics, chat_get_topic
call chat_list_topics {} isError=false _meta=null
  => {"topics":[{"id":"1555400854075346944","root":"1555400828490092545","name":"mcpagent"}]}
call chat_get_topic {"topicId":"1555400854075346944"} isError=false _meta=null
  => {"topic":{"id":"1555400854075346944","root":"1555400828490092545","name":"mcpagent"}}
call chat_get_topic {"topicId":"1"} isError=true
  _meta {"code":"NOT_AVAILABLE","message":"the backend does not serve this object, or it refuses this caller","retryable":false}
exit {"withinBound":true,"millis":0,"code":0,"signal":null} | connectError null
stderr ["chat-mcp: configured for http://127.0.0.1:6892, 2 topic ids, key type LONG",
        "chat-mcp: ready, protocol revision is chosen by the SDK",
        "chat-mcp: chat_list_topics answered call=1 duration=108ms code=OK status=200",
        "chat-mcp: chat_get_topic answered call=2 duration=13ms code=OK status=200",
        "chat-mcp: chat_get_topic refused: the backend does not serve this object, or it refuses this caller call=3 duration=8ms code=NOT_AVAILABLE status=404",
        "chat-mcp: stdin closed, exiting"]
```

**No call answered `AUTHENTICATION_REQUIRED`.** The code is absent from the
transcript, from the stderr lines and from the exit record.

Every row of the 2026-09-30 criteria table reads the same way. Three rows are
restated here, because the agent account could have moved them.

| Task 8 criterion | Reading |
|---|---|
| Discovery lists both tools | `tools: chat_list_topics, chat_get_topic` |
| The served topic carries the real id, root and name | All three match the deployment's answer |
| The unserved id answers a refusal | `isError=true`, code `NOT_AVAILABLE` |
| stderr carries adapter diagnostics alone | Six lines, and every one starts with `chat-mcp: ` |
| The process exits within the bound | `withinBound=true`, code 0 |
| The credential is judged, and not ignored | The control run answered 401 on all three calls |

### The control run, and the refusal matrix

The control replaced the token with junk text and ran the same harness. All
three calls then answered `AUTHENTICATION_REQUIRED` with `status=401`.

Each row below is one measured request to `GET /topic/id/1555400854075346944`.

| Credential | Status | Meaning |
|---|---|---|
| None | 401 | No token reached the chain |
| Junk text | 401 | The decoder refused it |
| Right `client_id`, no scope | 403 | The scope check refused it |
| Right `client_id`, `openid` only | 403 | The scope check refused it |
| Right `client_id`, `chat.mcp` | 200 | Accepted |

**The agent account changed none of these rows.** The credential path does not
read the account, and the agent holds `MessageTopic.GET` through the `User`
root, so the last row allows.

### What this run does not prove

- **No grant was denied to the agent.** Every accepted call was permitted by a
  row the agent holds through the `User` root. The three denies of the agent
  matrix are measured in `AnonymousAuthorizationMatrixTests`, not here.
- **The read did not need an owner row.** `getRoom` allows for the agent, and it
  allows for `Anon` too. The adapter reads, and the read grant is the floor.

## Reproduce it

1. Obtain a credential by the procedure in `docs/MCP-CREDENTIAL-ISSUANCE.md`.
2. Start a deployment with the four `app.security` values that the procedure
   names. Create a topic through it. Record its id, root and name.
3. Write the configuration above with that real id, and one id the deployment
   does not hold.
4. Run the harness with the adapter behind the deployment.
5. Compare each answer against the deployment's own answer to the same route.
6. Replace the credential file with junk text. Run the harness again. Every
   call must answer `AUTHENTICATION_REQUIRED` with `status=401`.

## The 2026-10-09 automated messaging run

Issue: `CHAT-teujorxl`. Task 6 source: `1b5e73ff`.
Follow-up `e2c77a7d` adds a changed-room conflict assertion to the same test.
The earlier dated measurements remain historical evidence.
This run adds authenticated messaging coverage without replacing those records.

`McpMessagingDeploymentTests` starts an authenticated memory REST deployment on a random port.
It uses real handlers, memory persistence and Pubsub, Lucene indexing, and `app.command.bus=memory`.
The server selects the sender from the agent token.
The fixture signs JWTs with a temporary trusted key.
It does not request tokens from an authorization server.
It uses loopback HTTP and proves no TLS behavior.

The pinned Node client starts the real adapter as a child process.
Each call checks protocol-only stdout and adapter shutdown within five seconds.
The deployment classpath includes Logback, so the child uses a temporary configuration with logging disabled.
The normal adapter runtime still has no logging provider.

| Test | Evidence |
|---|---|
| Submission and reads | An MCP send reaches persistence and index. Both read tools return the same message and token-selected sender. |
| Existing room | A second agent creates the room. The caller joins, then history and submission probes succeed. |
| Pending completion | A test-only store gate produces `PENDING`. Release permits P and I to succeed, followed by both message reads. |
| Repeat and conflict | Two adapter processes reuse one request identity and receive one receipt. Changed text or room conflicts. History contains one message. |
| Room refusal | The denied room is configured in the adapter. The server refuses history and submission. |
| Message scope | Raw message-by-ID GET succeeds. The adapter blocks that message outside its configured rooms without content or identifier disclosure. |
| Command owner | Another agent receives the same unavailable result for an existing command and a missing command. |

The test class has six tests because one test covers both room refusal and message scope.
It reports zero failures, errors, and skips.
Omitting the persistence wrapper fails the pending test, which receives `COMPLETED` instead.
The held attempt may become `UNCERTAIN` after the 30-second watchdog deadline.
The test permits that state before release and requires success afterward.
No production delay setting or control route exists.

The updated agent HTTP gate passed two identity tests, six MCP tests, and seven relay tests.
All three classes report zero failures, errors, and skips.
The REST run emitted no Surefire shutdown warning.
The earlier full RSocket-profile warning remains unexplained.
The full REST-profile failure is a separate accepted baseline under `CHAT-gsddauhn`.
The final `build-health.sh --ci` run used an empty temporary `DOCKER_CONFIG`.
It ran 2,450 reactor tests, with zero failures, zero errors, and 82 skips, and it reported no drift.
Its agent HTTP gate passed the same 15 tests without skips.
`docs/BUILD-HEALTH.md` records both runs and the cause of the skip count.
The [profile report](superpowers/reviews/2026-10-09-mcp-messaging-profile-check.md) records the comparison without `chat-mcp`.

Run the deployment class explicitly:

```sh
mvn -B -pl chat-deploy-memory -am -Pexpose-webflux clean verify \
  -Dtest=McpMessagingDeploymentTests -Dsurefire.failIfNoSpecifiedTests=false
```

Run the authentication, messaging, and relay gate:

```sh
shell-scripts/agent-http-gate.sh
```

For manual acceptance, obtain a token through `docs/MCP-CREDENTIAL-ISSUANCE.md`.
Create a room through `POST /topic/new`, or join one through `PUT /topic/join/{id}`.
Confirm one history read and one submission succeed before MCP calls.
Configure that room and enable sending.
Use caller-owned request IDs and the server completion timeout of `5s`.
Use the `--calls-file` harness mode documented in `docs/MCP-ADAPTER.md`.
A server restart removes Stage 1 retry safety for earlier requests.
