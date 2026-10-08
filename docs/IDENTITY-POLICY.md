# The identity policy

How this application turns a security context into a chat identity.

`CHAT-ltvfmcvh`. Written on 2026-09-23. The measurement that produced it is
`docs/superpowers/specs/2026-09-23-anonymous-identity-diagnosis.md`.

## The rule

`ContextIdentity` in `chat-security` holds the whole rule. **Every access
decision reads its principal through that class.**

| State | Identity | Result |
|---|---|---|
| No security context | none | denied |
| A context with no authentication | none | denied |
| `isAuthenticated=false` | none | denied |
| `AnonymousAuthenticationToken` | the `Anon` root key | anonymous access |
| A `ChatUserDetails` principal | the key of its user | that user |
| A `User` principal | its own key | that user |
| Any other principal | none | denied |

The rules apply in that order. **The list is closed.** A principal type that
no rule names answers no identity.

## Four properties this policy holds

1. **Absence denies.** No context and no authentication both mean denied. An
   identity must be established, and it is never assumed.
2. **Anonymous is a decision, not an absence.** A caller receives the `Anon`
   root key because a seam authenticated it as anonymous. A caller that
   reached the service with nothing receives no identity.
3. **`isAuthenticated=false` denies.** A rejected credential carries this
   shape, and so does an expired one. The read refuses both before it looks
   at the principal. Both deployed seams refuse such a credential before the
   read runs, so this rule is the guard behind them.
4. **An unknown principal denies quietly.** No identity is not an error. The
   access methods end with `switchIfEmpty(Mono.just(false))`, so an empty
   answer refuses the access.

## Where an identity is established

A seam must establish the identity. The read never invents one.

| Transport | What establishes the identity |
|---|---|
| RSocket | `RSocketSecurity.simpleAuthentication` with `RSocketAuthenticationManager` for a password or an agent bearer, and `RSocketSecurity.anonymous` for a caller without one |
| WebFlux | A validated agent JWT with the configured client ID and scope |

**The WebFlux application chain does not enable anonymous authentication.**
Since `CHAT-pgpmsgvr` the chain requires a valid agent token on every route it
owns, so a credential-less request answers 401 before any identity read. The
`Anon` root key is an RSocket decision alone.

`AgentDenialMatrixTests` in `chat-deploy` pins the 401 over a real socket.

**`app.service.composite.auth` turns the access checks on.** `chat-build`
passes it on every core launch.

## The RSocket authentication seam

`CHAT-jkordfef`. The owner set this policy on 2026-10-07.

The seam reads the credential before any access check. Each case below has
one result.

| Credential | Frame | Result |
|---|---|---|
| None | setup and request | the `Anon` root key |
| A valid password | setup | the key of that user |
| A valid agent bearer | request | the key of the agent that its `client_id` selects |
| A wrong password | setup | `RejectedSetupException`, `Invalid Credentials` |
| A wrong password | request | `0x401` |
| A bearer with a bad signature, or text that is not a token | request | `0x401` |
| An expired bearer | request | `0x401` |
| A bearer from a client that no agent entry lists | request | `0x401` |
| A bearer without the required scope | request | `0x403` |
| An auth type that is not simple or bearer | setup | `RejectedSetupException` |
| An auth type that is not simple or bearer | request | `0x401` |
| The legacy `basic` authentication MIME type | request | `0x401` |

Six rules hold the table.

1. **No credential takes `Anon`, by design.** `RSocketSecurity.anonymous`
   makes that decision. The `Anon` key reaches the grant floor and nothing
   more.
2. **A caller that sends a credential never takes `Anon`.** A credential
   that the seam cannot accept is refused. It does not fall back.
   `UnsupportedCredentialRefusal` refuses an auth type that the converter
   cannot read, because the converter answers no authentication for it, and
   the anonymous filter would then run.
3. **Expired and invalid bearers share `0x401`.** RFC 6750 uses one
   `invalid_token` error for both. A client does one thing in both cases: it
   gets a new token. The message text can differ, and a client must not read
   it.
4. **A refusal stops the request before a handler runs.** No room is created.
   For an expired bearer and a wrong-client bearer,
   `CoreBearerDenialNoDownstreamEffectsTests` also shows that no store and no
   controller receives a call.
5. **Request metadata has precedence over setup metadata.** An invalid request
   credential does not fall back to the setup identity.
   `RSocketIdentityPrecedenceTests` pins it.
6. **A setup refusal arrives on the first request.** `connectTcp` completes
   with a requester, and `RejectedSetupException` lands on the first call
   that uses it. A client that reads only the connect result reads a refusal
   as a success.

**No outside input reaches an unsupported principal at this seam.** The seam
makes three kinds of authentication: `AnonymousAuthenticationToken`, a
`ChatUserDetails` principal from the password manager, and
`AgentAuthenticationToken`, which carries a `ChatUserDetails`. Rule 7 of
`ContextIdentity` denies any other principal, and `ContextIdentityTests` pins
that rule. The reachable form of an unsupported credential is an unknown auth
type, and rule 2 refuses it.

`RSocketAuthenticationSeamTests` in `chat-deploy-memory` sends each case to a
running server and mocks nothing. It reads the result from the stores. On
2026-10-07, all 11 tests passed. With the refusal removed from
`RSocketSecurityConfiguration`, exactly the three unsupported cases failed.

## What changed on 2026-09-23

Before this date the rule lived in four places and two of them disagreed.

- **No context answered the `Anon` root key.** An unauthenticated caller and
  an anonymous caller reached one identity. They are separate now.
- **`AnonymousAuthenticationToken` raised `ClassCastException`.** The read
  cast the principal, and the refusal depended on an `onErrorReturn` far from
  the decision. It answers the `Anon` root key now.
- **`isAuthenticated=false` answered the key of the user.** It denies now.
- **A `User` principal raised `ClassCastException`.** It answers its own key
  now.
- **`WebFluxSecurity.filterChain` requires an agent token.** An HTTP request
  without a credential answers 401 before it reaches an identity read.
- **`DefaultingAnonymousPayloadInterceptor` is removed**, with
  `ChatAnonymousAuthenticationToken`. The interceptor installed a token that
  the read could not use, and a measurement on 2026-09-23 showed the token
  never reached the read. `RSocketSecurity.anonymous` replaces it.
- **`SpringSecurityAccessBrokerService` and the cassandra composite seam** both
  read through `ContextIdentity` now. Neither carries a copy of the rule.

## Every read, audited on 2026-09-23

`ContextIdentity.kt:44` holds the only live read of
`ReactiveSecurityContextHolder` in main source. Two other lines name it and
both are commented out, in `CompositeAccessBeansConfiguration.kt:14` and
`CompositeServiceBeansConfiguration.kt:71`.

**One production resolver owns the policy.** Three callers read through it:

| Caller | Module |
|---|---|
| `SpringSecurityAccessBrokerService.getSecurityContextPrincipal` | chat-security |
| `CompositeServiceConfiguration.serviceAccessCompositeServiceAccessBeans` | chat-deploy-cassandra |
| `WebFluxAnonymousIdentityTests`, through the production filter chain | chat-webflux |

`CassandraCompositeIdentityTests` builds the cassandra beans and compares
their answer with `ContextIdentity` for every state in the table above. A
copied rule would drift, and that comparison is what catches it.

## How to add a principal type

1. Add a rule to `ContextIdentity.identityOf`.
2. Add one test to `ContextIdentityTests`.
3. Add one row to the table above.

Do not add a fallback. A fallback would reopen the closed list, and an
unknown principal would gain access in silence.

## What this policy does not do

- **It does not separate an expired credential from an invalid one.** REST
  answers 401 for both, and RSocket answers `0x401` for both. The owner chose
  one code on 2026-10-07. No identity exists for an expired credential.
- **It does not state what the `Anon` root key may reach.** The access broker
  answers that, and `CHAT-cvdcfczj` owns the full surface.
- **It does not make a refusal stop an operation.** The programmatic access
  wrappers in `chat-service-composite` call the broker and discard the answer,
  so no identity and a false answer both proceed. That path is latent, because
  no launch sets `app.service.composite.security`. `CHAT-ruapxetl` holds it.
  The live path is the `@PreAuthorize` wrappers, and Spring method security
  reads their boolean.
