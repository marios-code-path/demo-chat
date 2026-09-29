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

The run does not prove production REST authentication. No deployment enforces a
credential today. `WebFluxSecurity` permits every exchange and adds no
authentication. `RSocketServerConfiguration` carries `TODO: lock down!`. Both
are recorded in `forward-register.md`. `CHAT-pgpmsgvr` closes that boundary.

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
curl -X PUT http://127.0.0.1:6791/persist/topic/add \
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
own refusal is `the backend answered 404`.

## The configuration

```properties
backendBaseUrl=http://127.0.0.1:6791
credentialFile=credential.txt
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
call chat_list_topics {} isError=false
  => {"topics":[{"id":"1554429686883287040","root":"1554361143634427905","name":"mcprealacceptance"}]}
call chat_get_topic {"topicId":"1554429686883287040"} isError=false
  => {"topic":{"id":"1554429686883287040","root":"1554361143634427905","name":"mcprealacceptance"}}
call chat_get_topic {"topicId":"1"} isError=true => "the backend answered 404"
stdout lines 5 | parse failures 0
stderr ["chat-mcp: configured for http://127.0.0.1:6791, 2 topic ids, key type LONG",
        "chat-mcp: ready, protocol revision is chosen by the SDK",
        "chat-mcp: chat_list_topics answered",
        "chat-mcp: chat_get_topic answered",
        "chat-mcp: chat_get_topic refused: the backend answered 404",
        "chat-mcp: stdin closed, exiting"]
exit {"withinBound":true,"millis":331,"code":0,"signal":null} | connectError null
```

## The criteria, one by one

| Task 8 criterion | Reading |
|---|---|
| Discovery lists both tools | `tools: chat_list_topics, chat_get_topic` |
| The served topic carries the real id, root and name | All three match the deployment's answer |
| The unserved id answers a refusal | `isError=true`, `the backend answered 404` |
| The refusal carries no backend text | The string `is not in the registry` appears in neither stream |
| The refusal carries no name | The refused call returns no name and no count |
| stdout carries protocol frames alone | Five lines, all JSON-RPC 2.0, zero parse failures |
| stderr carries adapter diagnostics alone | Six lines, and every one starts with `chat-mcp: ` |
| One diagnostic line per call | Three call lines in stderr, one per call |
| The process exits within the bound | `withinBound=true`, 331 ms, code 0 |

## Two further readings

1. **The negotiated revision is the served revision.** The client declared
   `2025-11-25` and the adapter answered the same value. Decision D1 holds
   against a real deployment, not only against the fake backend.
2. **A refused id is left out of the list answer with no trace.**
   `chat_list_topics` answered one topic although `topicIds` held two. The
   unserved id appears with no name and no count. That is the documented
   behaviour for a topic the deployment does not serve.

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

This run does not close `CHAT-ylvoiixm`. Two things stay open.

1. **Authentication.** `CHAT-pgpmsgvr` enforces the credential boundary for
   every route the adapter uses. Until it passes, no end-to-end authorization
   claim is valid for this adapter.
2. **The denial path.** The deployment holds no denial to observe. So this run
   never exercised a 403 from a real grant. Task 4's stdio test covers a 403
   against the fake backend. That test is the only coverage of the denial path.

## Reproduce it

1. Start a memory deployment with the prerequisite recipe in the plan.
2. Create a topic through the deployment. Record its id, root and name.
3. Write the configuration above. Use the real id, and one id the deployment
   does not hold.
4. Run the harness with the adapter behind the deployment.
5. Compare each answer against the deployment's own answer to the same route.
