# The authorization grant policy

Issue `CHAT-zhjltbky`, under the parent `CHAT-ltvfmcvh`.

**Status: draft, and it waits for the owner review.** Nothing in this document
is implemented.

Measured on 2026-10-01 at master `09d4c9a6`.

## The stipulation

The owner took six decisions on 2026-10-01. Each one answers a question that
`CHAT-zhjltbky` recorded, and each one was measured first.

1. **The `Anon` floor stays.** The actor set holds the `Anon` key and the
   `User` root. Every grant that names either key reaches every caller.
2. **Four operations deny an anonymous caller.** They are `addRoom`, `send`,
   `addUser` and `deleteRoom`.
3. **The room owner grant is written server-side at room creation.** The
   client stops writing it.
4. **This issue delivers the configuration policy and its tests.** The typed
   schema stays with `CHAT-zcxgrtqc`.
5. **A room owner may delete a room.** The rank already answers this. See the
   delete decision below.
6. **This issue edits no line of `userinit.yml`.** The file carries an
   uncommitted change from another line of work. It also carries two inert
   keys. Both belong to `CHAT-zcxgrtqc`.

## The measured state

The three changes named on 2026-09-24 are done. So is the target scan.

| Change | State |
|---|---|
| `*` expands to the asked permission | done, `CHAT-rgdcyxlv` |
| The rank reads principal specificity | done, `CHAT-lbhmzccn` |
| The actor set holds the `User` root | done, `CHAT-mahevldm` |
| A check reads the target and its domain root | done, `CHAT-rfzsnbco` |

The shipped grants reach an operation now. `docs/ANONYMOUS-AUTHORIZATION.md`
carries the matrix.

| Operation | anonymous | authenticated |
|---|---|---|
| `addRoom`, MessageTopic NEW | deny | deny |
| `send`, room SEND | deny | deny |
| `whoami`, User FIND | allow | allow |
| `messageById`, Message GET | allow | allow |
| `listRooms`, MessageTopic ALL | allow | allow |
| `addUser`, User NEW | deny | deny |

**Decision 2 changes no grant.** Every one of the four operations denies
today. The policy states that this is the intent, and not an accident.

## What the configuration can express

`RoleDefinition` binds three fields. They are `user`, `target` and `role`.
`InitialUsersService` resolves both names through `RootKeys.byName`.

So one row can name a **root key only**. It cannot carry an expiry. It cannot
name an object.

**The consequence is decisive.** "The creator of a room owns it" cannot be
written in `userinit.yml`. It needs a per-object grant at room creation.

Three keys read as grants and hold no meaning. Measured at `09d4c9a6`:

- `rolesAllowed` binds and nothing reads it.
- `wildcard` binds and nothing reads it.
- `role: '-'` has no denial meaning. Nothing subtracts.

Blocker 1 named the binding risk. `InitalRoles` declares `rolesAllowed` and
`wildcard` as required, so removing either key from the file fails the
binding. Decision 6 answers it. **This issue removes no key.** The removal and
its binding measurement move to `CHAT-zcxgrtqc`, which owns the shape of that
file. The cost is one more issue in which the file reads as policy it does not
carry. The alternative costs a Kotlin-default measurement under
`@ConstructorBinding`, and that measurement is not about grants.

## The delete decision

Blocker 2 asked whether a room owner may delete a room. **The answer is yes,
and no new code carries it.** The rank of `CHAT-lbhmzccn` already answers, and
its KDoc states the intent.

`TopicServiceAccess.deleteRoom` checks the room key with `REM`. Four rows
decide, and each reads the rank in `AuthSummarizer`:

| Caller | Room | Answer | Why |
|---|---|---|---|
| anonymous | open | deny | No shipped row grants `REM`. `ALL` is a literal, so it does not cover `REM` |
| authenticated, not the owner | open | deny | Same. The caller holds no row on this room |
| the owner | open | **allow** | The `*` row names the asked permission |
| the owner | closed | **allow** | Both rows are wildcards, so level 1 ties. The owner names an `ENTITY` principal, and the close names a `DOMAIN_ROOT` principal |
| not the owner | closed | deny | The close wins its group, and its expiry then drops it |

**Level 2 is what keeps the owner above the close.** `PrincipalRank` reads a
domain root as `DOMAIN_ROOT` and every other principal as `ENTITY`. The owner
row names the user key, so it is an `ENTITY`. The close names a domain root.
So an `ENTITY` wildcard outranks a `DOMAIN_ROOT` wildcard, and the close does
not reach the owner.

**This is the owner-authored intent.** The `PrincipalRank` KDoc states that
level 2 exists so that the owner and the administrator survive a close. A rule
that denied the owner would contradict it, and it would need a special case.
The cost if this is wrong: an owner can remove a room and its grants. Nothing
enforces the check in a deployment yet, so the effect is latent until
`CHAT-znprrzhn`.

## The owner grant port

Blocker 3 asked for the exact seam and the failure behaviour. This section
answers both.

### The port

The port lives in `chat-core`, beside `AuthorizationService` and `AccessBroker`
in `com.demo.chat.service.security`. Both `chat-service-composite` and
`chat-security` depend on `chat-core`, and neither depends on the other.

```kotlin
interface RoomOwnerGrant<T> {
    fun grantOwner(roomKey: Key<T>): Mono<Void>
}
```

### The implementation

`ContextRoomOwnerGrant` lives in `chat-security`, at
`com.demo.chat.security.service`. `ContextIdentity` is the one live reader of
the security context, and `chat-security` is its home.

```kotlin
class ContextRoomOwnerGrant<T>(
    private val identity: ContextIdentity<T>,
    private val authorizationService: AuthorizationService<T, AuthMetadata<T>>,
    private val rootKeys: RootKeys<T>,
    private val typeUtil: TypeUtil<T>,
) : RoomOwnerGrant<T> {

    override fun grantOwner(roomKey: Key<T>): Mono<Void> =
        identity.identity()
            .flatMap { owner ->
                authorizationService.authorize(
                    AuthMetadata.create(
                        Key.empty(typeUtil.empty(), rootKeys.of(ChatDomain.AUTH_METADATA).id),
                        owner,
                        roomKey,
                        AuthSummarizer.WILDCARD,
                        false,
                        0L,
                    ),
                    true,
                )
            }
            .then()
}
```

Four properties of this body, each of which is a decision.

1. **The empty identity answers an empty `Mono`.** `flatMap` never runs, and
   `then()` completes. So no identity writes no grant, and it raises no error.
   That is decision 5 of the register, and it holds today.
2. **`expires` is `0L`.** The summarizer keeps a row when `expires` is `0L`.
   The shell wrote `Long.MAX_VALUE`. A never-expiring grant needs no sentinel.
3. **The permission is `AuthSummarizer.WILDCARD`.** One writer and one
   spelling, so the constant is read and not retyped.
4. **`exist` is `true`.** The key is `Key.empty`, so `CoreAuthorizationService`
   mints the grant key and verifies both parties first. That is the same call
   the shell makes today.

### The bean

`chat-security` declares one configuration. Its condition is
`app.service.composite.auth`, and not `app.service.composite`. The writer
depends on `authorizationService`, and `AuthBeansConfiguration` registers that
bean under the same condition. So the writer exists exactly where the service
it needs exists.

```kotlin
@Configuration
@ConditionalOnProperty(prefix = "app.service.composite", name = ["auth"])
open class RoomOwnerGrantConfiguration<T>(
    private val authorizationService: AuthorizationService<T, AuthMetadata<T>>,
    private val rootKeys: RootKeys<T>,
    private val typeUtil: TypeUtil<T>,
) {
    @Bean
    open fun roomOwnerGrant(): RoomOwnerGrant<T> =
        ContextRoomOwnerGrant(ContextIdentity(rootKeys), authorizationService, rootKeys, typeUtil)
}
```

### The wiring

`CompositeServiceBeansConfiguration` already takes `vectorIndexers` as an
`ObjectProvider`. The port follows that pattern, so a composition without
`chat-security` still builds a topic service.

```kotlin
private val roomOwnerGrants: ObjectProvider<RoomOwnerGrant<T>>,
```

```kotlin
topicService = TopicServiceImpl(
    ...
    roomOwnerGrant = roomOwnerGrants.ifAvailable,
)
```

`TopicServiceImpl` takes one parameter, `private val roomOwnerGrant:
RoomOwnerGrant<T>? = null`. The default keeps every existing constructor call
compiling, including the test ones.

### The chain

The grant write is the last step of `addRoom`.

```kotlin
.flatMap { room ->
    topicPersistence.add(room)
        .then(topicIndex.add(room))
        .then(pubsub.open(room.key.id))
        .then(grantOwner(room.key))
        .then(Mono.just(room.key))
}
```

```kotlin
private fun grantOwner(roomKey: Key<T>): Mono<Void> =
    roomOwnerGrant?.grantOwner(roomKey) ?: Mono.empty()
```

**An absent port writes no grant and raises no error.** The port is absent
exactly when the composition carries no authorization, and then no owner check
can run.

### The failure behaviour

**A failed grant write fails the request, and every earlier step stays.** No
step of this chain compensates any other step. A failed index write already
leaves the room in the store, and a failed open already leaves the store and
the index rows. The grant write adds no compensation, because a rollback
written for one step alone would remove a room whose topic another reader may
already hold.

The residual is an ownerless room. The failure message names the room key, so
an operator can write the missing row. Two paths repair it: the
`{User, MessageTopic, ALL}` row reaches the room for a list, and an operator
writes a `*` row for the intended owner.

**The failure takes its own type**, `RoomOwnerGrantException` in `chat-core`
beside the port. It carries the room key as a field and the cause, so a caller
can act on the room and an operator can read the reason. A plain
`ChatException` would carry no room. `ChatException` gains an optional cause
for this, because `Exception` already carries one.

**A failure is reported, and not silent.** A silent skip would hide the loss
of ownership, which is the `RedisDeployBootTests` lesson.

### The no-identity case

The owner asked whether a direct service test covers this case. **It does, and
it is the only test that can.**

A wired route denies `addRoom` before `TopicServiceImpl` runs. So a route test
can never reach the writer with no identity. The direct call is the only
observation of that branch.

It is also the live path today. No deployment wires a check, and `chat-shell`
creates a room with no credential. So the direct test pins behaviour that a
real launch produces now.

## What this issue changes

### 1. `userinit.yml` changes no line

Decision 6. The file keeps its nine committed rows and its two inert keys.
This issue writes no line of it, so it cannot collide with the uncommitted
change the main checkout holds.

The nine rows already state decision 2. `Anon` holds `User: FIND`,
`User: PUT` and `Message: GET`. The `User` root holds the `MessageTopic` and
`Message` rows.

**The pending edit moves no matrix row.** It removes
`{Anon, User, PUT}`. Two of the eleven moved checks read that row, and both
are `core` checks over a `User` entity, `PersistenceAccess.add` and
`IndexAccess.add`. Neither is a row of the matrix.

### 2. The `role: '-'` concept leaves this issue

No code subtracts a permission. The draft holds two spellings of one outcome
for a denial. `docs/superpowers/specs/2026-09-23-operation-policy-draft.md`
records both, and it states that neither has code behind it.

A denial is the absence of a grant. That is the whole rule, and this issue
adds no syntax that states it.

### 3. The room owner grant moves to the server

`TopicCommands.addTopic` writes a grant today, in the shell client. It names
the room key with `*` and it never expires. The server takes that duty, through
the port above.

**Why not `TopicServiceAccess`.** That wrapper is inert, because no deployment
sets `app.service.composite.security`. It also carries the refusal defect of
`CHAT-ruapxetl`. A grant written there would exist only in a composition that
no launch script starts.

### 4. The shell stops writing the grant

`*` is singular per target. Two writers would give one room two owners, and
the rank would then answer by time alone. So one writer remains, and it is the
server.

**The shell loses ownership until it authenticates.** A room that `chat-shell`
creates has no owner. Nothing breaks today, because no deployment wires a
check. `CHAT-znprrzhn` owns that wiring, and it must land after the shell can
present an identity.

### 5. One expression is aligned

`PubSubAccess.sendMessage` checks `#message.key.id` with `SEND`. That is the
message key. `MessageServiceAccess.send` checks `#req.dest()`, which is the
room key.

**The two sites disagree about the target of one operation.** A `*` row on the
room key can never cover the message-key site, because the message key is not
the room key and neither is the root of the other.

The policy says a send is checked against the room. So the expression becomes
`@chatAccess.hasAccessTo(#message.key.dest, 'SEND')`.

## Findings that change the documents

1. **The singular `*` claim is unenforced.** `AuthSummarizer.WILDCARD` states
   that a second `*` on one target is refused at the source. No code refuses
   it. A search for `WILDCARD` in main source reaches two files, and both are
   the summarizer and its rank. **One writer removes the need for a check.**
   The KDoc must say that, and not state an enforcement that does not exist.
2. **`send` has two check sites with two targets.** Recorded above. The wider
   effect table of the target domain scan spec does not list it.
3. **The room owner is a new matrix row.** After this change, the owner of a
   room allows `send` and every other room permission. Anonymous never owns a
   room, because `addRoom` denies it. So the anonymous column does not move,
   and the document needs a row for the owner.

## What this issue does not do

- **The typed `operationPolicy` schema.** `CHAT-zcxgrtqc` holds it. Until it
  lands, no row carries an expiry, and no row names an object.
- **Two inert keys and one denial spelling in `userinit.yml`.**
  `CHAT-zcxgrtqc` owns the file.
- **Instance access checks.** `CHAT-znprrzhn` holds the wiring. It depends on
  this issue, because the checks must not be enabled before the grants are
  aligned.
- **The discarded refusal in the programmatic wrappers.** `CHAT-ruapxetl`.
- **A token that expires.** No deployed transport validates one.

## Evidence plan

Each test names the decision it holds.

| Test | What it pins |
|---|---|
| `RoomOwnerGrantTests` | The writer names the owner, the room key and `*`, and the row never expires |
| `RoomOwnerGrantNoIdentityTests` | No identity writes no grant, and raises no error |
| `TopicServiceImpl` owner grant test | `addRoom` writes exactly one `*` row for the creating caller |
| `TopicServiceImpl` grant failure test | A failed grant write fails `addRoom`, the room stays, and the failure names the room key |
| `TopicServiceImpl` no-port test | An absent port writes no grant, and creates the room |
| A delete decision test | The five rows of the delete table |
| A new expression test | Both send sites check the room, not the message |
| `AnonymousAuthorizationMatrixTests` | The six matrix rows, and a seventh for the room owner |
| `CassandraAuthorizationMatrixTests` | The same rows against a real store and index |
| `UserInitConfigBindingTests` | Unchanged. It holds the shape this issue leaves alone |

**The owner row is the new measurement.** It is the first row that a shipped
composition answers allow for a write, so it carries the most risk.

**The delete rows are the second.** They are latent until `CHAT-znprrzhn`.

## Open, and for the owner

1. **Does a second `*` writer ever exist again?** A future client that writes
   `*` would create a second owner with no error. One writer answers this
   today. A check at the source would answer it in general, and it is not in
   this scope.
2. **The pending edit to `userinit.yml`.** The main checkout holds an
   uncommitted change that removes `{ user: Anon, target: User, role: PUT }`.
   This spec describes the committed nine-row file, and it writes no line of
   it. `CHAT-zcxgrtqc` must land the two inert keys, the pending edit and the
   decision-2 comment together.
