# Composite access enforcement

Issue `CHAT-znprrzhn`. It has no parent.

**Status: approved for planning.** The owner approved this specification on
2026-10-01 and recorded two decisions. Nothing in this document is implemented.

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

Two consumers break, and both are expected. **The owner decided on 2026-10-01
that the shell receives an authenticated identity, and that the grants do not
widen.** See decision 1 of the second session below.

**That repair is its own issue and it is not in this scope.** It lands before
this issue, because this issue turns the checks on. See decision 1 of the
second session below.

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

## The decisions of the owner, 2026-10-01 (second session)

The owner approved this specification for planning and took two decisions.

### 1. `chat-shell` receives an authenticated identity

**The grants do not widen.** A shell credential keeps `addRoom` and `send`
aligned with the approved policy. Widening a grant to preserve an
unauthenticated client would move the policy to fit the client, and the policy
is the thing under decision.

**The work sits outside this issue.** It owns the shell credential, the shell
login, and the shell identity. It is a separate issue with its own acceptance
test.

**That issue lands before this one**, because this issue is what makes the
checks live. So `CHAT-znprrzhn` depends on it, and on `CHAT-eoqkbqve`. Its
other four dependencies are already done.

**The three core children are children, and not dependencies.** They do not
block this issue. Nothing about the composite boundary depends on a core
boundary.

**The shell tests are the acceptance gate for that child.** They fail when the
check runs and no credential is present. So the child passes exactly when
`--ci` is green with the checks wired.

### 2. The core surface splits into three children

The six core interfaces become three children, one per access boundary.
`CHAT-ikjisqqd` is deleted, and its three replacements carry the work.

| Child | Interfaces | Methods |
|---|---|---|
| Persistence and index access | `PersistenceAccess`, `IndexAccess` | 22 |
| Pub/sub and topic inventory access | `PubSubAccess` (which carries `TopicInventoryAccess`) | 12 |
| Key and secret access | `IKeyServiceAccess`, `SecretsStoreAccess` | 4 |

**Each boundary gets its own child because each has a different route, a
different downstream effect, and a different acceptance test.** One large child
would weaken the review scope of every one of them.

### 3. The transport tests are the boundary gate

**The composite plan keeps its transport tests as the boundary gate.** If an
RSocket dispatch or a REST dispatch does not cross the proxy, the work stops
and the design returns to the owner. **A failure there is a finding about the
boundary, and not a cue to widen the tests until they pass.**

### 4. `topic-by-name` carries no check, and the owner reversed this decision

**This decision replaced an earlier one on 2026-10-02.** The earlier text read
"`topic-by-name` stays fail-closed". It is kept below, because a reader needs
to see what changed.

**`getRoomByName` cannot carry an honest check.** `ByStringRequest` holds
`name: String` and no id, so there is no target key at the point of the check.

**The earlier decision answered that with an explicit deny.** It refused every
caller, and no grant reached the route.

**The owner reversed that on 2026-10-02.** The route answers every caller that
matches a room. **The answer is minimal.** `MessageTopic` carries the room key
and the full room name, and nothing else. So the route exposes no room content.

**The access condition travels with the next operation.** Every operation on a
resolved room holds its own check. So a caller that reads a name still cannot
send, join, or read the room without a grant.

**The owner named an alternative and deferred it.** An after-fetch
authorization would filter after the fetch, for a target whose key was unknown
at the fetch. The owner ranks that work below the first production release. No
issue tracks it.

Four shell call sites use the route: `TopicCommands.kt` lines 55, 62, 77 and
99, and `PubSubCommands.kt` line 46, which is send by topic name. **The earlier
deny refused all four.** They reach the delegate now.

**The route still answers `NotFoundException` for an unknown name.** A miss is
not a grant question, and `CHAT-dgjhljbl` predicted that reading.

### 5. Every test names the caller state precisely

Three caller states exist, and a test must name the one it drives. A test that
does not is a test that cannot be read.

- **RSocket with no credential reaches the `Anon` identity.** The RSocket
  server seam calls `anonymous`, so the identity is the `Anon` root key.
- **No security context reaches no identity.** A direct call with no
  `contextWrite` reaches the empty identity, and the policy denies it.
- **A denied authenticated caller reaches its user identity and lacks the
  grant.** The identity resolves, and the grant read answers no.

**These are three different outcomes and one refusal.** A test that asserts
only "denied" cannot tell them apart, and `CHAT-zhjltbky` measured that the
distinction is where the defects live.

**The specification was approved with decisions 1, 2, 3 and 5, and decision 4
was taken on 2026-10-01 after the plan review.** The plan states decision 4 as
Task 5. **The owner reversed decision 4 on 2026-10-02, so Task 5 of the plan is
superseded.** The plan keeps its Task 5 text as the record of that session.
