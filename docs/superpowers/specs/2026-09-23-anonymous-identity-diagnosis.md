# The anonymous identity, measured

`CHAT-ltvfmcvh`. Measured on 2026-09-23 at master `fe9e8581`.

**This document changes no behaviour.** It states what the code does today.
The owner asked for the distinction to be measured before anything moves.

Two test classes hold the measurements:

- `chat-security/src/test/kotlin/com/demo/chat/test/AnonymousIdentityDiagnosisTests.kt`
  reads the resolver alone.
- `chat-service-controller/src/test/kotlin/com/demo/chat/test/rsocketseam/AnonymousRSocketSeamDiagnosisTests.kt`
  reads the RSocket interceptor and the resolver as one pair.

## What the resolver answers

`SpringSecurityAccessBrokerService.getSecurityContextPrincipal` reads
`it.authentication?.principal as ChatUserDetails<T>?`.

**The answer depends on the runtime class of the principal.** It does not
depend on whether the caller authenticated.

| Security context | Identity that reaches the access broker |
|---|---|
| No context at all | The `Anon` root key |
| A context whose authentication is null | The `Anon` root key |
| `AnonymousAuthenticationToken`, principal `"anonymousUser"` | None. The cast fails and the call is denied |
| A token whose principal is a `User` | None. The cast fails and the call is denied |
| A token whose principal is a `ChatUserDetails` | That user key |
| The same token with `isAuthenticated=false` | **That same user key** |

## Finding 1: the two anonymous paths disagree

The issue records four decision sites. Two of them meet on the RSocket seam,
and they do not agree.

1. `DefaultingAnonymousPayloadInterceptor` replaces an
   `AnonymousAuthenticationToken` with a `ChatAnonymousAuthenticationToken`.
   The principal of the replacement is a `User`.
2. The resolver casts the principal to `ChatUserDetails`. A `User` is not a
   `ChatUserDetails`.

So the caller that the interceptor prepared is **denied**. A caller who sends
no credential, and who therefore never reaches the interceptor, is **granted**
the anonymous identity.

The measurement is direct. The pair runs in one process in
`AnonymousRSocketSeamDiagnosisTests`, and the broker never sees a key.

## Finding 2: the read never consults `isAuthenticated`

A token that reports `isAuthenticated=false` carries its principal through to
the access decision. It reaches the same key as an authenticated caller.

**This is wider than the risk the issue names.** The issue asks about an
expired credential reaching the anonymous identity. The measurement shows a
rejected credential reaching the **user** identity.

## Finding 3: no deployed seam validates a token

The risk in the issue assumes a token expiry threshold. Neither seam that
reaches these four sites validates a token.

| Seam | What it does |
|---|---|
| `WebFluxSecurity` | `anyExchange().permitAll()`. The class comment states that it adds no authentication |
| `RSocketServerConfiguration` | `simpleAuthentication`, then `permitAll` on setup, exchange and request. It carries a `TODO: lock down!` |

Token validation exists in `chat-web`, which is a login client, and inside
`chat-authorization-server` for its own routes. **Neither one guards the four
sites.**

So an expired token cannot reach these resolvers today, because nothing reads
a token there. The confusion the issue names is not live. It becomes live on
the day authentication is added, and finding 2 says it will arrive in a
stronger form than the issue expects.

## What is live today

The access path is not dormant. `chat-build` passes
`-Dapp.service.composite.auth` on every core launch, and the golden flag
files record it. `MethodSecurityConfiguration` supplies the resolver behind
that property, and eight access classes carry `@PreAuthorize`.

So finding 1 is live. Finding 2 is live for any caller that can place an
unauthenticated token in the context.

## Finding 4: the interceptor token does not reach the resolver

Finding 1 predicts that an anonymous RSocket caller is denied. The running
system does not deny one.

Three facts, each read on 2026-09-23:

1. `MessageServiceAccess.send` carries
   `@PreAuthorize("@chatAccess.hasAccessTo(#req.dest(), 'SEND')")`, and
   `TopicServiceAccess.addRoom` carries
   `@PreAuthorize("@chatAccess.hasAccessToDomain('MessageTopic', 'NEW')")`.
   So both operations read the resolver.
2. `ShellPubSubCommandsTests` names no login, no identity and no whoami. The
   shell sends no credential unless `LoginCommands.login` runs, because
   `loginMetadata` starts empty.
3. Those tests pass in the `--ci` run, with `app.service.composite.auth` set.

**So an unauthenticated caller reaches an access checked method and is
allowed.** The identity it reaches must be the `Anon` root key, which is the
answer for an absent context or a null authentication. It is not the answer
for the token that the interceptor installs.

The conclusion is that `DefaultingAnonymousPayloadInterceptor` does not change
what the resolver reads. **Finding 1 is a real disagreement between two
readers, and it is not a live outage.** The branch it breaks is not reached.

This also asks what the interceptor is for. It may be dead on this path.

## What is not measured

- **Why the interceptor has no effect.** The evidence above is behavioural.
  Nothing in this work instrumented a running server to show which context
  object the resolver reads. **Do this before deleting or moving the
  interceptor.**
- **The four sites were read, and three were measured.**
  `CompositeServiceConfiguration` in `chat-deploy-cassandra` repeats the
  resolver shape. No test drives it.
- **What access the `Anon` root key holds.** The tests show it is enough for
  `send` and `addRoom`. The full grant is not read here.

## The decision, which belongs to the owner

The issue offers three candidates. The measurement changes what each one buys.

1. **One resolver that every seam calls.** This repairs finding 1 directly,
   because the disagreement is between two readers of one principal. It does
   not repair finding 2.
2. **Reject an expired token in the filter chain.** There is no filter chain
   to put it in. This choice is a request to add authentication first.
3. **A distinct identity for an expired session.** This repairs finding 2 and
   needs finding 3 resolved first, because nothing produces an expired session
   today.

A fourth option follows from the measurement, and the issue does not name it.

4. **Make the read state its own contract.** The cast is the defect in both
   findings. A read that asked for a key, rather than casting a principal,
   would refuse an unknown principal type loudly and would be able to consult
   `isAuthenticated`.

**The order the measurement suggests.** Option 4 first, because the cast is
the one defect that both findings share, and because it is the only option
that does not wait on a decision about authentication. Option 1 follows it
naturally, because a stated contract has one home. Options 2 and 3 wait for
authentication to exist.

**Read finding 4 before touching the interceptor.**
