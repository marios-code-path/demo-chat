# The anonymous authorization matrix

What a caller may do, and what the `Anon` root key may do.

**Measured on 2026-09-24 at `58163b7c`, and re-measured on 2026-09-30 for
`CHAT-rfzsnbco`.** The first measurement was taken on 2026-09-23 at master
`4fcae69c`. Two rows have moved since. `listRooms` went from deny to allow
under `CHAT-mahevldm`, and `messageById` went from deny to allow under
`CHAT-rfzsnbco`.

`docs/IDENTITY-POLICY.md` states which identity a caller reaches. **This
document states what that identity may then do.** They are separate questions,
and a green identity test proves nothing here.

**This matrix describes a store that keeps every grant row.** The test
replaces the store and the index with maps.

**The cassandra authorization index now keeps every grant row.** Measured on
2026-09-27, the grant id is a clustering column of both index tables, so one
target and one principal each hold many grants. A removal reads the
`auth_metadata_by_id` row, because `rem` receives the grant key alone.
`CHAT-rmxxtwtu` held both defects.

Two tests carry that. `AuthMetadataIndexRepositoryTests` writes two grants on
one target and on one principal, reads both, removes one and proves the other
stands. `CassandraGrantRestartTests` proves the same shape through the
production `AccessBroker` on the `MESSAGE_TOPIC` root, which four shipped rows
share, and proves both grants survive a context restart.

**The matrix below is now measured on both.** `AnonymousAuthorizationMatrixTests`
replaces the store and the index with maps.
`CassandraAuthorizationMatrixTests` runs the production stack against a
cassandra store and a cassandra index, over the launch surface that loads
`userinit.yml`. Measured on 2026-09-27: **all rows answer the same on cassandra
as on the map store**, for all five caller states. It carries one further row
than the matrix below, a room read, and it was re-measured on 2026-09-30 for
`CHAT-rfzsnbco`.

That test mints the message key instead of sending a message, because
`send` fails on a cassandra deployment. See `CHAT-xcmpudyb`. The
`messageById` expression checks one message key, and no message is sent, so
the minted key measures the same expression.

Only the operations of that test are measured this way. The self authority,
many target and expiry cases in this document are still measured on a map
store alone.

**`*` means ownership, and not "all permissions".** It is singular per target,
and it is a sentinel, so a `*` row stops the read and its expiry decides.
`docs/superpowers/specs/2026-09-23-operation-policy-draft.md` states the three
properties under `What \* means`. **No shipped row names `*` on a target that
an operation below checks**, so the rank does not move this matrix. The one
shipped `*` row names the `Admin` key as its target, and no operation here
names that target.

`AnonymousAuthorizationMatrixTests` holds the measurement. It wires the
production `CoreAuthorizationService`, `AuthSummarizer`,
`AuthMetadataAccessBroker` and `SpringSecurityAccessBrokerService`, and
replaces only the store and the index.

## The grants

Every deployment loads
`shared-deploy-configuration/src/main/config/userinit.yml`. It holds nine
roles. **All nine are listed here, because since `CHAT-mahevldm` the five that
name `User` reach a caller.** An earlier version of this section listed the
three `Anon` rows alone.

| Principal | Target | Permission | Reaches a caller |
|---|---|---|---|
| `Admin` | `Admin` | `*` | the `Admin` key alone, and the self rule already covers it |
| `Anon` | `User` | FIND | yes |
| `Anon` | `User` | PUT | yes |
| `Anon` | `Message` | GET | yes, and it reaches `messageById` since `CHAT-rfzsnbco` |
| `User` | `Message` | SEND | yes, and it reaches no room, because a room is another domain |
| `User` | `MessageTopic` | ALL | yes |
| `User` | `MessageTopic` | GET | yes, and it reaches `getRoom` and `getRoomByName` since `CHAT-rfzsnbco` |
| `User` | `MessageTopic` | JOIN | yes, and it reaches `leaveRoom` since `CHAT-rfzsnbco` |
| `User` | `MessageTopic` | MEMBERS | yes, and it reaches `roomMembers` since `CHAT-rfzsnbco` |

"Reaches a caller" is the principal side, which `CHAT-mahevldm` closed.
"Reaches an operation" is the target side, which `CHAT-rfzsnbco` closed. A
check on one object now reads the domain root of that object beside it.

## The room owner

**The server writes one `*` row for a new room, for the caller that created it.**
`ContextRoomOwnerGrant` is the only writer, and `CHAT-zhjltbky` decided that.

**An anonymous caller owns no room.** The RSocket server seam calls `anonymous`,
so a caller with no credential reaches the `Anon` root key rather than no
identity. `ContextIdentity` rule 4 is what answers it. The writer drops that
key, so the room is created with no owner row.

**A row for the `Anon` key would reach every caller.** The actor set of every
query holds the `Anon` key, so every caller would be an owner of the room. A
close could not reach the row either, because an identity is an `ENTITY` and a
close names a `DOMAIN_ROOT`. Level 2 of the rank keeps the owner row.

**An ownerless room is a state this application accepts.** A caller holds every
right over its own key, so an owner may delete its own room. A room with no
owner row is deleted by an operator, who writes the missing row or removes the
room.

## Self authority

**A key holds every right over itself.** The owner decided this on
2026-09-24, under `CHAT-ixzpkqxg`. `AuthMetadataAccessBroker` answers allow
when the principal equals the target, before it reads a row. No row grants
this and no row removes it, so an expired wildcard or a close does not reach
it. The `Admin` row is redundant for this reason.

In a many target read, the rule permits the caller's own target and no
other. See the next section.

## Many target reads

**A many target read answers the permitted targets alone.** The owner chose
this filter contract on 2026-09-24, under `CHAT-wkwiipgy`.

- Each target is evaluated on its own, through the single target check.
- A denied target is left out, and the request does not fail.
- An empty list, or a list with no permitted target, answers nothing.
- Self authority permits the caller's own target and no other.

`AccessBroker.permittedTargets` answers the subset. `PersistenceAccess.byIds`
carries `@PostFilter`, which evaluates each returned entity through
`hasAccessToEntity`. So a denied entity never reaches the caller. It does reach
the method security proxy, because the store reads every key first.

`EntityTargets` names the target of each entity. A `KeyBearer` names its key.
A `TopicMembership` names its raw id as a key, because its `key` is not a
`Key`. Any other entity names no target, and no target denies.

**The contract this replaced allowed a whole list when one target had a
grant.** The Boolean many target checks read the rows of every target into one
set and asked whether it held the permission. One Boolean cannot carry a
subset, so those checks are removed. No production class implements
`PersistenceAccess`, so nothing ran the old contract in a deployment.
`CHAT-znprrzhn` holds that wiring.

**A target key is never an actor.** The actor set is the `Anon` key, the
`User` root and the caller. Until `CHAT-ixzpkqxg` it also held the target key.
So a row whose principal equals its target passed the actor filter for every
caller that asked about that target. The `Admin` row was that case, and any
caller reached `*` on `Admin`. No operation in this matrix checks that target,
so no row of the matrix moved.

## The matrix

The operations are the `@PreAuthorize` expressions of the access interfaces in
`chat-security`.

| Operation | anonymous | authenticated | unauthenticated | unsupported | no context |
|---|---|---|---|---|---|
| `addRoom`, MessageTopic NEW | deny | deny | deny | deny | deny |
| `send`, room SEND | deny | deny | deny | deny | deny |
| `whoami`, User FIND | **allow** | **allow** | deny | deny | deny |
| `messageById`, GET | **allow** | **allow** | deny | deny | deny |
| `listRooms`, MessageTopic ALL | **allow** | **allow** | deny | deny | deny |
| `addUser`, User NEW | deny | deny | deny | deny | deny |

## Three results that are easy to miss

1. **An anonymous grant is a floor for every caller, and so is a `User` root
   grant.** `CoreAuthorizationService` puts the `Anon` key **and the `User`
   root** in the actor set of every query. So an authenticated caller reaches
   the same answers as an anonymous one, plus whatever names its own key.

   The `User` root half arrived with `CHAT-mahevldm` on 2026-09-24. Before it
   the `Anon` key was the only floor.
2. ~~**The four `user: User` rows reach nobody.**~~ **They reach every caller
   since `CHAT-mahevldm`, measured on 2026-09-24.** The actor set carries the
   `User` root beside the anonymous key, because every caller is a user.

   **`listRooms` moved from deny to allow** for an anonymous caller and for an
   authenticated one. The shipped configuration is what says so:
   `{user: User, target: MessageTopic, role: ALL}`.

   At that point it was the only row of this matrix that had moved.
   **`messageById` moved later, under `CHAT-rfzsnbco`.** Result 3 carries that.

   The `GET`, `JOIN` and `MEMBERS` rows named the `MessageTopic` root as their
   target while every operation that asked for them named one room. So they
   reached no operation, and `CHAT-rfzsnbco` is what changed that. Result 3
   carries it.
3. **A grant on a domain root covers an object of that domain, since
   `CHAT-rfzsnbco`.** A permission check reads the given target and the domain
   root of that target. The owner selection does not, because a domain root
   read there would give one target two owners.

   `Anon` holds `Message:GET`, and `messageById` checks one message key. The
   check now reads the `Message` root beside that key, so the row applies.
   `send` stays denied, because the `Message` root is not the root of a room.

   **Five checks name a root as the target already.** `hasAccessToDomain`
   passes the domain root, so `addRoom`, `listRooms`, `addUser`,
   `findByUsername` and `findByUserId` compare against it directly. At those
   five the scan adds nothing, because a root key is its own root.

   **Three `MessageTopic` permissions now reach an operation.** `GET` reaches
   `getRoom` and `getRoomByName`, `JOIN` reaches `leaveRoom`, and `MEMBERS`
   reaches `roomMembers`. Each of those checks names one room, and the scan
   reads the `MessageTopic` root of that room. `joinRoom` is not one of them,
   because it names a user key. See the wider-effect table of
   `docs/superpowers/specs/2026-09-30-target-domain-scan-design.md`.

So the shipped configuration allows `User:FIND`, `User:PUT`,
`MessageTopic:ALL`, `Message:GET` and three named `MessageTopic` permissions
to every caller that reaches an identity. **The six matrix write operations
still deny for every caller.**

`addRoom` and `addUser` deny although both sides match, because no shipped row
grants `NEW` on either domain. `send` denies, and `deleteRoom` denies.

**Eleven checks move in total, and only one of them is a matrix row.**
`messageById` is that row. The other ten were denied before and allow now.
Four are composite checks over one room: `getRoom`, `getRoomByName`,
`leaveRoom` and `roomMembers`. Six are `core` checks: `PersistenceAccess.add`
and `IndexAccess.add` reach `{Anon, User, PUT}` for a `User` entity,
`PersistenceAccess.get` and `byIds` reach `{Anon, Message, GET}` for a
`Message` entity, `PubSubAccess.sendMessage` reaches `{User, Message, SEND}`,
and `TopicInventoryAccess.getUsersBy` reaches `{User, MessageTopic, GET}`.

**Two of the eleven are conditional.** `PersistenceAccess.add` and
`IndexAccess.add` move through the `{Anon, User, PUT}` row alone. Every one of
the eleven is latent. See the wider-effect table of the spec.

## Expiry

`AuthSummarizer` keeps a row when `expires` is `0L` or in the future. A grant
with an expiry in the past does not allow. That is the only expiry this
application has. **No credential expires, because no deployed transport
validates a token.** See `docs/IDENTITY-POLICY.md`.

## What is not wired

**No production type implements the annotated interfaces.**
`TopicServiceAccess`, `UserServiceAccess` and `MessageServiceAccess` in
`com.demo.chat.security.access.composite` carry the checks that name a domain
as text. The `core` package carries more, over `PersistenceAccess`,
`IndexAccess`, `PubSubAccess`, `TopicInventoryAccess`, `IKeyServiceAccess` and
`SecretsStoreAccess`. Every one of them is
latent. `CompositeControllersConfiguration` imports the three composite
interfaces and implements none of them. The controllers delegate to
`CompositeServiceBeans`, which supplies the plain services.

The other path, the programmatic wrappers in `chat-service-composite`, needs
`app.service.composite.security`. No launch script, no yml and no test sets
that property.

**So no authorization check runs in any deployed composition today.** The
matrix above is what the configuration means, not what a running deployment
enforces. `CHAT-znprrzhn` holds the wiring gap. `CHAT-ruapxetl` holds a second
defect in the programmatic wrappers.

This also explains why `chat-shell` can create a room and send a message with
no credential. The matrix denies both.

## Two expression shapes cannot be evaluated

Measured on 2026-10-01 at `09d4c9a6`, with `javap` on the compiled
`SpringSecurityAccessBrokerService` and a probe on spring-expression 7.0.9.

**A raw id cannot bind to the two argument `hasAccessTo`.** The compiled
signature is `hasAccessTo(com.demo.chat.domain.Key, java.lang.String)`. SpEL
resolves a method by name and argument count, and then by assignability. A
`Long` is not a `Key`, so the call fails with `EL1004E`. The three argument
form erases to `(Object, Object, String)`, so a raw pair of ids binds there.

**`#req.dest()`, `#req.uid()` and `#req.roomId()` name methods that Kotlin
never generates.** A Kotlin `data class` property `dest` compiles to
`getDest()`. The property form `#req.dest` resolves.

Both send expressions were repaired under `CHAT-zhjltbky`, by
`hasAccessToId` and by the property form. **The remaining sites are open.**
Every `@PreAuthorize` of the access interfaces is latent, because no
production type implements one, so this changes no running answer.

## Before turning the checks on

Read this table first. **Enabling the checks against the shipped grants would
deny `addRoom` and `send` to every caller**, including an authenticated one.
The grants need a decision before the wiring does.

`listRooms` allows since `CHAT-mahevldm`, measured on 2026-09-24. An earlier
version of this line named it beside `addRoom` and `send`, and that was true
while the four `user: User` rows reached nobody.
