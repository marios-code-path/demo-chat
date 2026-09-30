# REST agent authentication: design for review

Issue `CHAT-pgpmsgvr`. Status: **approved in three sections on 2026-09-29. No
code changed.**

This issue enforces authentication on the REST routes of a Demo Chat
deployment. The MCP adapter is the first caller that needs it.

`CHAT-ylvoiixm` stays unable to claim end-to-end MCP authorization until this
issue passes.

## Owner decisions

1. **One configured agent.** The token client id must match a configured client
   id. The chat identity comes from a separate configured agent username.
2. **The `sub` claim is ignored.** No token claim selects the chat identity.
3. **The agent resolves through the user store once, at startup.** A request
   never reads the user store for the agent.
4. **The resolved `ChatUserDetails` is the authentication principal.**
   `ContextIdentity` does not change. Its rule 5 answers the agent user key.
5. **No key travels in the token.** `KeyVerifier` keeps validating every route
   key.
6. **Every route handled by the application chain requires a valid token.**
   The actuator chain remains separate. The RSocket seam does not change in
   this issue.
7. **The deployment trusts the public key alone.** The core process parses
   `server_keycert.jwk`, discards the private material, and builds the decoder
   from the public key. A public-only JWK file is the preferred input.
8. **Every enforced request carries the scope that
   `app.security.agent.required-scope` names.** The selected deployment value is
   `chat.mcp`, which maps to the authority `SCOPE_chat.mcp`. The scope name is a
   deployment value and not a fixed contract.
9. **"Without permission" means "without the configured scope" in this issue.**
   Object-level denial stays with the grant and authorization work. See the
   boundary section.

## Measured state

Read from source at master `7b1a3e2e` on 2026-09-29. Implementation evidence is
recorded in the task commits below.

1. **`WebFluxSecurity.filterChain` requires an agent token.** The application
   chain validates the token and requires the configured scope. Anonymous
   authentication is not enabled on this chain.
2. **`RSocketServerConfiguration.rsocketSecurityAuthentication` permits every
   payload.** It carries the comment `TODO: lock down!`.
3. **`ContextIdentity.identityOf` is a closed list.** A `JwtAuthenticationToken`
   principal matches no rule, so a validated token alone answers no identity.
   Every access check would deny.
4. **The adapter already sends a bearer token.** `JdkBackendHttp.send` sets the
   header `Authorization: Bearer <credential>`. Only the deployment side is
   missing.
5. **`spring-boot-starter-oauth2-resource-server` is absent** from the core
   deployment classpath. `chat-webflux` carries `spring-boot-starter-oauth2-client`
   alone.
6. **The signing key is a file.** `server_keycert.jwk` holds the private key and
   an `x5c` chain. `shell-scripts/gen-dckeys.sh` writes it. `chat-build authserv
   --jwk` passes it, and `app.oauth2.jwk.path` names it.
7. **`AuthorizationServerConfig.jwtCustomizer` sets the algorithm alone.** It
   adds no client claim to a token.
8. **The authorization server writes no `client_id` claim by default.** For the
   `client_credentials` grant the `sub` claim holds the client id. This claim
   shape needs a test before the design rests on it.
9. **The agent client's scope set comes from `oauth2-client.yml`.** That file
   grants `openid` and `profile` alone.
10. **The application chain reaches five deployables.** `chat-deploy`, plus
    `chat-deploy-memory`, `chat-deploy-redis`, `chat-deploy-cassandra` and
    `chat-deploy-kafka` under `-Pexpose-webflux`. `chat-deploy-e2ee` does not
    declare that profile.
11. **`chat-web` is a separate application.** It does not embed `chat-webflux`.
12. **The MCP acceptance deployment ran alone.** It was `chat-deploy-memory`,
    with no authorization server and no Postgres.

## Design

### Configuration

Four properties, under a new `app.security` prefix. The shape follows
`app.actuator.username`, which `ActuatorWebSecurityConfiguration` reads today.

| Property | Meaning | Default |
|---|---|---|
| `app.security.agent.client-id` | The client id that the agent token must carry. | none, required |
| `app.security.agent.username` | The chat user handle that names the agent identity. | none, required |
| `app.security.agent.required-scope` | The scope that every enforced request must carry. The selected value is `chat.mcp`. | none, required |
| `app.security.jwt.jwk-path` | The JWK file that holds the trusted public key. | none, required |

**No property has a default, and an absent value fails the start.** This follows
the `app.nodeid` decision. A committed default would make every deployment the
same agent in silence.

The values bind through one typed class. A reader sees the required set in one
place.

### Startup

The agent resolves in a `SmartLifecycle` of `chat-webflux`, at phase
`Integer.MAX_VALUE - 3072`.

**`RootKeyStartup` is not the carrier.** It lives in `chat-deploy`, and
`chat-webflux` cannot add a step to it. The phase of this lifecycle sits above
`RootKeyStartup` at `Integer.MAX_VALUE - 4096` and below the reactive web server
at `Integer.MAX_VALUE - 2048`. So the roots are loaded, the agent is resolved,
and no server is listening.

Three failures stop the start and name the cause. A missing property. An agent
username that the user store does not hold. A JWK file that is absent,
unreadable, or not a key the decoder accepts.

The resolution runs once. No request reads the user store for the agent.

### The decoder

A `NimbusReactiveJwtDecoder` builds from the public key in the JWK file. The
process discards the private material after it parses the file. A public-only
JWK file removes the private material from the process entirely, and the design
prefers that input.

The decoder validates the signature and the `exp` claim. This issue adds no
audience validator. See the boundary section.

### The chain

The actuator chain keeps basic authentication and keeps its order. **The
configured-scope requirement does not reach an actuator route.** The actuator
chain matches first, so an actuator request never meets the application chain.

The application chain gains three things.

1. `oauth2ResourceServer { jwt { ... } }` with the decoder above.
2. One authority requirement. The chain builds the authority from
   `app.security.agent.required-scope` and requires it on every exchange. With
   the selected value the authority is `SCOPE_chat.mcp`. The old `permitAll`
   goes away.
3. A `Converter<Jwt, Mono<AbstractAuthenticationToken>>` that checks the
   `client_id` claim against the configured client id.

**`anonymous()` comes off the application chain.** Every exchange now needs an
authority, so anonymous authentication could turn a 401 into a 403 alone. A
caller with no credential must learn that it must authenticate. The anonymous
identity stays an RSocket decision, and `docs/IDENTITY-POLICY.md` takes that
correction.

### The authentication token

**`JwtAuthenticationToken` does not carry `ChatUserDetails` as its principal.**
This design uses a custom `AbstractAuthenticationToken` whose principal is the
startup-resolved `ChatUserDetails`. The token holds the validated `Jwt` beside
the principal, because the claims stay useful for diagnostics and for the scope
check.

`ContextIdentity.identityOf` then matches rule 5 and answers the agent user key
with no change to that class. One test calls `identityOf` with this
authentication and asserts the agent key.

### Denial semantics

| Request | Answer | Reaches the service |
|---|---|---|
| No `Authorization` header | 401 plus `WWW-Authenticate` | No |
| Malformed, expired, or bad signature | 401 | No |
| Valid token, wrong `client_id` | 401 | No |
| Valid token, without the configured scope | 403 | No |
| Valid agent token | The route runs | Yes |

**A wrong `client_id` raises a controlled authentication failure.** A
`BadCredentialsException` answers 401. An arbitrary exception from a converter
could answer a server error instead, and a server error is not a refusal.

**A wrong `client_id` answers the same status as an invalid token.** A caller
cannot learn which clients exist. The answer carries no object name, no count,
no key and no content.

### The `client_id` claim

The authorization server adds a `client_id` claim from
`registeredClient.clientId` when it encodes an **access token alone**. `JwtEncodingContext`
exposes the registered client and the token type, and the customizer checks
both. A refresh token and an identity token carry no such claim.

This claim is required, because a Spring Authorization Server JWT carries no
`client_id` claim by default. The `sub` claim holds the client id for the
`client_credentials` grant, and this design ignores `sub`.

### The scope authority

`JwtGrantedAuthoritiesConverter` maps a `scope` claim to the authority
`SCOPE_<name>` by default. So the configured scope becomes the required
authority with no custom converter. One test asserts the mapping for the
configured value.

`chat.mcp` is the value this deployment selects. It is not a fixed contract of
the code. An operator that changes `app.security.agent.required-scope` changes
the required authority, and the authorization server must grant the matching
scope.

The scope is one coarse route gate. It does not name a domain operation. Object
authorization stays with the resolved agent identity and the existing grants.

## What this issue does not do

- **It does not authorize an object.** "Without permission" means "without the
  configured scope". Object-level denial stays owned by the grant work in
  `CHAT-zhjltbky` and the wiring work in `CHAT-znprrzhn`.
- **It does not distinguish an expired token from an invalid one.** Both answer
  401. `CHAT-jkordfef` owns that decision.
- **It does not enforce authentication on RSocket.** `CHAT-jkordfef` and
  `CHAT-ileqgajf` own that gap.
- **It does not validate the audience.** See below.
- **It does not issue a credential.** `CHAT-rvcrzxvw` owns the account, the
  grant, and the token.
- **It does not add issuer discovery.** A deployment trusts a file. Key
  rotation needs a file replacement and a restart.
- **It denies every interactive `chat-web` user of this API deployment.** A user
  token does not match the agent client. Narrowing the route set is a later
  design decision. The owner recorded this consequence on 2026-09-29.

## The audience question

The `AccessTokenClaimsTests` test in `chat-authorization-server` encodes a
`client_credentials` access token. It measured `aud` as
`[31649af5-0154-4be5-8695-fda9d18b7981]`, which equals the client id.

- If `aud` equals the client id, the two checks hold the same value with
  different meanings. The `client_id` check binds the agent client. The
  audience binds a token to one resource. **The current token shape makes the
  values equal. A future resource token can separate them. File audience
  validation as its own issue, and keep that sentence in the issue. This action
  created `CHAT-okpgpxkj`.
- If `aud` is absent or different, add a validator to the decoder now.

The `client_id` check stays either way.

## Readers that change

| Reader | Change |
|---|---|
| `WebFluxAnonymousIdentityTests` | The premise inverts. A credential-less request answers 401 before any identity read. The test becomes a denial test, and the anonymous assertion moves to RSocket. |
| `BothChainsApplication`, `SecurityChainOrderTests`, `ActuatorBasePathOwnershipTests` | The open test route answers 401. Chain ownership stays the claim under test, and the 401 joins it. |
| `gate-embedding-launch.sh` | It calls `/persist/user/add`, `/persist/topic/add` and `/message/recall/topic` with no credential. **Each call changes from an unauthenticated call to a call that carries a minted test token.** A gate that keeps the old call stays at 401 and fails. |
| `docs/IDENTITY-POLICY.md` | The WebFlux row loses anonymous. Anonymous becomes an RSocket decision alone. |
| `docs/VECTOR-RECALL-API.md`, `docs/EMBEDDING-PROVIDERS.md` | Every procedure that calls a REST route carries a token. |
| `docs/MCP-REAL-DEPLOYMENT-ACCEPTANCE.md` | The note that no route read the bearer header becomes false. The document records the new reading. |
| `shell-scripts/build.sh` and `test-flags.sh` | The `rest` launch passes the four new values, because only `rest` declares `expose-webflux`. A core launch does not mount the application chain and needs none of them. |

**How a gate obtains a token.** The gate runs a standalone deployment with no
authorization server, so it cannot request one. The gate mints a short-lived
ES256 token from the JWK it already holds. `AuthorizationServerTestSigningKey`
builds a test key the same way. The deployment trusts that file, so no new trust
path appears.

**No reader keeps a 401 as its expected answer.** The embedding gate, the recall
gate and the MCP acceptance procedure each carry a token after this change.

## Tests

1. **Converter.** A matching `client_id` builds the agent authentication. A
   mismatched `client_id` raises the controlled failure. An absent `client_id`
   claim raises the controlled failure.
2. **Identity.** `ContextIdentity.identityOf(authentication)` answers the agent
   user key. The principal is a `ChatUserDetails`.
3. **Authority.** The agent token carries the authority built from the
   configured scope. The selected value answers `SCOPE_chat.mcp`.
4. **The denial matrix.** Five cases at the production chain over a real
   intercepted route. Each denied case asserts zero service calls.
5. **A composed context.** A test in `chat-deploy` boots the real chain with one
   real controller. The proof is a route and not an internal service call.
6. **Credential states.** An expired token and a token with a bad signature both
   answer 401.
7. **Zero downstream effects.** A denied `send` leaves persistence, the index
   and pub/sub untouched. The test asserts the mocks were never called.
8. **Startup failures.** A missing property, an unknown agent username, and an
   unreadable JWK each fail the context and name the cause.
9. **The authorization server.** The `aud` measurement, and the `client_id`
   claim present on an access token alone.
10. **The full `--ci` build.** The image rebuilds, because the `chat-shell`
    tests run against the image and not against the reactor.

## Issue updates before implementation

1. Narrow `CHAT-pgpmsgvr` to the REST routes. Its RSocket requirement has no
   work in this issue.
2. Record the RSocket gap on `CHAT-jkordfef` and `CHAT-ileqgajf`.
3. File the issuer-discovery issue, with its own startup and rotation tests.
4. File the audience issue, if the measurement says `aud` equals the client id.
5. Record the interactive-user consequence on `CHAT-pgpmsgvr`.

## Not measured

- **Which deployables an operator starts with `-Pexpose-webflux` today.** The
  pom declares the profile in five modules. The blast-radius tests cover the
  REST deployment path.
