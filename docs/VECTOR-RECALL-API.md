# Vector recall API

How to send messages, index them, and search them.

Every claim here names the test that proves it. A claim with no test beside it
is marked as unproven.

## Read this first

**No deployment sets the vector selectors.** The feature is off in every
composition this repository ships. You must set the properties below yourself.

**The embedding model is a mock.** `DummyEmbeddingModel` builds character bigram
vectors at 256 dimensions. So a search matches on shared character pairs, not on
meaning. A search for `car` finds `cart` and it does not find `automobile`. Do
not read the examples below as semantic search.

**The vector store is a derived cache.** The persisted messages are the source
of truth. A lost store is rebuilt from them, and no deployment mounts a volume
for it.

**The REST application routes carry no authentication today.** The application
filter chain permits every route it owns, and it wires no HTTP Basic and no
authentication manager. So no `Authentication` reaches a chat route, and the
commands below send no credentials. The actuator routes are the exception. They
sit behind their own chain and they need the actuator user. See
`CHAT-jdsamcia`.

## Turn it on

Five properties start the recall beans and the seed route. All five are
required.

```properties
app.service.composite=true
app.service.core.vector=simple
app.service.core.embedding=mock
app.controller.recall=true
app.controller.persistence=true
```

`app.service.core.vector` takes `mock`, `simple`, `redis`, or `embedded`.
`app.service.core.embedding` takes `mock`, `openai`, or `local`. A mock vector
store takes the mock model alone. A production model also needs
`app.service.core.embedding.identity`. An illegal pair fails the startup.

`app.controller.persistence` opens the seed route of the first scenario.

Two more properties reach the operator endpoint. The deployments disable every
actuator endpoint by default, so exposure alone answers 404.

```properties
management.endpoint.vectorindex.enabled=true
management.endpoints.web.exposure.include=vectorindex
```

Two optional properties control the rebuild.

```properties
app.vector.index.trust=none
app.vector.index.startup=report
```

`trust` decides whether a job from an earlier process start counts as coverage.
`none` is the default and accepts only this process. `startup` decides what
happens at `ApplicationReadyEvent`. `report` is the default and starts no job.

## Scenario: synthetic messages, then a search

The messages below share the word `recipe`. The bigram model matches on that
shared text.

### Step 1. Write three messages

`POST /message/send/{id}` binds `@AuthenticationPrincipal`, and that parameter
is always null on this chain. So this scenario writes through the persistence
route, which binds a request body alone.

```bash
for text in "apple pie recipe" "banana bread recipe" "carrot soup recipe"; do
  curl -sS \
    -X PUT http://localhost:8080/persist/message/add \
    -H 'Content-Type: application/json' \
    -d "{\"type\":\"MessageSendRequest\",\"msg\":\"$text\",\"from\":10,\"dest\":20}"
done
```

`10` is the sender id and `20` is the topic id. Each answer carries the new
message key, and the status is 201.

### Step 2. Rebuild the index

**A persistence write creates no vector.** The send route indexed each message
as it arrived. This route does not. So a search now returns no hit until a
rebuild reads the persisted messages.

```bash
curl -sS -u actuator:actuator -X POST http://localhost:8080/actuator/vectorindex
```

### Step 3. Wait for the rebuild to finish

```bash
until curl -sS -u actuator:actuator http://localhost:8080/actuator/vectorindex \
  | jq -e '.status.running == false' > /dev/null; do sleep 1; done
```

The rebuild runs on another scheduler, so an immediate search can read an
incomplete index. The `indexComplete` flag names that state.

### Step 4. Search the topic

```bash
curl -sS \
  -X POST http://localhost:8080/message/recall/topic \
  -H 'Content-Type: application/json' \
  -d '{"type":"TopicRecallRequest","topicId":20,"query":"recipe","limit":10}'
```

The answer is one JSON object.

```json
{
  "indexComplete": true,
  "hits": [
    { "key": { "key": { "id": 1, "from": 10, "dest": 20 } }, "score": 0.87 },
    { "key": { "key": { "id": 2, "from": 10, "dest": 20 } }, "score": 0.81 }
  ]
}
```

**The message key sits one level deeper than its field name.** `Key` carries a
wrapper object named `key`, at `KeyValuePair.kt`. So the path to a message id is
`hits[0].key.key.id`. `MessageRecallRestTests` pins that path.

A hit carries the key and the score and nothing else. Reload the message body
through `GET /message/id/{id}`.

### Step 5. The other two searches

```bash
# One sender, across every topic.
curl -sS -X POST http://localhost:8080/message/recall/user \
  -H 'Content-Type: application/json' \
  -d '{"type":"UserRecallRequest","userId":10,"query":"recipe"}'

# Every message this node indexed.
curl -sS -X POST http://localhost:8080/message/recall/global \
  -H 'Content-Type: application/json' \
  -d '{"type":"GlobalRecallRequest","query":"recipe","limit":5,"threshold":0.4}'
```

## Scenario: a lost index, then a rebuild

This is the case the feature exists for. The store is ephemeral, so a restart
can leave persisted messages with no index.

### Step 1. Search, and read the flag

```json
{ "indexComplete": false, "hits": [] }
```

**An empty result has two meanings, and only the flag separates them.**
`indexComplete=false` says no job covers this index, so the empty list proves
nothing. `indexComplete=true` says the search was complete and found nothing.

### Step 2. Trigger a rebuild

```bash
curl -sS -u actuator:actuator -X POST http://localhost:8080/actuator/vectorindex
```

The answer is the claim snapshot.

```json
{
  "phase": "REBUILDING",
  "running": true,
  "complete": false,
  "activeJob": null,
  "coveringJob": null
}
```

**`activeJob` is null here, and that is not a defect.** The run creates its job
after it takes the claim, and on another scheduler. **This call never promises
the job key.**

### Step 3. Poll the read operation

Read until `activeJob` is not null, or until `running` is false. An immediate
second read does not close that race.

```bash
until curl -sS -u actuator:actuator http://localhost:8080/actuator/vectorindex \
  | jq -e '.status.running == false' > /dev/null; do sleep 1; done
```

### Step 4. Read the result

```bash
curl -sS -u actuator:actuator http://localhost:8080/actuator/vectorindex | jq .
```

```json
{
  "status": {
    "phase": "COMPLETE",
    "running": false,
    "complete": true,
    "activeJob": null,
    "coveringJob": { "key": { "id": 500 } },
    "lastSuccessCount": 3,
    "lastReport": { "attempted": 3, "indexed": 3, "skipped": 0, "failed": 0 }
  },
  "jobs": [
    {
      "key": { "key": { "id": 500 } },
      "nodeId": 1,
      "keyType": "long",
      "outcome": "SUCCEEDED",
      "attempted": 3,
      "indexed": 3,
      "failed": 0,
      "invalidationCount": 0
    }
  ]
}
```

The read returns at most 50 jobs of this node and key type. It sorts by start
instant, then by root key, both descending.

### Step 5. Search again

The same search from the first scenario now answers with the three messages and
`indexComplete=true`. `VectorIndexRecoveryTests` proves this whole sequence.

## Limits and errors

| Rule | Value | Result when broken |
|------|-------|--------------------|
| `query` | Must not be blank | `InvalidRecallRequestException` |
| `limit` | 1 to 50 | `InvalidRecallRequestException` |
| `threshold` | 0.0 to 1.0 | `InvalidRecallRequestException` |

`threshold` 0.0 accepts every document, so a search with it returns the whole
corpus up to `limit`.

**The routes answer with JSON and refuse anything else.** A request with
`Accept: application/x-ndjson` gets 406. The design says these routes no longer
produce NDJSON, and a named media type is what holds that rule.
`MessageRecallRestTests` covers all three routes.

## RSocket

The same three searches are request-response routes. Each answers with one
`MessageRecallResult`.

| Route | Request |
|-------|---------|
| `message-recall-topic` | `TopicRecallRequest` |
| `message-recall-user` | `UserRecallRequest` |
| `message-recall-global` | `GlobalRecallRequest` |

`MessageRecallControllerTests` covers all three.

## What the flag means during a rebuild

**A repair keeps the coverage of the older successful job.** So a rebuild of a
healthy index reports `indexComplete=true` while it runs. A first rebuild
reports `false` until it succeeds.

The embedded provider removes a document before it writes that document again.
Recall can miss one message between the two calls, and the index still reports
complete. **This is an accepted false positive.** The window runs from the end
of the removal to the end of the write, and it covers the embedding call and the
provider commit. The design gives that window no duration bound.

## Where each claim is proven

| Claim | Test |
|-------|------|
| The three REST routes answer with one JSON object | `MessageRecallRestTests` |
| A request for NDJSON gets 406 | `MessageRecallRestTests` |
| The key sits at `hits[0].key.key.id` | `MessageRecallRestTests` |
| The three RSocket routes answer with one result | `MessageRecallControllerTests` |
| The actuator route answers with `status` and `jobs` | `MemoryVectorIndexActuatorTests` |
| The endpoint bean exists in a real deployment | `MemoryVectorRecallBootTests` |
| The read sorts, bounds, and filters the jobs | `VectorIndexEndpointTests` |
| Seed, rebuild, and search recovers a lost index | `VectorIndexRecoveryTests` |
| A second rebuild succeeds in one process | `VectorIndexRecoveryTests` |
| A job message never enters the recall corpus | `VectorIndexRecoveryTests` |
| The request limits and the error type | `MessageRecallServiceImplTests` |

**Unproven here.** The `curl` commands are written from the route definitions
and the test assertions. No test drives this document end to end through a
running server.

**The JSON examples are abbreviated.** Each one omits fields that the real
answer carries. A status also holds `lastSuccessAt` and `lastFailure`. A job
also holds `incarnationId`, `startedBy`, `startedAt`, `finishedAt`, and
`lastInvalidationAt`. A report also holds `startedAt` and `finishedAt`. A
message key also holds `timestamp` and `empty`.

A job never carries `covers`, because that value is derived and the type hides
it from Jackson. A status does carry `complete` and `running`, because those two
are derived and not hidden.

The scores are illustrative. The bigram model decides them, and the values
depend on the text.

## Related

- `docs/VECTOR-BUILD-CONTROLS.md` for what the build decides and what launch
  decides.
- `docs/superpowers/specs/2026-09-10-vector-reindex-design.md`
- `docs/superpowers/specs/2026-09-11-vector-index-run-record-design.md`
