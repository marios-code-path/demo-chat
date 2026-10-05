# More than one agent on one REST deployment

Issue `CHAT-frcrctdp`. Parent issue `CHAT-aqpcacwv`.

The owner approved the design in sections on 2026-10-05. Sigma reviewed the
design twice on the same day. This document includes the corrections of both
reviews.

## Problem

A REST deployment accepts one agent. `app.security.agent` holds one
`client-id` and one `username`. `AgentIdentity` holds one principal.
`AgentAuthenticationConverter` compares the token `client_id` with one value.
A token from any other client answers 401.

The core validates a relayed bearer token with the same single agent.

The authorization server cannot issue a token for a second agent. The
`client-init` profile registers `chatClient`, and that client carries no
`chat.mcp` scope. The `memory` profile registers one client from
`oauth2-client.yml`.

## Goal

- The token `client_id` selects the agent identity, on REST and on the core.
- An unknown client id answers 401 on REST and `0x401` on the core.
- Two entries that share a client id or a handle fail the start.
- Each agent identity resolves at startup. A missing user fails the start and
  names the handle.
- The required scope stays one value. This deployment selects `chat.mcp`.
- The authorization server registers one client per agent.

## Owner decisions

1. **A list replaces the single agent.** The scope moves to `app.security`.
2. **The operator names each agent at launch.** `chat-build` takes a
   repeatable `--agent CLIENT_ID=HANDLE` on `core`, `rest` and `authserv`.
3. **The core creates each agent user from the launch flag.** `userinit.yml`
   keeps the shipped `Agent` user alone.
4. **The authorization server generates each agent secret and prints it
   once.** No secret is on a command line or in a file.
5. **The shipped `chat-client` loses the `chat.mcp` scope.** `--agent` is the
   only way to make an agent client.
6. **An existing JDBC agent row with a different shape fails the start.** The
   operator deletes the row. The next start registers it again with a new
   secret.

### A deviation from the issue text

The issue says that `userinit.yml` declares the agent users. Decision 3
replaces that. The launch flag declares every agent user other than `Agent`.

## Configuration

```yaml
app:
  security:
    required-scope: chat.mcp
    agents:
      - client-id: 31649af5-0154-4be5-8695-fda9d18b7981
        username: Agent
      - client-id: 7c1e0b2a-0000-0000-0000-000000000000
        username: Claude
    jwt:
      jwk-path: /keys/server.jwk
```

`AgentSecurityProperties` binds `required-scope`, `agents` and `jwt`.

`requireComplete()` requires these values:

- `required-scope` is present and not blank.
- `jwt.jwk-path` is present and not blank.
- `agents` holds one entry or more.
- Each entry holds a `client-id` and a `username`, and neither is blank.

`requireComplete()` refuses these values, and each message names the value:

- A client id that two entries share:
  `app.security.agents names client id '<id>' twice.`
- A username that two entries share:
  `app.security.agents names username '<name>' twice.`
- A reserved username. See the next section.

The core keeps its current rule. With no `app.security` value, the core
refuses every bearer token. A partial set fails the start.

### Reserved handles

An agent handle must name a plain user. These handles are reserved:

- `Admin` and `Anon`, the two `ChatIdentity` names.
- Every handle in `app.security.service-accounts`. The default is `Service`.

**Why.** `ContextIdentity` answers the key of the agent user. The actor set of
a query holds the caller key. So an agent named `Admin` would reach every
`Admin` wildcard row. An agent named `Anon` is not a plain user. An agent
named `Service` would change the service account.

`chat-build` refuses a reserved handle. `AgentSecurityProperties` refuses it
too, on REST and on the core. So a launch that sets the properties directly
is also checked. The message reads:
`app.security.agents names reserved username '<name>'. An agent must be a plain user.`

### The comparison rule ignores case

The reserved check and the duplicate username check compare handles after
`lowercase(Locale.ROOT)`. So `admin` is reserved, and `Claude` and `claude`
are one handle.

**Why.** `LuceneIndex` stores `handle` as a `TextField`, and
`StandardAnalyzer` lowercases each token. So a lookup for `admin` finds the
user `Admin`. A check that compares case would let `--agent x=admin` select
the `Admin` identity.

The client id duplicate check compares exactly. A client id is an OAuth
value, and OAuth compares it exactly.

### The old keys fail the start

`AgentSecurityPropertiesGuard` reads the `Environment`. It fails the start
when any `app.security.agent.*` key is present:

`app.security.agent.* is replaced by app.security.agents[n] and app.security.required-scope. See CHAT-frcrctdp.`

Spring ignores an unbound key. Without the guard, an old launch would start
with no agent and refuse every token.

## Identity selection

`AgentIdentity` becomes `AgentIdentities`. It maps a client id to a
`ChatUserDetails`. A value is written once at startup.

`AgentIdentityLifecycle` resolves every entry before the web server starts.
It keeps phase `Int.MAX_VALUE - 3072`.

The lookup answers every user whose indexed handle matches the query. The
lifecycle keeps only a user whose `handle` equals the configured handle
exactly. A handle that keeps zero users, or two users or more, fails the
start:

`The agent username '<name>' for client '<id>' answered <n> users. It must answer exactly one user.`

Two entries that resolve to one user key fail the start:

`app.security.agents clients '<id-a>' and '<id-b>' resolve to one user key.`

The case rule above makes this case rare. The key check is the last guard,
because a store can hold two users that the index cannot separate.

`AgentAuthenticationConverter` reads `client_id` and looks it up in
`AgentIdentities`. A missing claim or an unknown client raises
`BadCredentialsException`. REST then answers 401. The core answers `0x401`.

REST and the core use the same converter class. Each side builds its own
instance from its own properties.

These parts do not change: `RequiredScopeAuthenticationManager`, the
`hasAuthority` rule of the REST chain, `AgentJwtDecoderFactory`, and the
`ROLE_AGENT` authority.

### The two lists must agree

REST and the core each carry an agent list. No check compares them. Assume
REST lists a client and the core does not. REST then accepts the token, the
core refuses it with `0x401`, and REST answers 401. The operator keeps the two
lists equal through `chat-build`.

On the `chat-build rest` facade, each handle resolves through the core over
RSocket. So the core must start first and hold every agent user.

## chat-build

`--agent CLIENT_ID=HANDLE` is repeatable.

- A handle holds letters, digits and underscores alone. A dot or a bracket
  breaks the property path. A hyphen meets the Lucene name token defect,
  `CHAT-hajmhslp`.
- A reserved handle exits 1 and names it.
- A duplicate client id or handle exits 1 and names it.
- `--agent` requires `--jwk PATH` on `core` and on `rest`.

`--agent-scope` stays. Its default is `chat.mcp`.

`--agent-client-id` and `--agent-username` are removed. A launch that passes
either one exits 2 and names `--agent`.

| Service | Emitted properties |
|---|---|
| `core` | `app.init.initialUsers[<H>].handle=<H>`, `.name=<H>`, `.imageUri=chatimg://agent.png`, `app.security.agents[i].client-id`, `app.security.agents[i].username`, `app.security.required-scope` |
| `rest` | `app.security.agents[i].client-id`, `app.security.agents[i].username`, `app.security.required-scope` |
| `authserv` | `app.oauth2.agents[i].client-id`, `app.oauth2.agents[i].username`, `app.oauth2.agent-scope` |

The core creates an agent user only when the launch runs the `users` init
phase. A core launch without that phase fails the start and names the handle.

`--agent <id>=Agent` changes the display name of the shipped user from
`MCP ADAPTER` to `Agent`. The owner accepts this.

## Authorization server

`AgentClientProperties` binds `app.oauth2.agents` and `app.oauth2.agent-scope`.
`AgentClientFactory` builds one `RegisteredClient` per entry:

| Field | Value |
|---|---|
| `id` | the client id |
| `clientId` | the client id |
| grant | `client_credentials` alone |
| authentication | `client_secret_basic` |
| scope | `app.oauth2.agent-scope` |
| consent | not required |
| secret | 32 random bytes, stored with the `{bcrypt}` prefix |
| access token format | `SELF_CONTAINED`, a signed JWT |
| access token lifetime | 300 seconds |

The factory sets the token format and the lifetime explicitly. It does not
rely on the library defaults. REST and the core decode a JWT locally, so an
opaque `REFERENCE` token would answer 401 on every call.

The server prints each new secret once:

`Generated secret for agent client '<id>' (<handle>): <secret>`

### The memory profile

`InMemoryRegisteredClientRepository` holds `chat-client` and every agent
client. Each start generates a new secret for every agent.

**A restart invalidates every agent secret.** Each adapter must then request a
new token. A token issued before the restart stays valid until it expires,
because the signing JWK comes from a stable file. The factory sets that
lifetime to 300 seconds.

### The client-init profile

The server reads each agent client from the repository.

- No row: the server saves the client and prints the secret.
- A row with the agent shape: the server keeps it and logs that the secret is
  unchanged.
- A row with another shape: the start fails and names the client id and each
  field that differs. The operator deletes the row, and the next start
  registers it again.

The agent shape is every row of the table above except `id`, `clientId` and
the secret value:

- the grant set is `client_credentials` alone
- the authentication method set is `client_secret_basic` alone
- the scope set is `app.oauth2.agent-scope` alone
- consent is not required
- the stored secret starts with `{bcrypt}`
- the access token format is `SELF_CONTAINED`
- the access token lifetime is 300 seconds

A row that differs in any of these fails the start. The message names each
field that differs.

**A lost secret has no recovery path.** The operator deletes the row.

`ClientInitializer` now registers every entry of
`spring.security.oauth2.authorizationserver.client.*`. It does not read
`chat-client` alone.

### Client id collisions

The start fails when an agent client id is also present in another source:

- `app.oauth2.client`
- any entry of `spring.security.oauth2.authorizationserver.client.*`
- the `--clientpath` file
- an existing repository row that does not have the agent shape

Two agent entries with one client id also fail the start. Each message names
the client id and the source.

**Why.** `ClientLoader.saveClient` saves a client only when its id is absent.
So an older row would keep its secret, grants and scopes with no warning.

### The shipped chat-client

`oauth2-client.yml` removes `chat.mcp` from `additional-scopes`.

No shipped default JDBC registration carries `chat.mcp`. The `client-init`
profile saves `chatClient` from `application.yml`, and its scopes are `auth`,
`message`, `topic`, `user` and `openId`. It never saves the client of
`oauth2-client.yml`. An operator override or a `--clientpath` file can still
store a client with `chat.mcp`. The collision check above refuses such a row
when its client id is also an agent client id. This work does not change any
other stored row.

### A defect found and not repaired

`chat-build authserv` passes `--clientpath='classpath:client.json'`. Main
resources hold no `client.json`. This work files the defect and does not
repair it.

## Tests

### Unit

- `AgentSecurityPropertiesTests`: a duplicate client id, a duplicate
  username, each reserved username, an empty list, a missing scope, and the
  old key guard. Each case fails and names the value. Case variants are
  separate cases: `admin`, `ANON` and `service` are refused, and `Claude`
  with `claude` is a duplicate.
- `AgentAuthenticationConverterTests`: two agents. Each token selects its own
  principal. A missing `client_id` and an unknown one each raise
  `BadCredentialsException`.
- `AgentIdentityLifecycleTests` and `AgentIdentityResolutionTests`: both
  handles resolve. A missing second handle fails the start and names it. A
  lookup that answers `Claude` for the configured handle `claude` keeps zero
  users and fails the start. Two entries that resolve to one user key fail
  the start.
- A binding test reads `app.init.initialUsers` from a yml source and from
  system properties together, and finds both users. The system properties
  use the bracket form, `app.init.initialUsers[<H>]`. The test includes the
  pair `Bot_1` and `Bot1`, and finds two users with their own handles.
  **Why.** Spring removes `_` from a map key without brackets. So `Bot_1` and
  `Bot1` would bind to one key, and one user would replace the other. A
  second binding test sets `app.security.agents` in both sources, and finds
  the system property list alone.
- `AgentClientRegistrationTests`: two agent clients each receive a
  `client_credentials` token. The token `scope` is `chat.mcp`, and the token
  `client_id` is that client. Each token is a JWT that the agent decoder
  accepts, and it expires 300 seconds after it is issued. This proves that a
  `{bcrypt}` secret is accepted.
- `AgentClientCollisionTests`: one case per collision source.
- `ClientInitializerTest`: save and print once, and keep a matching row. Each
  field of the agent shape gets its own drifted row, and each one fails the
  start and names that field. The fields are grant, authentication method,
  scope, consent, secret prefix, token format and token lifetime.
- `chat-build`: `test-flags.sh` golden cases for `core`, `rest` and
  `authserv` with two `--agent` values. Further cases refuse a reserved
  handle, a duplicate, and each removed flag.

### Integration

- `CoreBearerDenialNoDownstreamEffectsTests` adds an unlisted client. The
  core answers `0x401` and makes zero downstream calls.
- `RestToCoreBearerDeploymentTests` launches both processes with
  `--agent client-a=Agent --agent client-b=Claude`.
  - Each agent creates a room. Each room owner row names that agent key.
  - REST reads its own principal for each token and finds the correct agent.
  - A token from an unlisted client answers 401 on REST.
- A single process REST test in `chat-deploy-memory` starts with two agents.
  Each agent creates a room. Each owner row names that agent key. In this
  launch, the REST principal writes the owner row.

### Mutations

The converter class is shared. A mutation of the class breaks both sides at
once, so the mutations target the wiring.

- In `AgentSecurityConfiguration`, select the first agent for every token. The
  single process owner row test and the REST principal assertion must fail.
- In `RSocketAgentSecurityConfiguration`, select the first agent for every
  token. The relay owner row test must fail.
- Remove the duplicate client id check. Its test must fail.
- Remove the reserved handle check. Its test must fail.
- Compare reserved handles with case. The `admin` case must fail.
- Remove the exact handle filter in the lifecycle. The `claude` case must
  fail.
- Remove the bracket form from the emitted core flags. The golden case and
  the `Bot_1` binding case must fail.

### Gate

`build-health.sh --ci` with an empty `DOCKER_CONFIG` exits 0 and reports no
drift. The shell image id moves.

## Documents

- `docs/MCP-CREDENTIAL-ISSUANCE.md`: the procedure uses `--agent` on the
  authorization server, the core and REST. Limit 3 closes.
- `docs/REST-TOKEN-RELAY.md`: review the prose first. Then bind
  `AgentIdentities.kt` in place of `AgentIdentity.kt` with `drift link`. Then
  run `drift check`.
- `docs/BUILD.md`, `docs/EMBEDDING-PROVIDERS.md`, `docs/VECTOR-RECALL-API.md`,
  `docs/MCP-ADAPTER.md` and `shell-scripts/README-chat-build.md`.
- `docs/MCP-REAL-DEPLOYMENT-ACCEPTANCE.md` records past runs. It gains a
  pointer to the new flag shape alone.
- `shell-scripts/vector/gate-embedding-launch.sh` uses the new properties.
- `forward-register.md` gains a section.

## Not measured by this work

- Secret rotation, and an authorization server restart while an adapter runs.
- A check that the REST list and the core list agree. No such check exists.
