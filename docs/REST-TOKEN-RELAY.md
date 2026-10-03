# REST token relay

These diagrams describe `CHAT-mpjtnpqv` as the code implements it at
`e036cf67`. They were read from source. They were not drawn from the spec.

The sources are the
[design specification](superpowers/specs/2026-10-03-rest-token-relay-design.md)
and the [implementation plan](superpowers/plans/2026-10-03-rest-token-relay.md).

Two gaps are marked **GAP** in the diagrams. Each one is read from source and
is not measured. See [Known gaps](#known-gaps).

## Components on the main path

An agent token enters at the REST facade. The facade relays the token to the
core on every delegated request.

```mermaid
flowchart LR
    A["MCP adapter<br/>or other agent"]
    AS["Authorization server<br/>client_credentials"]

    subgraph REST["REST facade (app.primary=REST)"]
        RC["AgentResourceServerChain<br/>ES256 signature, expiry<br/>client_id, SCOPE_chat.mcp"]
        CTL["Composite REST controller"]
        MR["MetadataRSocketRequester<br/>DeferredRequestSpec"]
        ADV["KeyRefusalAdvice<br/>401 or 403"]
    end

    subgraph CORE["Core (app.server.proto=rsocket)"]
        PI["PayloadSocketAcceptorInterceptor"]
        RM["RSocketAuthenticationManager"]
        MS["Method security<br/>@PreAuthorize"]
        SVC["Composite services<br/>and stores"]
    end

    A -->|1 token request| AS
    A -->|2 HTTP + Bearer| RC
    RC -->|3 AgentAuthenticationToken| CTL
    CTL -->|4 call| MR
    MR -->|5 RSocket request<br/>+ bearer metadata| PI
    PI --> RM
    PI -->|6 authenticated| MS
    MS -->|7 allowed| SVC
    PI -. typed refusal .-> MR
    MR -. CoreSecurityRefusal .-> ADV
```

The `chat-security` module holds the shared parts. These are
`AgentSecurityProperties`, `AgentJwtDecoderFactory`,
`AgentAuthenticationConverter`, `AgentAuthenticationToken`, and
`AgentIdentity`. Both processes use the same classes.

## The request, end to end

This sequence shows a room creation by the agent. The REST facade and the core
are separate processes.

```mermaid
sequenceDiagram
    autonumber
    participant Ag as Agent
    participant RC as REST chain
    participant Ctl as REST controller
    participant MR as MetadataRSocketRequester
    participant EI as Error interceptor (100)
    participant AU as Authentication (200)
    participant AN as Anonymous (300)
    participant AZ as Authorization (400)
    participant MS as Method security
    participant Svc as Composite service

    Ag->>RC: POST /topic/new, Authorization: Bearer JWT
    RC->>RC: Decode ES256 with the public JWK, check expiry
    RC->>RC: client_id equals app.security.agent.client-id
    RC->>RC: hasAuthority SCOPE_ + required-scope
    RC->>Ctl: Context holds AgentAuthenticationToken(Agent, jwt)
    Ctl->>MR: TopicClient.addRoom, route prefix + "topic-add"
    Note over MR: At subscription, read the reactive context.<br/>Attach BearerTokenMetadata(jwt.tokenValue).
    MR->>EI: Request payload with bearer metadata
    EI->>AU: chain.next
    AU->>AU: RSocketAuthenticationManager routes the bearer token
    AU->>AU: JWT manager: ES256 signature, expiry
    AU->>AU: AgentAuthenticationConverter: client_id
    Note over AU: GAP 1: no scope check here
    AU->>AN: Authentication present, so anonymous is skipped
    AN->>AZ: Composite route is permitAll
    AZ-->>MS: Request proceeds with the Agent context
    MS->>MS: ContextIdentity reads the Agent user key
    MS->>Svc: addRoom as Agent
    Svc-->>Ctl: Room, owner row names Agent
    Ctl-->>Ag: 201 Created
```

The numbers in brackets are the interceptor orders. Spring Security sets
`AUTHENTICATION` to 200, `ANONYMOUS` to 300, and `AUTHORIZATION` to 400.
`RSocketSecurityErrorPayloadInterceptor` returns order 100. So it wraps all
three, and it sees every refusal that they raise.

`RestToCoreBearerDeploymentTests` measures this path. It reads the stored
owner row and compares its principal with the `Agent` key.

## The refusal path

A refusal inside the interceptor chain reaches REST as a typed error. The
decoder does not read message text.

```mermaid
flowchart TD
    E1["AuthenticationException<br/>in the chain"] --> EI
    E2["AccessDeniedException<br/>from authorizePayload"] --> EI
    EI["RSocketSecurityErrorPayloadInterceptor"]
    EI -->|"ApplicationErrorException<br/>{version:1, kind:AUTHENTICATION}"| D
    EI -->|"ApplicationErrorException<br/>{version:1, kind:AUTHORIZATION}"| D
    D["CoreSecurityErrorDecoder<br/>reads version and kind"]
    D -->|AUTHENTICATION| R401["CoreAuthenticationRefusal<br/>HTTP 401"]
    D -->|AUTHORIZATION| R403["CoreAuthorizationRefusal<br/>HTTP 403"]
    D -->|"no envelope,<br/>other version or kind"| RAW["Original error<br/>passes unchanged"]

    H["AccessDeniedException<br/>from @PreAuthorize<br/>in the handler"] -->|"GAP 2: outside<br/>the interceptor chain"| RAW
```

`authorizePayload` raises an authorization refusal only for the core routes:
`persist.**`, `index.**`, `pubsub.**`, `secrets.**`, `key.key`, and
`key.rem`. Each requires `ROLE_SERVICE` or `ROLE_ADMIN`. The agent principal
holds `ROLE_AGENT`, so the core refuses these routes to the agent.

## REST client: which metadata a request carries

`DeferredRequestSpec` chooses the metadata when the request is subscribed, not
when the requester is built. One shared connection can serve many callers.

```mermaid
stateDiagram-v2
    [*] --> ReadContext: request subscribed
    ReadContext --> Bearer: context holds AgentAuthenticationToken
    ReadContext --> Provider: no context, or another authentication
    Provider --> Simple: provider answers SimpleRequestMetadata
    Provider --> NoMetadata: provider answers EmptyRequestMetadata
    Bearer --> [*]: BearerTokenMetadata(jwt.tokenValue)
    Simple --> [*]: username and password metadata
    NoMetadata --> [*]: route metadata only
```

`Simple` covers the shell login and the authorization server `Service`
credential. Neither path changed. The REST facade holds no `Service`
credential, so a REST call without an agent token carries no metadata. The
REST chain refuses such a call with 401 before delegation.

## Core: identity of one request

Request metadata has precedence over setup metadata. An invalid request
credential never falls back to the setup identity or to `Anon`.

```mermaid
stateDiagram-v2
    [*] --> Inspect: payload arrives
    Inspect --> SetupIdentity: request carries no auth metadata
    Inspect --> SimpleAuth: simple metadata
    Inspect --> BearerAuth: bearer metadata

    SetupIdentity --> Authenticated: setup frame authenticated
    SetupIdentity --> Anonymous: setup frame had no credential

    SimpleAuth --> Authenticated: password matches
    SimpleAuth --> RefusedAuthn: bad credential

    BearerAuth --> RefusedAuthn: core has no JWK configuration
    BearerAuth --> Decode: JWK configured
    Decode --> RefusedAuthn: bad signature or expired
    Decode --> ClientCheck: valid ES256 token
    ClientCheck --> RefusedAuthn: client_id differs
    ClientCheck --> AgentAuthenticated: client_id matches
    note right of ClientCheck
        GAP 1: the core does not check
        the required scope here
    end note

    Authenticated --> RouteRules
    AgentAuthenticated --> RouteRules
    Anonymous --> RouteRules

    RouteRules --> RefusedAuthz: core route without SERVICE or ADMIN
    RouteRules --> Handler: composite route, permitAll
    Handler --> MethodCheck: ContextIdentity reads the key
    MethodCheck --> Served: @PreAuthorize allows
    MethodCheck --> Denied: @PreAuthorize denies

    RefusedAuthn --> [*]: AUTHENTICATION envelope
    RefusedAuthz --> [*]: AUTHORIZATION envelope
    Served --> [*]
    Denied --> [*]: plain Access Denied (GAP 2)
```

`RSocketIdentityPrecedenceTests` covers the four entry branches.
`CoreBearerWithoutJwkTests` covers the refusal with no JWK.
`CoreBearerDenialNoDownstreamEffectsTests` covers the expired and
wrong-client refusals. It checks that the controller, persistence, index,
secrets, and pubsub receive no call.

## Core: startup states

The core refuses to start in every state where it would serve without the
security chain or with a partial agent configuration.

```mermaid
stateDiagram-v2
    [*] --> ProtoCheck
    ProtoCheck --> NotRSocket: app.server.proto is not rsocket
    ProtoCheck --> AuthCheck: app.server.proto=rsocket

    AuthCheck --> FailAuth: composite.auth is absent or not exactly "true"
    AuthCheck --> AgentProps: composite.auth=true

    AgentProps --> BearerDisabled: no app.security value
    AgentProps --> FailPartial: some values, set incomplete
    AgentProps --> LoadJwk: client id, username, scope, and JWK path

    LoadJwk --> FailJwk: unreadable, not EC, or not P-256
    LoadJwk --> ResolveAgent: decoder built

    ResolveAgent --> FailAgent: username matches zero or many users
    ResolveAgent --> BearerEnabled: exactly one user

    BearerDisabled --> Serving
    BearerEnabled --> Serving

    FailAuth --> [*]: An RSocket server requires app.service.composite.auth=true.
    FailPartial --> [*]: names the missing property
    FailJwk --> [*]
    FailAgent --> [*]
    NotRSocket --> [*]: no RSocket security beans
    Serving --> [*]
```

`BearerDisabled` still refuses bearer metadata, through
`BearerAuthenticationNotConfiguredManager`. It does not ignore it.

`AgentIdentityLifecycle` resolves the agent at phase `Int.MAX_VALUE - 3072`.
The root keys load earlier, at phase `Int.MAX_VALUE - 4096`. Both run before
the servers start.

## Known gaps

Both gaps are read from source at `e036cf67`. Neither is measured.

### GAP 1: the core does not check the required scope

The REST chain requires `SCOPE_<required-scope>` through
`AgentResourceServerChain`. The core bearer path does not. It checks the
signature, the expiry, and `client_id` alone.

So a token from the agent client with no `chat.mcp` scope gets 403 at REST.
The same token sent directly to the core authenticates as the agent. The
2026-09-30 acceptance run measured that the authorization server issues such
a token.

The issue scope requires the scope check at the core boundary.
`CoreBearerDenialNoDownstreamEffectsTests` has no wrong-scope case.

### GAP 2: a method security denial is not typed

The typed envelope covers refusals inside the interceptor chain only. A
`@PreAuthorize` denial happens in the handler, after the chain completes. So
it reaches REST as a plain `ApplicationErrorException` with `Access Denied`.

`CoreSecurityErrorDecoder` passes it through unchanged. `KeyRefusalAdvice`
holds no handler for it. The REST status for this case is not measured.

This is the main 403 case on the REST path. An example is an agent that
deletes a room it does not own. `RestCoreAuthenticationErrorMappingTests`
reads the advice annotations only. It does not send a request through the
REST boundary.
