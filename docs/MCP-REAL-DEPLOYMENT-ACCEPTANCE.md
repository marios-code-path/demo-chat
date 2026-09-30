# Real-deployment acceptance for the MCP adapter

This document records one acceptance run of the Demo Chat MCP adapter against a
running Demo Chat deployment. Issue `CHAT-spbwlamz` carries the work. Task 8 of
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

The run now requires production REST authentication. `CHAT-pgpmsgvr` makes the
WebFlux application chain validate an agent token on every route it owns.
`RSocketServerConfiguration` remains outside this issue and carries
`TODO: lock down!`.

**The token in `credential.txt` must be a valid agent token.** This issue proves
that the adapter sends the bearer header and that REST routes validate it.
`CHAT-rvcrzxvw` still covers credential issuance for a deployed agent account.

The run also does not prove deployed authorization. The deployment holds no
denial to observe, because no route enforces one. So every call in this run was
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
credentialFile=credential.txt   (a valid agent token)
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

1. **Authentication.** `CHAT-pgpmsgvr` enforces the credential boundary for
   every route the adapter uses. This run uses a token from the trusted JWK.
2. **The denial path.** The deployment holds no denial to observe. So this run
   never exercised a 403 from a real grant. Task 4's stdio test covers a 403
   against the fake backend. That test is the only coverage of the denial path.
3. **The credential origin.** This run mints a token locally from the trusted
   JWK. `CHAT-rvcrzxvw` covers production credential issuance.

## Reproduce it

1. Start a memory deployment with the prerequisite recipe in the plan.
2. Create a topic through the deployment. Record its id, root and name.
3. Write the configuration above. Use the real id, and one id the deployment
   does not hold.
4. Run the harness with the adapter behind the deployment.
5. Compare each answer against the deployment's own answer to the same route.
