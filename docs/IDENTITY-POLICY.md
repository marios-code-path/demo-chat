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
   at the principal.
4. **An unknown principal denies quietly.** No identity is not an error. The
   access methods end with `switchIfEmpty(Mono.just(false))`, so an empty
   answer refuses the access.

## Where an identity is established

A seam must establish the identity. The read never invents one.

| Transport | What establishes the identity |
|---|---|
| RSocket | `RSocketSecurity.simpleAuthentication` for a credential, and `RSocketSecurity.anonymous` for a caller without one |
| WebFlux | `ServerHttpSecurity.anonymous`, called by `WebFluxSecurity.filterChain` |

**Reactive Spring Security does not enable anonymous authentication by
default.** `ServerHttpSecurity.build` reads `if (this.anonymous != null)`, and
that field stays null until `anonymous` is called. The servlet side defaults it
on. This side does not.

An earlier version of this document said the opposite, and
`WebFluxSecurity.filterChain` did not call `anonymous`. Every HTTP request
without a credential was denied for that period.
`WebFluxAnonymousIdentityTests` pins the repair.

**`app.service.composite.auth` turns the access checks on.** `chat-build`
passes it on every core launch.

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
- **`WebFluxSecurity.filterChain` calls `anonymous`.** It did not on
  2026-09-23, and an HTTP request with no credential reached no identity.
- **`DefaultingAnonymousPayloadInterceptor` is removed**, with
  `ChatAnonymousAuthenticationToken`. The interceptor installed a token that
  the read could not use, and a measurement on 2026-09-23 showed the token
  never reached the read. `RSocketSecurity.anonymous` replaces it.
- **`SpringSecurityAccessBrokerService` and the cassandra composite seam** both
  read through `ContextIdentity` now. Neither carries a copy of the rule.

## How to add a principal type

1. Add a rule to `ContextIdentity.identityOf`.
2. Add one test to `ContextIdentityTests`.
3. Add one row to the table above.

Do not add a fallback. A fallback would reopen the closed list, and an
unknown principal would gain access in silence.

## What this policy does not do

- **It does not validate a token.** No deployed seam validates one today.
  `WebFluxSecurity` permits every exchange. `RSocketServerConfiguration`
  permits every payload and carries `TODO: lock down!`.
- **It does not separate an expired session from a rejected one.** Both deny.
  A distinct identity for an expired session needs a seam that can tell them
  apart, and no seam can today.
- **It does not state what the `Anon` root key may reach.** The access broker
  answers that, and `CHAT-cvdcfczj` owns the full surface.
