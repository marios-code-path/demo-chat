# Composite access enforcement

Issue `CHAT-znprrzhn`, under the parent `CHAT-ltvfmcvh`.

**Status: draft, and it waits for the owner review.** Nothing in this document
is implemented.

Measured on 2026-10-01 at master `87657e0c`.

## The defect

`chat-security` holds 53 `@PreAuthorize` and `@PostFilter` annotations across
8 interfaces. **No production type implements any of them.**

Every controller delegates to a plain service:

```kotlin
@Controller
@MessageMapping("topic")
class TopicServiceController<T, V>(b: CompositeServiceBeans<T, V>) :
    TopicServiceControllerMapping<T, V>, ChatTopicService<T, V> by b.topicService()
```

Kotlin interface delegation calls the raw `TopicServiceImpl`. Spring method
security advises a bean that carries the annotation. Nothing here carries one,
so nothing is advised.

`app.service.composite.auth` is set on every core launch. It supplies the
`chatAccess` bean and it enables method security. **It does not put an
annotation on any bean.**

So no authorization check runs in any deployed composition. The matrix in
`docs/ANONYMOUS-AUTHORIZATION.md` states what the configuration means, and not
what a deployment enforces.

## The decision of the owner, 2026-10-01

1. **The controller carries the annotated interface.** It declares the existing
   `*ServiceAccess` interface by delegation, so the controller bean becomes the
   annotated boundary. The annotations stay in one place. No new port is
   needed and no dependency changes.
2. **This pass wires the composite surface alone.** The six core interfaces get
   a child issue and their own spec.
3. **`CHAT-eoqkbqve` lands first**, on its own commit set.

## Why this boundary

The owner decided on 2026-09-27 that enforcement must run at an intercepted
boundary. An annotated route and a separately proxied service both qualify. A
call inside one object does not.

The controller is the route. Spring resolves the controller bean from the
context, and the proxy is what the dispatcher holds. So a route call crosses
the proxy, and the annotation fires.

**A port to a separately proxied service was considered and rejected.** It
needs one port per interface in `chat-core`, or a new
`chat-service-composite` to `chat-security` dependency. The controller already
sits in a module that depends on `chat-security`, so the cheap boundary and the
correct boundary are the same one here.

## The mechanism is measured

**The delegation shape already works in this repository.**
`MethodSecurityIntegrationTests` holds it at the bean level:

```kotlin
@Service
class TestUserService<T>(that: CompositeServiceBeans<T, *>) :
    UserServiceAccess<T>, ChatUserService<T> by that.userService()
```

Measured on 2026-10-01:
`LongMethodSecurityIntegrationTests` runs 4 tests with 0 failures and 0
skipped. Its test `call key service for new key, deny access` asserts
`AccessDeniedException` through
`IKeyServiceAccess<T>, IKeyService<T> by that.keyService()`. The store mock
answers a value, so the method security proxy is the only source of that
denial.

**One thing is not measured.** No test shows that an RSocket `@MessageMapping`
dispatch or a webflux `@RequestMapping` dispatch crosses the bean proxy. The
transport tests of this issue establish that, and they are the first steps of
the plan. **A failure there is a finding about the boundary**, and it returns
this decision to the owner.

## What this pass wires

| Interface | Methods | RSocket route prefix |
|---|---|---|
| `TopicServiceAccess` | 8 | `topic` |
| `UserServiceAccess` | 3 | `user` |
| `MessageServiceAccess` | 3 | `message` |

Fourteen methods. Four `chat-service-controller` classes and the matching
`chat-webflux` controllers.

Each controller gains one supertype and loses nothing:

```kotlin
class TopicServiceController<T, V>(b: CompositeServiceBeans<T, V>) :
    TopicServiceControllerMapping<T, V>,
    TopicServiceAccess<T, V> by b.topicService()
```

`TopicServiceControllerMapping` and `TopicServiceAccess` both extend
`ChatTopicService`. Both declare the same eight methods with the same
signatures. **One delegation clause satisfies both**, because Kotlin resolves
the conflict to the single delegate.

## What this pass does not do

- **The six core interfaces.** `PersistenceAccess` (10), `IndexAccess` (12),
  `PubSubAccess` (12), `IKeyServiceAccess` (2) and `SecretsStoreAccess` (2).
  They sit on store beans that a composite calls internally, and an internal
  call is not an intercepted boundary. They need route coverage built from
  scratch.
- **`CHAT-eoqkbqve`.** It lands before this work. Its expressions are listed in
  the section below.
- **`CHAT-esengqpv`.** A second wildcard row for one target stays refused
  nowhere.
- **`CHAT-ruapxetl`.** The programmatic wrappers discard their refusal.
- **The grants.** This issue changes no grant and no line of `userinit.yml`.

## The expressions this pass depends on

Every expression below is evaluated by the proxy once the route is wired. An
expression that cannot be evaluated refuses every caller, and it does so with
no cause.

`CHAT-eoqkbqve` holds the repair. Two shapes cannot be evaluated today, and
both were measured on 2026-10-01 at `09d4c9a6`:

1. **A raw id cannot bind to the two argument `hasAccessTo`.** The compiled
   signature is `hasAccessTo(Key, String)`. SpEL resolves a method by name and
   argument count, and then by assignability. A `Long` is not a `Key`, so the
   call fails with `EL1004E`. The three argument form erases to
   `(Object, Object, String)`, so a raw pair of ids binds there.
2. **`#req.dest()`, `#req.uid()` and `#req.roomId()` name methods that Kotlin
   never generates.** A Kotlin `data class` property `dest` compiles to
   `getDest()`. The property form `#req.dest` resolves.

The composite surface carries these expressions:

| Route | Expression | Shape |
|---|---|---|
| `topic-add` | `hasAccessToDomain('MessageTopic', 'NEW')` | ok |
| `topic-rem` | `hasAccessTo(#req.component1(), 'REM')` | raw id |
| `topic-list` | `hasAccessToDomain('MessageTopic', 'ALL')` | ok |
| `topic-by-id` | `hasAccessTo(#req.component1(), 'GET')` | raw id |
| `topic-by-name` | `hasAccessTo(#req.component1(), 'GET')` | raw id |
| `topic-join` | `hasAccessTo(#req.uid(), 'JOIN')` | method name |
| `topic-leave` | `hasAccessTo(#req.roomId(), 'JOIN')` | method name |
| `topic-members` | `hasAccessTo(#req.component1(), 'MEMBERS')` | raw id |
| `user-add` | `hasAccessToDomain('User', 'NEW')` | ok |
| `user-by-handle` | `hasAccessToDomain('User', 'FIND')` | ok |
| `user-by-id` | `hasAccessToDomain('User', 'FIND')` | ok |
| `message-listen-topic` | `hasAccessTo(#req.component1(), 'SUBSCRIBE')` | raw id |
| `message-by-id` | `hasAccessTo(#req.component1(), 'GET')` | raw id |
| `message-send` | `hasAccessToId(#req.dest, 'SEND')` | repaired |

**Eight of the fourteen cannot be evaluated today.** The repair belongs to
`CHAT-eoqkbqve`.

## The enabled matrix

The owner decided on 2026-10-01 that the four write operations deny, and that
the room owner owns its room. `docs/ANONYMOUS-AUTHORIZATION.md` carries the
measured rows.

**Every one of those rows becomes live when this issue lands.** That is the
point of the issue, and it is the first time a deployment enforces a grant.

**`chat-shell` creates a room and sends a message with no credential today.**
After this change:
- `addRoom` denies the shell, because no shipped row grants `NEW` on
  `MessageTopic`.
- `send` denies the shell, because the shell owns no room.

Two consumers break, and both are expected. The shell needs an identity, or
the grants need a decision. **That decision is the owner's and it is not in
this scope.**

## Verification

The owner requires transport level tests with denied callers and zero
downstream effects. Passing identity verification does not grant permission.

Each test drives a real route on a real server and asserts:

1. A denied caller receives a refusal.
2. The store, the index and the pub/sub service received no call.

| Test | Transport | What it pins |
|---|---|---|
| `RSocketDeniedCallerTests` | RSocket | A caller with no identity is refused at each wired route, and no downstream bean is touched |
| `RestDeniedCallerTests` | REST | The same, over the webflux routes |
| `RSocketAllowedCallerTests` | RSocket | A caller with the matching grant reaches the route, and the downstream bean is called once |

`MethodSecurityIntegrationTests` already holds the bean level shape. The
transport tests are new, because no test crosses the dispatcher today.

**The zero-downstream assertion is what makes the test meaningful.** A test
that only asserts a refusal cannot tell a refusal from a broken route.

## Risks

1. **The dispatcher may not hold the proxy.** Measured first, as step 1 of the
   plan. If it fails, this boundary is wrong and the decision returns to the
   owner.
2. **The shell breaks.** Expected, and stated above. It is the first live
   effect of the whole authorization line of work.
3. **A route that carries an annotation and no identity refuses.** The
   RSocket server seam calls `anonymous`, so a caller with no credential
   reaches the `Anon` root key rather than no identity. A route with no
   credential therefore answers through the `Anon` grants, and not through an
   absent identity.
4. **`@PostFilter` does not appear on the composite surface.** It appears on
   `PersistenceAccess.byIds`, which this pass does not wire.

## Open, and for the owner

1. **Does the shell get an identity, or do the grants change?** This issue
   makes both `addRoom` and `send` deny for `chat-shell`. The repair is either
   a shell credential or a changed grant set. Neither is in this scope.
2. **Is the core surface one child issue or several?** It is 38 methods across
   6 interfaces, and each needs a route that may not exist yet.
