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

`userinit.yml` ships four such rows. The table below names each one and the
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

1. **The four `user: User` rows of `userinit.yml` reach one operation each at
   most.** The matrix records the measured answer.
2. **The administrator invariant cannot reach a closed room.** The owner named
   that row on 2026-09-24 as the reason an expired wildcard is safe. A closed
   object stays beyond administration while the scan reads one target.

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
`dest{ROLE=*}` identifies that caller. The rule holds only while the selection
reads the exact target.

So the owner selection read stays one target. `getAuthorizationsForTarget`
does not change.

The reason is concrete. A close writes an expired wildcard row on a domain
root principal. A grant on the domain root also carries a wildcard row while
the operator writes one. If the selection scan read the domain root, the room
would answer two holders of `*`, and the single owner decision would not hold.

**A wildcard row on a domain root is the hazard.** No shipped row is one. The
policy draft does not propose one. The spec records it as a limit, because
`{principal: Any, target: <Domain>, role: '*'}` would own every object of that
domain through level 1 of the rank.

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

The six rows cover six operations. The annotated interfaces carry more. Each
check below names one object as its target, so each one moves.

| Check | Permission | The row that now reaches it |
|---|---|---|
| `getRoom`, `getRoomByName` | GET on a room | `{User, MessageTopic, GET}` |
| `leaveRoom` | JOIN on a room | `{User, MessageTopic, JOIN}` |
| `roomMembers` | MEMBERS on a room | `{User, MessageTopic, MEMBERS}` |
| `PubSubAccess` message send | SEND on a message | `{User, Message, SEND}` |
| `PubSubAccess` topic get | GET on a room | `{User, MessageTopic, GET}` |
| `PersistenceAccess` get, byIds | GET on a message | `{Anon, Message, GET}` |
| `PersistenceAccess` get, byIds | GET on a room | `{User, MessageTopic, GET}` |
| `PersistenceAccess` put | PUT on a user | `{Anon, User, PUT}` |

**Every row above reaches every caller.** Each names `User` or `Anon` as its
principal. The `User` root and the `Anon` key are both in the actor set of
every query.

**This widens the shipped configuration, and the widening is the point of the
issue.** The grants themselves are the next decision. `CHAT-zhjltbky` holds it.
The order of the chain is deliberate: this issue widens, the next one decides,
and `CHAT-znprrzhn` enables the checks last.

**No deployed composition behaves differently today.** No production bean
implements the annotated interfaces. `CHAT-znprrzhn` holds that gap. So this
change moves what the configuration means, and it moves no running answer.

## The administrator invariant

The rule the owner named on 2026-09-24 is that an administrator acts on a
closed target. Without this scan the rule cannot hold, because the close sits
on the object and the administrator row sits on something else.

The scan closes it. A row `{ADMIN, <Domain>, '*', never}` now reaches every
object of that domain.

The rank delivers the result.

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
4. **The five domain root checks do not read the target twice.** A counting
   index proves one lookup per check when the target is its own root.
5. **Owner selection reads the exact target.** A wildcard row on the domain
   root does not enter the selection for one room. The answer holds one key.
6. **An administrator acts on a closed target.** A `{ADMIN, <Domain>, '*'}`
   row beats a close on one object of that domain.
7. **A close still beats a domain root grant.** An expired wildcard row on a
   domain root principal removes a live named row on the domain root target.
8. **The exact target is still read.** A row on one object reaches its check
   after the change. The scan adds a target and removes none.

The cassandra matrix test takes the same fixture change and the same first
three cases. It runs the production stack against a real store.

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
