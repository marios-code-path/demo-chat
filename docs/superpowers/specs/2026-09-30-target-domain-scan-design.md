# The target domain scan

Issue `CHAT-rfzsnbco`. This is the target side of root expansion.
`CHAT-mahevldm` carries the principal side.

The document states one change to the authorization core. It measures what
that change does to the grants that `userinit.yml` already ships.

Read `docs/ANONYMOUS-AUTHORIZATION.md` first. It measures the state before
this change.

## The defect

`CoreAuthorizationService.getAuthorizationsAgainst` reads one target.

    authIndex.findBy(queryForTarget.apply(uidB)).flatMap(authPersist::get)

So a check on one object collects the rows whose target is that object alone.
A row that names a domain root as its target never reaches a check on an
object of that domain.

`userinit.yml` ships five such rows. The table below names each one and the
checks it fails to reach.

| Row | The checks it should reach |
|---|---|
| `{Anon, Message, GET}` | `messageById`, and a message read through `PersistenceAccess` |
| `{User, Message, SEND}` | the message send check of `PubSubAccess` |
| `{User, MessageTopic, GET}` | `getRoom`, `getRoomByName` |
| `{User, MessageTopic, JOIN}` | `leaveRoom` |
| `{User, MessageTopic, MEMBERS}` | `roomMembers` |

`{User, MessageTopic, ALL}` already reaches `listRooms`, because that check
names the domain root as its target.

Two consequences follow.

1. **The five `user: User` rows of `userinit.yml` reach one operation each at
   most.** The matrix records the measured answer. **An earlier version of
   `docs/ANONYMOUS-AUTHORIZATION.md` said four.** The file ships five.
2. **The administrator invariant cannot reach a closed room.** The owner named
   that row on 2026-09-24 as the reason an expired wildcard is safe. A closed
   object stays beyond administration while the scan reads one target.

### The grant set is not settled in the working tree

**A working tree edit removes one row, and the reference set must be stated.**

The committed file at HEAD ships nine roles. The working tree removes
`{ user: Anon, target: User, role: PUT }`, so the file there ships eight. That
edit belongs to another line of work, and this issue leaves it alone.

The measurement in this document uses the **committed** set of nine rows. That
is the set `AnonymousAuthorizationMatrixTests.shippedGrants()` mirrors, and it
is the set `docs/ANONYMOUS-AUTHORIZATION.md` measures.

**The mirror is stale against the working tree.** The test hardcodes the `PUT`
row at `AnonymousAuthorizationMatrixTests.kt:320`, and the file no longer
carries it. So the test states nine rows while the working file holds eight.

Two rows of the wider-effect table depend on the `PUT` row, and they are marked
below. Under the working tree set they do not move. **The plan records this on
the issue and does not resolve it**, because the file belongs to other work.

## The change

**A permission check reads two targets: the given one, and the domain root of
the given one.**

A key already carries its root. `Key.root` holds the id of the domain root of
the key. A root key is its own root, so `Key.root(id)` builds it with no
registry read and no lookup.

    private fun targets(uidB: Key<T>): List<Key<T>> {
        val root = Key.root(uidB.root)
        return if (root == uidB) listOf(uidB) else listOf(uidB, root)
    }

The distinct list matters. Five checks already name a domain root as their
target. At those checks the given target equals its own root, so the list holds
one entry and the scan does not read the same target twice.

`getAuthorizationsAgainst` becomes

    summarizer.computeAggregates(
        Flux.concat(targets(uidB).map { authIndex.findBy(queryForTarget.apply(it)).flatMap(authPersist::get) }),
        actors(uidA),
        permission
    )

`getAuthorizationsAgainstMany` takes the same list per target, so the two paths
agree. It has no production caller today, and it is a permission check, so it
follows the same rule.

### Which reads widen, and which do not

| Read | Widens | Why |
|---|---|---|
| `getAuthorizationsAgainst` | yes | The permission check path. One production caller: `AuthMetadataAccessBroker.hasAccessByKey`. |
| `getAuthorizationsAgainstMany` | yes | The same question for a list of targets. |
| `getAuthorizationsForTarget` | **no** | Owner selection. See the next section. |
| `getAuthorizationsForMultipleTarget` | **no** | Owner selection, private, and it has no caller. |

## The exact target owner rule

**This is the load bearing rule of this design, and it is the owner's.**

The owner decided on 2026-09-24 that one caller holds `*` per target.
The policy expression `dest{ROLE=*}` identifies that caller. `ROLE` there names
the `permission` field of a stored row, and the expression is the draft
notation. `AuthMetadata` names that field `permission`, and `permission` is the
term this document uses everywhere else. The rule holds only while the
selection reads the exact target.

So the owner selection read stays one target. `getAuthorizationsForTarget`
does not change.

The reason is concrete. A close writes an expired wildcard row on a domain
root principal. A grant on the domain root also carries a wildcard row while
the operator writes one. If the selection scan read the domain root, the room
would answer two holders of `*`, and the single owner decision would not hold.

**A wildcard row on a domain root is the hazard.** No shipped row is one. The
policy draft does not propose one. The spec records it as a limit, because a
row `{principal: Any, target: <Domain>, permission: '*'}` would own every
object of that domain through level 1 of the rank.

## What this moves in the shipped matrix

Measured against the grants of `userinit.yml`, on the map store.

**One row of the six moves. `messageById` reads allow.**

| Operation | Before | After |
|---|---|---|
| `addRoom`, MessageTopic NEW | deny | deny |
| `send`, room SEND | deny | deny |
| `whoami`, User FIND | allow | allow |
| `messageById`, GET | **deny** | **allow** |
| `listRooms`, MessageTopic ALL | allow | allow |
| `addUser`, User NEW | deny | deny |

Why each row stays or moves.

- `messageById` checks one message key with `GET`. The domain root of a message
  key is the `Message` root. The row `{Anon, Message, GET}` names that root, and
  the `Anon` key is always in the actor set. So the row reaches every caller.
- `send` checks the destination room with `SEND`. The domain root of a room key
  is the `MessageTopic` root. No shipped row grants `SEND` on that root. The
  `{User, Message, SEND}` row names the `Message` root, which is a different
  domain. So `send` stays denied.
- `addRoom` and `addUser` stay denied. Both check a domain root already, and no
  shipped row grants `NEW` on either domain.
- `whoami` and `listRooms` already allowed. Both check a domain root.

### The wider effect, which the six rows do not show

The six rows cover six operations. The annotated interfaces carry more. Every
check below names one object as its target, so the scan reaches it. The last
column names the shipped row that then matches.

Sources are the files of the `@PreAuthorize` expressions. `pass` is the
permission string that the expression asks.

| Check | Source | `pass` | Deploy | The row that reaches it |
|---|---|---|---|---|
| `deleteRoom` | `access/composite/TopicServiceAccess.kt:14` | REM | latent | none. No shipped row grants REM |
| `getRoom` | `access/composite/TopicServiceAccess.kt:20` | GET | latent | `{User, MessageTopic, GET}` |
| `getRoomByName` | `access/composite/TopicServiceAccess.kt:23` | GET | latent | `{User, MessageTopic, GET}` |
| `joinRoom` | `access/composite/TopicServiceAccess.kt:26` | JOIN | latent | none. The target is a user key, and no shipped row grants JOIN on the `User` root |
| `leaveRoom` | `access/composite/TopicServiceAccess.kt:29` | JOIN | latent | `{User, MessageTopic, JOIN}` |
| `roomMembers` | `access/composite/TopicServiceAccess.kt:32` | MEMBERS | latent | `{User, MessageTopic, MEMBERS}` |
| `listenTopic` | `access/composite/MessageServiceAccess.kt:14` | SUBSCRIBE | latent | none. No shipped row grants SUBSCRIBE |
| `messageById` | `access/composite/MessageServiceAccess.kt:17` | GET | latent | `{Anon, Message, GET}` |
| `send` | `access/composite/MessageServiceAccess.kt:20` | SEND | latent | none. The `Message` root row does not cover a room |
| `PersistenceAccess.add` | `access/core/PersistenceAccess.kt:20` | PUT | latent | `{Anon, User, PUT}`, for a `User` entity alone. **Conditional, below** |
| `PersistenceAccess.rem` | `access/core/PersistenceAccess.kt:22` | DEL | latent | none. No shipped row grants DEL |
| `PersistenceAccess.get` | `access/core/PersistenceAccess.kt:24` | GET | latent | `{Anon, Message, GET}` for a `Message` entity, and `{User, MessageTopic, GET}` for a `MessageTopic` entity |
| `PersistenceAccess.byIds` | `access/core/PersistenceAccess.kt:39` | GET | latent | the same two rows as `get` |
| `IndexAccess.add` | `access/core/IndexAccess.kt:12` | PUT | latent | `{Anon, User, PUT}`, for a `User` entity alone. **Conditional, below** |
| `IndexAccess.rem` | `access/core/IndexAccess.kt:15` | REM | latent | none. No shipped row grants REM |
| `PubSubAccess.sendMessage` | `access/core/PubSubAccess.kt:24` | SEND | latent | `{User, Message, SEND}` |
| `PubSubAccess.subscribe` | `access/core/PubSubAccess.kt:12` | SUBSCRIBE | latent | none |
| `PubSubAccess.unSubscribe` | `access/core/PubSubAccess.kt:15` | SUBSCRIBE | latent | none |
| `PubSubAccess.unSubscribeAll` | `access/core/PubSubAccess.kt:18` | UNSUBALL | latent | none |
| `PubSubAccess.unSubscribeAllIn` | `access/core/PubSubAccess.kt:21` | UNSUBALLIN | latent | none |
| `PubSubAccess.listenTo` | `access/core/PubSubAccess.kt:27` | SUBSCRIBE | latent | none |
| `PubSubAccess.exists` | `access/core/PubSubAccess.kt:31` | SUBSCRIBE | latent | none |
| `TopicInventoryAccess.open` | `access/core/PubSubAccess.kt:37` | OPEN | latent | none |
| `TopicInventoryAccess.close` | `access/core/PubSubAccess.kt:40` | CLOSE | latent | none |
| `TopicInventoryAccess.getByUser` | `access/core/PubSubAccess.kt:43` | TOPICS | latent | none. The target is a user key |
| `TopicInventoryAccess.getUsersBy` | `access/core/PubSubAccess.kt:46` | GET | latent | `{User, MessageTopic, GET}` |
| `IKeyServiceAccess.rem` | `access/core/IKeyServiceAccess.kt:15` | DEL | latent | none |

**Eleven checks move under the committed set of nine rows.** They are
`getRoom`, `getRoomByName`, `leaveRoom`, `roomMembers`, `messageById`,
`PersistenceAccess.add`, `PersistenceAccess.get`, `PersistenceAccess.byIds`,
`IndexAccess.add`, `PubSubAccess.sendMessage` and
`TopicInventoryAccess.getUsersBy`. The rest of the table holds the answer it
had.

**Two of the eleven are conditional.** `PersistenceAccess.add` and
`IndexAccess.add` move through the `{Anon, User, PUT}` row alone. Under the
working tree set that row is absent, so **nine checks move there** and those
two do not. The other nine depend on no removed row.

**Every moved row reaches every caller.** Each names `User` or `Anon` as its
principal. The `User` root and the `Anon` key are both in the actor set of
every query.

**Every row above is latent.** No production bean implements an annotated
interface. Implementations exist in `chat-security/src/test` alone:
`integration/LongByIdsFilterTests.kt:152` and
`integration/MethodSecurityIntegrationTests.kt:228` and `:231`.

The deployed seam does run. `MethodSecurityConfiguration` enables reactive
method security, and `chat-build` passes `app.service.composite.auth` on every
core launch. **An enabled proxy over a bean that implements no annotated
interface fires nothing.** The second seam, the programmatic wrappers, needs
`app.service.composite.security`. `chat-deploy-cassandra` names that property
at `CompositeServiceConfiguration.kt:22`, and no launch script, no yml and no
test sets it. `CHAT-znprrzhn` holds the wiring.

So no row above is deployed, and this change moves what the configuration
means. It moves no running answer.

**The widening is the point of the issue.** The grants themselves are the next
decision. `CHAT-zhjltbky` holds it. The order of the chain is deliberate: this
issue widens, the next one decides, and `CHAT-znprrzhn` enables the checks
last.

## The administrator invariant

The rule the owner named on 2026-09-24 is that an administrator acts on a
closed target. Without this scan the rule cannot hold, because the close sits
on the object and the administrator row sits on something else.

The scan closes it. A row `{Admin, <Domain>, '*', never}` now reaches every
object of that domain.

**The caller must hold the `Admin` key.** The actor set is the `Anon` key, the
`User` root and the caller alone. A row passes the filter only when its
principal is in that set. The `Admin` row names the `Admin` key as its
principal, and the `Admin` key is an object of the `User` domain. It is neither
the `User` root nor the `Anon` key.

So an anonymous caller does not satisfy the row, and an ordinary authenticated
caller does not either. The test must build its security context from a
`ChatUserDetails` whose user key is the `Admin` key. The `Admin` key is an
object principal, so `PrincipalRank` reads it as `ENTITY`.

The rank then delivers the result.

- Level 1 places both rows at the wildcard. The close is a wildcard row, and so
  is the administrator row.
- Level 2 decides. The administrator principal is `ENTITY`, because `RootKeys`
  holds the two identities apart from the domain roots. The close principal is
  a domain root, which is `DOMAIN_ROOT`. `ENTITY` outranks `DOMAIN_ROOT`.
- So the administrator row is last, it has not expired, and the administrator
  holds the target.

## Tests

The tests live in `chat-security`, beside `AnonymousAuthorizationMatrixTests`.
The store and the index stay maps, and the production classes stay real.

### The fixture, which must change first

`AnonymousAuthorizationMatrixTests` builds its keys through `TestKeys.key(id)`.
That factory gives every key the fixed root `TestRoots.LONG`. So every key in
that test has the same root, and no key carries the root of its own domain.

**The scan cannot be measured through that fixture.** A room key must carry the
`MessageTopic` root, or the domain root of a room is not the `MessageTopic`
root key.

The fixture must build keys the way production builds them.

- A domain root is `Key.root(id)`, so it is its own root.
- An object key is `Key.of(id, domainRootId)`.
- An identity is a user, so `Key.of(id, userRootId)`.

`RootKeySnapshot.load` builds the same three shapes, so the fixture agrees with
the production path.

The registry must answer the same root for each id. `TestVerifiers.holding`
takes the root from the key it is given, so the fixture change carries into it.

### The tests

1. **`messageById` allows, and it denied before.** The one moved row of the six.
2. **`send` still denies.** The scan must not widen across domains. The
   `Message` root row does not cover a room.
3. **A room read allows through a domain root row.** `getRoom` with one room
   object and one `{User, MessageTopic, GET}` row.
4. **A domain root check reads one target.** A counting index records each
   target it is asked for. A check whose target is its own root must ask for
   that key alone. **The assertion reads the recorded target keys, and not
   their count.** A count of one would also hold for two lookups of one key,
   or for a lookup of the wrong key.
5. **Owner selection reads the exact target.** A wildcard row on the domain
   root does not enter the selection for one room. The answer holds one key.
6. **An administrator acts on a closed target.** A `{Admin, <Domain>, '*'}`
   row beats a close on one object of that domain. **The context carries the
   `Admin` key as its caller.** An anonymous context must fail this test, and
   a second case proves it does.
7. **A close still beats a domain root grant.** An expired wildcard row on a
   domain root principal removes a live named row on the domain root target.
8. **The exact target is still read.** A row on one object reaches its check
   after the change. The scan adds a target and removes none.
9. **A many target request mixes one object and one domain root.** The request
   holds a room and the `MessageTopic` root. The permitted answer carries both,
   and each one is evaluated through its own single target check.
10. **The counting index answers by target key.** The test states the expected
    keys, so a lookup of the wrong key fails.

`CassandraAuthorizationMatrixTests` takes the same fixture change and the
first three cases. It runs the production stack against a real store.

### The stale comments

`AnonymousAuthorizationMatrixTests` carries comments that are stale since
`CHAT-mahevldm`. The plan updates them in the same task as the fixture.

- The class comment of `an authenticated caller reaches the same answers` says
  the four `user: User` rows reach nobody. **The file ships five, and they
  reach every caller.**
- The class comment of `an anonymous caller may find a user and nothing else`
  says the `Message:GET` grant never applies. **This change makes it apply.**
- `shippedGrants()` mirrors the committed file. It stays as it is, and the
  plan records the working tree difference on the issue.
- `docs/ANONYMOUS-AUTHORIZATION.md` says the three composite interfaces carry
  every `@PreAuthorize` in the repository. The `core` package carries more.

## Limits

1. **A wildcard row on a domain root owns the whole domain.** Level 1 of the
   rank places a wildcard above every named row. With the scan, such a row
   reaches every object of its domain. No shipped row is one. Do not write one
   without deciding its meaning first.
2. **The scan reads one index query more per check.** Both backends answer
   `findBy` by target key, and a root key is an ordinary key. So the cost is one
   query, and it is not a scan of the store.
3. **The scan does not verify the root.** It trusts `uidB.root`, which the
   boundary verified. `KeyVerifier` verifies every target before the broker
   reads it. A forged root can match no stored row, because a grant write
   verifies both parties too.
4. **The scan does not read the domain of the principal.** The principal side
   is already closed. `ContextIdentity` answers the key of a user or the `Anon`
   key, and both carry the `USER` root.
5. **The rank does not change.** This spec adds rows to a group. It does not
   change level 1, level 2 or level 3.
6. **A named grant on one object beats a domain root grant.** The reverse does
   not hold. Level 2 places an `ENTITY` principal above a `DOMAIN_ROOT`
   principal, so a row that names one caller on one object is last and it
   decides.

## What this does not do

- It does not change the grants. `CHAT-zhjltbky` decides them, and it runs
  after this.
- It does not enable the checks. `CHAT-znprrzhn` holds the wiring, and it runs
  last.
- It does not add the dedicated agent identity. `CHAT-werokcbb` waits on all
  three.
- It does not add a subtractive row, a precedence value, or a close mechanism.
  Those stay in the policy draft.

## The documents this work updates

- `docs/ANONYMOUS-AUTHORIZATION.md` takes the measured matrix. The
  `messageById` row moves from deny to allow. The section `The grants` states
  which shipped rows now reach an operation. The section `What is not wired`
  takes the measured count of the annotated interfaces.
- `docs/superpowers/specs/2026-09-23-operation-policy-draft.md` states, under
  `The scan must read two targets`, that four reads need the domain lookup.
  This design widens two of them and leaves two exact. The plan records that
  correction on the draft, because the owner rule is the reason.

## The working tree

**This work does not touch the unrelated changes in the working tree.** Two
files carry uncommitted edits on `master`: `userinit.yml` and
`CompositeControllersConfiguration.kt`. They belong to another line of work.
The branch of this issue leaves them alone, and it stages no part of them.
