# MCP adapter credential issuance

This document tells an operator how the adapter credential file gets its value.
Issue `CHAT-rvcrzxvw` carries the work.

The adapter reads that file. The adapter never writes it, and it never accepts a
token as an argument. So the file needs an outside procedure.

The procedure asks the authorization server for a token. The token carries the
`chat.mcp` scope, and the deployment requires that scope.

`docs/MCP-ADAPTER.md` states the adapter contract.
`docs/MCP-REAL-DEPLOYMENT-ACCEPTANCE.md` records a run that uses this procedure.
`docs/BUILD.md` states how to start a REST launch and how to make a signing key.

## What the credential must be

| Property | Value |
|---|---|
| Form | One JSON Web Token, as bare text. No `Bearer` prefix and no quotes. |
| Scheme | `ES256`, signed by the key the deployment trusts |
| Claim `scope` | `chat.mcp` |
| Claim `client_id` | The value of `app.security.agent.client-id` in the deployment |
| Lifetime | 300 seconds from issue |

The file holds the token alone. The reader trims the surrounding whitespace, so
a trailing newline is accepted.

**The `client_id` claim is the link between the two sides.** One client id
appears three times. It names the OAuth client in the token request. It names
`app.security.agent.client-id` at the deployment. A token from another client is
refused with 401, even when its scope is right.

## The procedure

### Step 1: start the authorization server

The authorization server issues the token. It must hold the private half of the
key that the deployment trusts.

```sh
./shell-scripts/chat-build authserv --run --notls --node-id 8 \
  --jwk "$PWD/encrypt-keys/server_keycert.jwk" --profile memory
```

`--profile memory` selects the in-memory client repository. That repository
reads `app.oauth2.client` from `oauth2-client.yml`. The client there carries the
`chat.mcp` scope.

**The `jdbc` Maven profile does not select the JDBC client repository.** That
profile is a build profile, and it is always active for this service. The Spring
profile alone selects the repository. Read the next limit before you use the
`client-init` path.

Wait until the log prints `Started ChatApp`. The token endpoint answers 404
before that line.

### Step 2: request the token

```sh
curl -sS -u '<client-id>:<client-secret>' \
  -d 'grant_type=client_credentials' -d 'scope=chat.mcp' \
  http://127.0.0.1:9000/oauth2/token
```

The response carries four fields. Measured on 2026-09-30:

```json
{"scope":"chat.mcp","token_type":"Bearer","expires_in":299,"access_token":"<redacted>"}
```

The token header and claims, measured on the same run:

```
header  {"alg":"ES256","kid":"f438dc6b-45e8-40e8-9342-038754562052"}
claims  aud, client_id, exp, iat, iss, jti, nbf, scope, sub
scope   ["chat.mcp"]
iss     http://127.0.0.1:9000
aud     the client id
client_id  the client id
```

The audience equals the client id. Audience validation is not enforced today.
`CHAT-okpgpxkj` holds that gap.

The token request carries the client secret over plain HTTP on a loopback
address, which is a development form. Use TLS for any other host.

### Step 3: write the credential file

```sh
curl -sS -u '<client-id>:<client-secret>' \
  -d 'grant_type=client_credentials' -d 'scope=chat.mcp' \
  http://127.0.0.1:9000/oauth2/token \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["access_token"])' \
  > /secure/path/credential.txt
chmod 600 /secure/path/credential.txt
```

Point `credentialFile` in the adapter configuration at that path. A relative
value resolves against the directory of the configuration file.

Keep the file outside the repository. A token under a tracked directory is one
`git add` away from a commit, which is the rule the signing key already follows.

### Step 4: launch the deployment

The deployment needs four values under `app.security`. None of them has a
default, so the context refuses to start without them.

| Property | Value |
|---|---|
| `app.security.agent.client-id` | The client id from step 2 |
| `app.security.agent.username` | The chat user that names the agent identity |
| `app.security.agent.required-scope` | `chat.mcp` |
| `app.security.jwt.jwk-path` | The JWK file that holds the trusted public key |

`docs/BUILD.md` states the launch in full.

#### The supported route: chat-build rest

```sh
./shell-scripts/chat-build rest --run --notls --node-id 1 \
  --jwk "$PWD/encrypt-keys/server_keycert.jwk" \
  --agent-client-id 31649af5-0154-4be5-8695-fda9d18b7981 \
  --agent-username Agent
```

Both `--notls` and `--node-id` are required. `chat-build` refuses a start
without either one. Measured on 2026-09-30: the command without them exits 2
with `the following arguments are required: --node-id`.

`Agent` is the handle that `userinit.yml` declares for the MCP adapter account.
The deployment resolves it once at startup, and a wrong handle fails the start.

Give `--jwk` an absolute path. `spring-boot:run` sets the module directory as
the working directory, so a relative path would resolve against that rather
than against you.

**This command starts a different shape from the measured run.** Its dry run on
2026-09-30 reads as follows.

| Property | `chat-build rest` | The measured run |
|---|---|---|
| Module | `chat-deploy` | `chat-deploy-memory` |
| Backend | `client`, over RSocket | `memory`, in process |
| Application port | 6792 | 6892 |
| Root keys | Read over HTTP from a core on 6791 | Loaded in process |

So this route needs a core service to run first. It is a REST facade, and it
holds no store of its own.

#### The measured route: the memory jar

Use this route to reproduce the acceptance run. The appendix carries the full
flag list, and its ports are 6892 and 6893.

`chat-build` gives no command for this shape. It accepts the agent flags on the
`rest` service alone, and that service always takes the `client` backend.

The deployment resolves the agent identity once at startup. It looks the
username up through `ChatUserService` and requires exactly one match. Any other
count fails the start with `The agent username '<name>' answered <n> users.`

### Step 5: run the adapter

```sh
node harness.mjs --read-id <allowed id> --refused-id <other id> -- \
  java -cp <classpath> com.demo.chat.mcp.McpAdapterMainKt --config adapter.properties
```

A working credential answers 200 for an allowed topic. A broken one answers a
tool error with `code=AUTHENTICATION_REQUIRED` and `status=401`.

## Measured evidence

Measured on 2026-09-30, from a memory deployment on `127.0.0.1:6892`. Node
v26.7.0. Client `@modelcontextprotocol/sdk` 1.31.0.

The deployment ran `chat-deploy-memory` as a single process that mounts the
application chain. Its flag list is in the appendix below. It read
`app.security.agent.username=Admin`.

**The 2026-10-01 run repeated every reading with
`app.security.agent.username=Agent`.** That run is the one this document's
procedure describes. The 2026-09-30 readings above stand, and the account they
named is no longer the account the procedure uses. See "The agent account"
below.

The token came from the authorization server by the step 2 request. The harness
then ran with that token in `credential.txt`.

| Reading | Result |
|---|---|
| The token request | HTTP 200, `scope":"chat.mcp"`, `expires_in":299` |
| `GET /topic/id/1` with no credential | 401 |
| `GET /topic/id/1` with the token | 404, `Key 1 is not in the registry.` |
| Topic creation `PUT /persist/topic/add` with the token | 201 |
| `GET /topic/id/<new id>` with the token | 200, the real name, id and root |
| Harness exit | 0, within bound, `connectError` null |
| Harness calls | Two answered, one refused `NOT_AVAILABLE` |
| `AUTHENTICATION_REQUIRED` in the run | Absent from every call |

**The harness transcript is in `docs/MCP-REAL-DEPLOYMENT-ACCEPTANCE.md`.** That
document records the 2026-09-30 run in full. This section records the credential
readings alone.

The topic was created through the deployment. Measured on 2026-09-30:

```sh
curl -H "Authorization: Bearer $TOKEN" -X PUT \
  http://127.0.0.1:6892/persist/topic/add \
  -H 'Content-Type: application/json' -d '{"type":"ByNameRequest","name":"mcpissuance"}'
```

```
HTTP/1.1 201 Created
{"key":{"id":1555004356812607488,"root":1555003879521783809,"empty":false}}
```

The name is one token. A hyphen splits the Lucene field, so this run avoids one.
`CHAT-hajmhslp` holds that defect.

**The acceptance run used a second topic, `mcpcredential`.** The command above
created `mcpissuance` to re-prove the create route on the day of this writing.
So the two documents name two topics, and both are real.

### The control run

A run that only succeeds proves nothing. Any text in the file would pass such a
run. So the same harness ran again with a junk credential in the same file.

| Call | Junk credential | Valid token |
|---|---|---|
| `chat_list_topics` | `AUTHENTICATION_REQUIRED`, `status=401` | answered, `status=200` |
| `chat_get_topic`, allowed id | `AUTHENTICATION_REQUIRED`, `status=401` | answered, `status=200` |
| `chat_get_topic`, unserved id | `AUTHENTICATION_REQUIRED`, `status=401` | `NOT_AVAILABLE`, `status=404` |

Every call in the control run failed with 401. So the successful run did read
the credential, and the deployment did judge it.

### The refusal matrix

Each row is one measured request to `GET /topic/id/<id>`.

| Credential | Status | Meaning |
|---|---|---|
| None | 401 | No token reached the chain |
| Junk text | 401 | The decoder refused it |
| Right scope, other `client_id` | 401 | The `client_id` claim check refused it |
| Right `client_id`, no scope | 403 | The scope check refused it |
| Right `client_id`, `openid` only | 403 | The scope check refused it |
| Right `client_id`, `chat.mcp` | 200 | Accepted |

401 is a refusal of the credential. 403 is a refusal of the scope. The adapter
maps both to `AUTHENTICATION_REQUIRED`.

## Expiry

A token lives 300 seconds. Nothing in this repository changes that value, so the
library default stands.

**An expired token needs a new file and no restart.** The adapter reads the
credential file at each request, and it holds no copy. So the operator writes a
new token to the same path and the next call uses it.

The adapter checks the file once at startup. That check proves the file exists
and holds text. It reads no token. So a file that is present and empty fails the
start with `the credential file holds no token`.

An operator who needs a long-lived adapter has three options.

1. Refresh the file on a timer under 300 seconds.
2. Raise the token lifetime at the authorization server.
3. Hold the signing key and mint the token locally, as
   `shell-scripts/agent-token.py` does.

Option 3 removes the authorization server from the path. Anyone who holds that
key can mint a token for any client and any scope. A test gate may do that. A
deployed agent should not.

## The agent account

The procedure names `Agent`. `userinit.yml` declares that account, and startup
creates it. `CHAT-werokcbb` closed on 2026-10-01, and this section is its
result.

**The agent is a plain user.** It takes no `ChatIdentity`. `ChatIdentity` stays
a closed set of `ADMIN` and `ANON`, and that is deliberate. So the agent holds
no administrator reach.

| Property | Value |
|---|---|
| Handle | `Agent` |
| Name | `MCP ADAPTER` |
| Image | `chatimg://agent.png` |
| Password | None. The start generates one. |

**The start generates the credential and prints it.** `InitialUsersService`
writes `Generated password for account 'Agent': <value>` to the console. The
value is written at every start, and the newer value replaces the stored one.
**Only the newest output holds the live password.** Measured on 2026-10-01:
the deployment log carried one such line and no other.

**No grant row names the agent.** `InitialUsersService` writes one `*` row per
domain root for the `Admin` key. The agent key is not that key, and it is in no
other caller's actor set. So those rows do not reach the agent.

### What the agent may do

The agent holds the floor that every identity holds. The floor is the `Anon`
key, the `User` root and the caller, plus the shipped rows those names reach.

| Operation | Agent | Admin |
|---|---|---|
| `getRoom`, `GET` | allow | allow |
| `listRooms`, `GET_ALL` | allow | allow |
| `addRoom`, `NEW` | allow | allow |
| `whoami`, `User FIND` | allow | allow |
| `messageById`, `Message GET` | allow | allow |
| `addUser`, `User NEW` | **deny** | allow |
| `deleteRoom`, `REM` | **deny** | allow |
| `send` to a room the caller does not own | **deny** | allow |

**No grant change can narrow one caller below the floor.** The actor set
carries the `Anon` key and the `User` root for every query. So the agent also
holds `addRoom`, `listRooms` and `messageById`. Each is wider than the adapter
needs. Narrowing them is a separate decision about the shipped grants.

`AnonymousAuthorizationMatrixTests` pins the two matrices, and the difference
between them is three operations.

## Limits

Limit 2 and limit 3 are open. The owner accepted them on 2026-09-30.

### Limit 2: the read grant covers no single object

The composite authorization interfaces are mounted since 2026-10-01. So a
write is judged now, and it denies for a caller that owns no room. The `core`
interfaces stay unmounted, and `CHAT-ruapxetl` holds that gap.

The read side is narrower than a full grant. `Anon` holds `Message:GET`, and a
grant on a domain root does not cover one object. The adapter reads single
topics, so its reads rest on the routes that the deployment permits today.

Read `docs/ANONYMOUS-AUTHORIZATION.md` before you widen an agent account.

### Limit 3: the `client-init` path registers no `chat.mcp` client

The `client-init` Spring profile builds its clients from
`spring.security.oauth2.authorizationserver.client.*`. The client in
`chat-authorization-server/src/main/resources/application.yml` is `chatClient`,
and its scopes are `auth, message, topic, user, openId`.

**A token request against `chatClient` with `scope=chat.mcp` fails.** Use the
`memory` profile, or add `chat.mcp` to that client.

This one is not filed. It is owner judgment, because the fix is a scope list in
one file. A reader who hits it should file it.

## What this does not prove

- **No deployed grant was exercised.** The deployment holds no denial to
  observe, so every accepted call was permitted by the credential alone. Limit 2
  records the state.
- **No token rotation was measured.** The expiry rule above rests on the source
  read of `TopicClient.readTopic`, which reads the file at each request.
- **No TLS was used.** Every measured request ran on a loopback address over
  plain HTTP.
- **The 300 second lifetime is the library default.** It is measured, and it is
  not configured in this repository.

## Appendix: the measured deployment flags

The deployment ran the executable jar of `chat-deploy-memory`, built with
`-Pexpose-webflux,deploy`. It carried the golden `core-memory-init` flag set,
with five changes.

1. `app.server.proto` is `rest`, not `rsocket`.
2. The application ports are 6892 and 6893.
3. The RSocket controller flags are replaced by the REST controller flags.
4. The four `app.security.*` values are added.
5. `spring.application.name` is named. **The golden set carries it, and this
   list omitted it until 2026-10-01.** A start without it fails with
   `Could not resolve placeholder 'spring.application.name'`.

```sh
java --enable-native-access=ALL-UNNAMED \
  -jar chat-deploy-memory/target/chat-deploy-memory-0.0.1-exec.jar \
  --app.nodeid=1 --app.key.type=long --app.server.proto=rest \
  --spring.application.name=mcp-agent-acceptance \
  --server.port=6892 --management.server.port=6893 \
  --app.service.core.key=memory --app.service.core.persistence=memory \
  --app.service.core.index=lucene --app.service.core.pubsub=memory \
  --app.service.core.secrets=memory \
  --app.service.composite=true --app.service.composite.auth=true \
  --app.service.security.userdetails=true --app.users.create=true \
  --spring.config.additional-location=classpath:/config/userinit.yml \
  --app.controller.persistence=true --app.controller.topic=true \
  --app.controller.user=true --app.controller.message=true \
  --app.controller.key=true --app.controller.index=true \
  --app.security.agent.client-id=31649af5-0154-4be5-8695-fda9d18b7981 \
  --app.security.agent.username=Agent \
  --app.security.agent.required-scope=chat.mcp \
  --app.security.jwt.jwk-path=/abs/path/server_keycert.jwk
```

### Three traps, each measured on 2026-10-01

1. **The stored process id belongs to the launcher, not to the JVM.**
   `chat-build` runs maven, and maven forks the server. So `$!` names the
   wrapper. Killing it leaves the server listening. Kill the process that holds
   the port, or kill the process group.
2. **The adapter classpath must be absolute.** The harness runs from
   `chat-mcp/src/test/client`, so a relative `chat-mcp/target/classes` resolves
   below that directory and the JVM reports
   `Could not find or load main class com.demo.chat.mcp.McpAdapterMainKt`.
3. **The generated password is a live credential.** The console line and any
   collected log hold it. Keep the log outside the repository, as the key and
   the token file already are.

The adapter configuration:

```properties
backendBaseUrl=http://127.0.0.1:6892
credentialFile=/secure/path/credential.txt
keyType=long
topicIds=1555003941702340608,1
```

Both ids are in `topicIds` on purpose. A refusal from the allowlist never
reaches a backend, so it would prove nothing about the deployment.

## Reproduce it

1. Start the authorization server with the step 1 command.
2. Request a token with the step 2 command. Read `scope` in the response.
3. Write the token to the credential file with the step 3 command.
4. Start a deployment with the four `app.security` values from step 4.
5. Create a topic through the deployment. Record its id, root and name.
6. Write the adapter configuration. Name the real id and one unserved id.
7. Run the harness. Read `code` on each call.
8. Replace the file with junk text. Run the harness again. Expect 401 on every
   call.
