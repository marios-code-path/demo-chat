# The anonymous authorization matrix

What a caller may do, and what the `Anon` root key may do.

**Measured on 2026-09-24 at `58163b7c`, and re-measured on 2026-09-30 for
`CHAT-rfzsnbco`.** The first measurement was taken on 2026-09-23 at master
`4fcae69c`. Two rows have moved since. `listRooms` went from deny to allow
under `CHAT-mahevldm`, and `messageById` went from deny to allow under
`CHAT-rfzsnbco`.

**One row was added on 2026-10-01 by `CHAT-eoqkbqve`, and the owner reversed
it on 2026-10-02.** That route carried a literal deny on 2026-10-01. The owner
removed the check on 2026-10-02, so the route answers every caller. The
section under the matrix states why.

**One row moved on the same day, and one grant was renamed.** The owner
decided that every user may add a room, so `addRoom` went from deny to allow.
The owner also renamed the `ALL` grant to `GET_ALL`, because the old name read
as "every permission" rather than "get every row". Both changes are stated
under the grants table and under the matrix.

`docs/IDENTITY-POLICY.md` states which identity a caller reaches. **This
document states what that identity may then do.** They are separate questions,
and a green identity test proves nothing here.

**The rows of this matrix run in a deployment now.** Six controllers implement
the three composite access interfaces, on RSocket and on REST. `addRoom`,
`send` and `addUser` left the latent state on 2026-10-01. The `core`
interfaces stay latent. See "What is wired, and what is not" below.

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

That test sends a real message, and the `messageById` row checks its key.
Until `CHAT-xcmpudyb`, `send` failed on a cassandra deployment, so the test
minted a message key and sent nothing.

Only the operations of that test are measured this way. The self authority,
many target and expiry cases in this document are still measured on a map
store alone.

**`*` means ownership, and not "all permissions".** It is singular per target,
and it is a sentinel, so a `*` row stops the read and its expiry decides.
`docs/superpowers/specs/2026-09-23-operation-policy-draft.md` states the three
properties under `What \* means`. **No `*` row reaches an operation below,
because the `Admin` identity is the only principal of one**, and the actor set
of every other caller excludes it. So the rank does not move this matrix. The
shipped `*` row names the `Admin` key as its target, and no operation here
names that target.

`AnonymousAuthorizationMatrixTests` holds the measurement. It wires the
production `CoreAuthorizationService`, `AuthSummarizer`,
`AuthMetadataAccessBroker` and `SpringSecurityAccessBrokerService`, and
replaces only the store and the index.

## The grants

Every deployment loads
`shared-deploy-configuration/src/main/config/userinit.yml`. It holds ten
roles. **All ten are listed here, because since `CHAT-mahevldm` the six that
name `User` reach a caller.** An earlier version of this section listed the
three `Anon` rows alone.

| Principal | Target | Permission | Reaches a caller |
|---|---|---|---|
| `Admin` | `Admin` | `*` | the `Admin` key alone, and the self rule already covers it |
| `Anon` | `User` | FIND | yes |
| `Anon` | `User` | PUT | yes |
| `Anon` | `Message` | GET | yes, and it reaches `messageById` since `CHAT-rfzsnbco` |
| `User` | `Message` | SEND | yes, and it reaches no room, because a room is another domain |
| `User` | `MessageTopic` | NEW | yes, and it reaches `addRoom` since 2026-10-01 |
| `User` | `MessageTopic` | GET_ALL | yes |
| `User` | `MessageTopic` | GET | yes, and it reaches `getRoom` since `CHAT-rfzsnbco`. **`getRoomByName` carries no check since 2026-10-02, so it reads no grant.** |
| `User` | `MessageTopic` | JOIN | yes. It reached `leaveRoom` from `CHAT-rfzsnbco` until 2026-10-02. `leaveRoom` now names the member, so the row reaches no operation |
| `User` | `MessageTopic` | MEMBERS | yes, and it reaches `roomMembers` since `CHAT-rfzsnbco` |

"Reaches a caller" is the principal side, which `CHAT-mahevldm` closed.
"Reaches an operation" is the target side, which `CHAT-rfzsnbco` closed. A
check on one object now reads the domain root of that object beside it.

**`GET_ALL` carries the permission that `ALL` carried on 2026-09-30.** The
owner renamed it on 2026-10-01, because the value reads as "get every row of a
dataset" and not as "every permission". A `*` row is what carries every
permission, and the section below states that rule.

## The Admin wildcard on every root

**The Admin identity holds `*` on every domain root.** The owner decided this
rule on 2026-10-01. `InitialUsersService` writes one row per loaded domain, so
a domain that a later release adds takes its row with no second edit.

**`userinit.yml` cannot express the rule.** A role definition names one user
and one target, so the file carries `{Admin, Admin, '*'}` alone. The per-root
rows are generated at user initialization, after the identities load and after
the roots load.

**The Admin rows do not move this matrix.** The actor set of a query is the
`Anon` key, the `User` root and the caller. The `Admin` key is in no other
caller's actor set, so a `*` row for Admin reaches the Admin identity alone.

`AdminRootGrantWiringTests` holds that measurement against a memory
deployment. It reads the store through `PersistenceServiceBeans` and asserts
one row per domain root, plus the shipped row that names the `Admin` key.
The checks run since 2026-10-01, so these rows reach the Admin identity in a
deployment.

## The initial grants are a seed

**`userinit.yml` gives the first value of a grant, and the store holds every
later change.** The owner decided this rule on 2026-10-07. See
`CHAT-ghwtzgjp`.

`InitialUsersService` reads the stored rows of each initial grant before it
writes. It applies the rule to the ten rows of the table above and to the
generated Admin rows.

| Stored rows with the same principal, target and permission | What a start does |
|---|---|
| None | It writes the grant. |
| One | It writes nothing. An expired or a muted row counts. |
| More than one | It keeps the row with the highest key id and removes the others. |

**A restart does not undo a revoke.** Expire the row to revoke an initial
grant. The next start finds that row and writes nothing.

**The removal changes no access decision.** The copies of one grant tie on the
wildcard level and on the principal rank. So `AuthSummarizer` breaks the tie
with the key comparator, and it selects the highest key id. The start keeps
that row.

**Before this rule, each start wrote one more copy of each named grant.**
Measured on 2026-10-07: two starts on one Redis store added 9 rows. Measured
again with the restart tests: the Redis and Cassandra tests both fail against
the earlier service.

- `InitialGrantSeedTests` holds the rule against a recording store.
- `RedisGrantRestartTests` holds it across a restart. It reads the deciding row
  before and after the restart, and it holds a revoke.
- `CassandraGrantRestartTests` asserts one row per named initial grant after
  two starts.

**The read is not atomic with the write.** Two processes that start at the
same time against one store can both write a grant. The owner guard has the
same limit.

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

## The room member

**A join grants the member `SEND` and `SUBSCRIBE` on the room, and a leave
expires both.** The owner decided `SEND` on 2026-10-02, under `CHAT-mfveaecc`,
and `SUBSCRIBE` on the same day, under `CHAT-lfaajjcj`. `MembershipGrant` is the
only writer.

- A join writes one row `{member, room, SEND}` and one row
  `{member, room, SUBSCRIBE}`, each with the expiry 0, which never expires.
- A leave sets the expiry of both rows to the time of the leave.
- A second join sets the same rows to 0 again. It writes no further row.

**`listen` checks `SUBSCRIBE` on the room.** Before `CHAT-lfaajjcj`, only the
owner `*` row and the `Admin` `*` row on the `MessageTopic` root carried it. So
a member that joined could send and could not listen.

**The writer changes the rows in place.** One member, one room and one
permission keep one expiry. Level 3 of the rank orders rows by key id, and a uuid key carries no
order. An appended expiry row could therefore lose to an older live row.

**An anonymous caller cannot join a room.** The owner decided this on
2026-10-02. The `JOIN` check on `#req.uid` alone allows it, because a key holds
every right over itself. Measured on 2026-10-02: an anonymous caller passed
that check for the `Anon` key. So `TopicServiceImpl.joinRoom` refuses a member
that is the `Anon` key with `AnonymousJoinException`, before any write.

**The `Anon` key and the `User` root receive no row.** Both keys are in the
actor set of every query. A row for either key would let every caller send to
the room and listen to it. The writer drops both keys, as a second guard behind
the refusal.

**The owner row is not touched.** The writer reads `SEND` and `SUBSCRIBE` rows
alone. Level 1 of the rank keeps the owner `*` row above them, so an owner who
leaves can still send and listen.

**A failed write fails the join or the leave, and the membership change
stays.** `RoomMemberGrantException` names the member and the room.

## The core routes

**The core RSocket routes require `ROLE_SERVICE` or `ROLE_ADMIN`.** The owner
decided this on 2026-10-02, under `CHAT-rdlghoqe`. One rule in
`RSocketServerConfiguration` holds it, at the seam.

| Route | Rule |
|---|---|
| `persist.**`, `index.**`, `pubsub.**`, `secrets.**` | `ROLE_SERVICE` or `ROLE_ADMIN` |
| `key.key`, `key.rem` | `ROLE_SERVICE` or `ROLE_ADMIN` |
| `key.rootOf`, `key.exists` | every caller |
| every composite route | every caller at the seam. Each operation carries its own method check |

**Measured before the rule, on 2026-10-02.** A caller with no credential read
the `Admin` password hash on `secrets.get`, read all 18 grant rows on
`persist.authmetadata.all`, wrote a `{Anon, MessageTopic, *}` row on
`persist.authmetadata.add`, and sent a message in the name of `Admin` on
`pubsub.sendMessage`. Each call completed. The core access interfaces have no
production implementation, so no check ran there. `CoreRouteAccessTests` pins
the refusal of each call.

**`key.rootOf` and `key.exists` stay open.** They read the registry and write
nothing. The shell resolves a room creator through `key.rootOf` before
`addRoom`, and an anonymous caller may add a room.

**The roles.** `CoreUserDetailsService` gives every user `ROLE_USER`. It adds
`ROLE_ADMIN` for the `Admin` key, and `ROLE_SERVICE` for each handle in
`app.security.service-accounts`. The default handle is `Service`, which
`userinit.yml` declares. The account is a plain user, so `ChatIdentity` stays
closed.

**Who presents which credential.**

- The authorization server sends the `Service` credential on every request.
  `docs/BUILD.md` carries the launch steps.
- The shell sends the credential of its login. A shell command that calls a
  core route directly needs an `Admin` login.
- The `rest` facade calls composite routes alone, so it needs no credential.

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

**The `@PostFilter` path is gone since 2026-10-04.** `PersistenceAccess`,
`hasAccessToEntity` and `EntityTargets` were removed with the core access
interfaces, because no production class implemented them. See
`CHAT-wgdnjdio`. `AccessBroker.permittedTargets` still states the contract
above, and no production code calls it.

**The contract this replaced allowed a whole list when one target had a
grant.** The Boolean many target checks read the rows of every target into one
set and asked whether it held the permission. One Boolean cannot carry a
subset, so those checks are removed. No production class implements
`PersistenceAccess`, so nothing ran the old contract in a deployment, and
nothing does now. See "What is wired, and what is not".

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
| `addRoom`, MessageTopic NEW | **allow** | **allow** | deny | deny | deny |
| `send`, room SEND | deny | deny | deny | deny | deny |
| `submit`, room SEND | deny | deny | deny | deny | deny |
| `command status`, `permitAll()` | owner only | owner only | owner only | owner only | owner only |
| `whoami`, User FIND | **allow** | **allow** | deny | deny | deny |
| `messageById`, GET | **allow** | **allow** | deny | deny | deny |
| `listRooms`, MessageTopic GET_ALL | **allow** | **allow** | deny | deny | deny |
| `addUser`, User NEW | deny | deny | deny | deny | deny |
| `getRoomByName`, no expression | **no check** | **no check** | **no check** | **no check** | **no check** |

The RSocket `message-send` rejects a sender that is not the caller, since Stage 1.
`submit` and `command status` are message commands. `docs/MESSAGE-COMMANDS.md`
describes them.

**`addRoom` moved from deny to allow on 2026-10-01.** The owner decided that
every user may add a room, because a room is an unbounded resource and no
counter bounds it. The `{User, MessageTopic, NEW}` row now reaches every
caller that holds an identity. A caller with no context, or with no
authentication, still denies, because that caller reaches no actor set at
all.

**`addUser` still denies, and that is the same decision.** Creating a user is
the work of an `Admin`, so no row grants `NEW` on the `User` domain to a
regular caller.

### `getRoomByName` carries no check, and the owner reversed this

**The 2026-10-01 row was a refusal by construction, and the owner reversed it
on 2026-10-02.** The earlier expression was the literal `false`. It resolved
no method and looked up no bean, so it refused every caller.

**`ByStringRequest` holds a name and no id.** So no target key exists at the
check, and no honest expression can judge the route.

**The route answers every caller that matches a room.** **The answer is
minimal.** `MessageTopic` carries the room key and the full room name, and
nothing else. So the route exposes no room content.

**The access condition travels with the next operation.** Every operation on a
resolved room holds its own check. So a caller that reads a name still cannot
send, join, or read the room without a grant.

**The owner named an alternative and deferred it.** An after-fetch
authorization would filter after the fetch, for a target whose key was unknown
at the fetch. The owner ranks that work below the first production release.

**An unknown name still answers `NotFoundException`.** A miss is not a grant
question.

Five shell call sites use the route: `TopicCommands.kt` lines 55, 62,
77 and 99, and `PubSubCommands.kt` line 46. **The earlier deny refused all
five.** **No credential repairs them, because none is needed.** The route
carries no check, so the five reach the delegate. Measured on 2026-10-02:
`RSocketBoundaryProbeDeniedTests` sends one call through a broker that denies
everything, and the delegate answers the room.

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
   `getRoom`, `JOIN` reaches `leaveRoom`, and `MEMBERS` reaches
   `roomMembers`. Each of those checks names one room, and the scan
   reads the `MessageTopic` root of that room. `joinRoom` is not one of them,
   because it names a user key. `getRoomByName` is not one either, because the
   owner removed its check on 2026-10-02. See the wider-effect table of
   `docs/superpowers/specs/2026-09-30-target-domain-scan-design.md`.

   **Two `MessageTopic` permissions reach an operation since 2026-10-02.**
   `leaveRoom` names the member now, as `joinRoom` does, so `JOIN` reaches
   neither. The room check let any caller remove any member. See
   `CHAT-mfveaecc`.

So the shipped configuration allows `User:FIND`, `User:PUT`,
`MessageTopic:NEW`, `MessageTopic:GET_ALL`, `Message:GET` and three named
`MessageTopic` permissions to every caller that reaches an identity.

**Four matrix operations denied for every caller on 2026-10-01, and three do
today**: `send`, `addUser` and `deleteRoom`. `send` and `deleteRoom` deny
because a room is an object of another domain, and only an owner row holds its
rights. `addUser` denies because creating a user is the work of an `Admin`.
**`getRoomByName` left this list on 2026-10-02**, because the owner removed
its check. See the sections above.

**Ten checks move in total, and only one of them is a matrix row.**
`messageById` is that row. The other nine were denied before and allow now.
Three are composite checks over one room: `getRoom`, `leaveRoom` and
`roomMembers`. Six are `core` checks: `PersistenceAccess.add`
and `IndexAccess.add` reach `{Anon, User, PUT}` for a `User` entity,
`PersistenceAccess.get` and `byIds` reach `{Anon, Message, GET}` for a
`Message` entity, `PubSubAccess.sendMessage` reaches `{User, Message, SEND}`,
and `TopicInventoryAccess.getUsersBy` reaches `{User, MessageTopic, GET}`.

**Two of the ten are conditional.** `PersistenceAccess.add` and
`IndexAccess.add` move through the `{Anon, User, PUT}` row alone.

**Three of the ten are enforced, and six are latent.** The three composite
checks over one room are wired, on both transports. The six `core` checks are
not. See "What is wired, and what is not" below, and the wider-effect table of
the spec.

**The six `core` checks no longer exist.** Their interfaces were removed on
2026-10-04. A role rule guards those routes instead. See "What is wired, and
what is not".

## Expiry

`AuthSummarizer` keeps a row when `expires` is `0L` or in the future. A grant
with an expiry in the past does not allow.

**A grant expiry is not a credential expiry.** An agent bearer token carries
its own expiry. The REST chain answers 401 for an expired token, and the
RSocket seam answers `0x401`, before any grant is read. See
`docs/IDENTITY-POLICY.md`.

## What is wired, and what is not

**Six controllers implement the three composite access interfaces.** Measured
on 2026-10-01 on branch `chat-znprrzhn-enforcement`, commit `601ed380`. Each
controller delegates to `CompositeServiceBeans`, and each now declares the
annotated interface as a supertype.

| Controller | Interface | Transport |
|---|---|---|
| `TopicServiceController` | `TopicServiceAccess` | RSocket |
| `MessageServiceController` | `MessageServiceAccess` | RSocket |
| `UserServiceController` | `UserServiceAccess` | RSocket |
| `ChatTopicServiceController` | `TopicServiceAccess` | REST |
| `ChatMessageServiceController` | `MessageServiceAccess` | REST |
| `ChatUserServiceController` | `UserServiceAccess` | REST |

**Every method of those three interfaces is enforced.** All fifteen are
listed below. **The first fourteen left the latent state on 2026-10-01, at
commit `601ed380`.** `listMessages` arrived on 2026-10-03 under `CHAT-rghaeqsa`,
and it was enforced from its first commit.

| Interface | Operation | Permission | Matrix row |
|---|---|---|---|
| `TopicServiceAccess` | `addRoom` | NEW | **addRoom** |
| `TopicServiceAccess` | `deleteRoom` | REM | |
| `TopicServiceAccess` | `listRooms` | GET_ALL | **listRooms** |
| `TopicServiceAccess` | `getRoom` | GET | |
| `TopicServiceAccess` | `getRoomByName` | no check | **getRoomByName** |
| `TopicServiceAccess` | `joinRoom` | JOIN | |
| `TopicServiceAccess` | `leaveRoom` | JOIN | |
| `TopicServiceAccess` | `roomMembers` | MEMBERS | |
| `MessageServiceAccess` | `listenTopic` | SUBSCRIBE | |
| `MessageServiceAccess` | `listMessages` | SUBSCRIBE | |
| `MessageServiceAccess` | `messageById` | GET | **messageById** |
| `MessageServiceAccess` | `send` | SEND | **send** |
| `UserServiceAccess` | `addUser` | NEW | **addUser** |
| `UserServiceAccess` | `findByUsername` | FIND | |
| `UserServiceAccess` | `findByUserId` | FIND | **whoami** |

**Seven matrix rows move out of the latent state.** The matrix below states
each one. The other seven operations are not matrix rows, and each answers as
this document already described.

**The `core` routes are guarded by role, not per object.** The owner decided
this on 2026-10-04.

- RSocket: `persist.**`, `index.**`, `pubsub.**`, `secrets.**`, `key.key`
  and `key.rem` require `ROLE_SERVICE` or `ROLE_ADMIN` (`CHAT-rdlghoqe`).
  `key.rootOf` and `key.exists` stay open.
- REST: a REST launch refuses the core controllers at startup
  (`CHAT-bnnkhgbd`).
- `CoreRouteAccessTests` holds one transport test per boundary: persistence,
  index, pubsub, topic inventory, key and secrets. A caller with no
  credential and a plain user are refused, and the store shows no effect. A
  service call on the same route is the control.

`PersistenceAccess`, `IndexAccess`, `PubSubAccess`, `TopicInventoryAccess`,
`IKeyServiceAccess` and `SecretsStoreAccess` were removed with their 39
annotations. No production class implemented them, so none ran. See
`CHAT-wgdnjdio`, `CHAT-kdxglvtt` and `CHAT-zwopgvkx`. `CHAT-ruapxetl` holds
the programmatic wrappers in `chat-service-composite`, which need
`app.service.composite.security`. No launch script, no yml and no test sets
that property.

**A refusal is a refusal on both transports.** RSocket answers
`ApplicationErrorException` with `Access Denied`. No exception handler claims
`AuthorizationDeniedException`, so the transport reports application error
0x201. REST answers 403 through `KeyRefusalAdvice`. **Before that handler
existed, a denial answered 500**, and a 500 is not a refusal to any caller.

**Three repairs were necessary to reach that.** Each was measured, and each is
recorded on `CHAT-znprrzhn`.

1. **A JDK dynamic proxy hides a class level `@RequestMapping`.** Method
   security proxies a controller that carries an annotated supertype. The
   controller implements interfaces, so the proxy is a JDK proxy. That proxy
   carries the interfaces and not the implementation class, so
   `RequestMappingHandlerMapping` saw no route and every REST route answered
   404. The routing annotations sit on `ChatTopicServiceRestMapping` now.
   `LongTopicRestTests` passes on the same controller, because no method
   security is active in its slice.
   **That 404 was a reading of a `@WebFluxTest` slice.** A real launch makes a
   CGLIB proxy, and its routes answered. `CHAT-pjymtozd` measured it on
   2026-10-04. See the table below.
2. **A facade method crosses no proxy.** A default method that calls its
   member on the same object never leaves that object. The member's
   `@PreAuthorize` did not run, and a denied caller reached the service.
   Measured under a JDK proxy and under CGLIB. The five facade methods of
   `ChatTopicServiceRestMapping` carry their own check now.
3. **`AuthorizationDeniedException` had no renderer.** `KeyRefusalAdvice`
   gained an `AccessDeniedException` handler, which answers 403.

**Repairs 1 and 2 did not reach the message controller until 2026-10-03.**
`ChatMessageServiceController` kept its class level `@RequestMapping`, and no
facade method carried a check. The routing annotations now sit on
`ChatMessageServiceRestMapping`, and each facade method carries the check of
its member. `MessageRestAccessTests` holds the proof.

**The two defects did not appear in the same place.**

- **The 404 appeared in a `@WebFluxTest` slice alone.** The slice makes a JDK
  proxy, which hides the class level mapping. `CHAT-evxtlmfs` measured it there
  on 2026-10-03.
- **A real launch skipped the checks.** `CHAT-oykeniec` ran the single process
  launch of `docs/MCP-CREDENTIAL-ISSUANCE.md` on 2026-10-04, at `67c66bca`,
  before the repair. Every route answered. A listen without `SUBSCRIBE`
  answered 200. A send without `SEND` reached the service. At `04d8424d`, after
  the repair, all three refuse with 403.

So the repair closed an open access path in that launch shape. It did not
repair a 404 there.

**The proxy type decides which defect appears.** `CHAT-pjymtozd` read the bean
type from `/actuator/beans` on 2026-10-04.

- A real launch makes `ChatTopicServiceController$$SpringCGLIB$$0`, because
  Spring Boot forces class proxies. A CGLIB proxy keeps the class level mapping
  visible.
- `spring.aop.proxy-target-class=false` makes `jdk.proxy2.$Proxy107` in the
  same launch, and the 404 of the slice appears there too.

The `/topic` routes at `447a1312`, the `CHAT-znprrzhn` commit before the
routing move. The identity is `Anon`, because the `Agent` account did not exist
yet, and `Anon` owns no room.

| Call | CGLIB, the default | JDK, forced |
|---|---|---|
| `GET /topic/list` | 200 | 404 |
| `DELETE /topic/id/OTHER`, no `REM` grant | **204, the delete reached the service** | 404 |
| `GET /topic/name/{unknown}` | 403, the literal `false` check | 404 |

At this branch, both proxies answer the same: `GET /topic/list` 200, the
delete 403, and an unknown name 404. So the routes and the checks hold under
both proxies now.

The measured statuses, with one agent token. `OWNED` is a room the agent
created. `OTHER` is a registered room id on which the agent holds no grant.

| Call | `67c66bca` | `04d8424d` |
|---|---|---|
| `POST /send/OWNED` | 201 | 201 |
| `GET /list/OWNED` | 404, no route | 200, completes |
| `GET /topic/OWNED` | 200 | 200 |
| `GET /id/{message}` | 200 | 200 |
| `POST /send/OTHER` | 500, reached the service | 403 |
| `GET /topic/OTHER` | 200 | 403 |
| `GET /list/OTHER` | 404, no route | 403 |
| `GET /list/{unknown}` | 404 | 404 |
| any route, no token | 401 | 401 |

The REST routes of `MessageServiceAccess`. Each path is under `/message`, and
each `{id}` resolves through the registry first.

| Operation | REST route | Media type | Check |
|---|---|---|---|
| `listenTopic` | `GET /topic/{id}` | NDJSON, stays open | SUBSCRIBE on the room |
| `listMessages` | `GET /list/{id}` | NDJSON, completes | SUBSCRIBE on the room |
| `messageById` | `GET /id/{id}` | JSON | GET on the message |
| `send` | `POST /send/{id}` | JSON | SEND on the room |

**`listMessages` reads the stored messages of one room, and the response
completes.** `listenTopic` stays open for live messages, so it cannot serve
the history alone. A caller without `SUBSCRIBE` gets 403. An unknown room gets
404. `CHAT-evxtlmfs` added the REST route on 2026-10-03. Before, only the
RSocket route `message-list-topic` served it.

**`chat-shell` reaches the anonymous identity at the RSocket seam.**
`RSocketSecurity.anonymous` establishes it. So a shipped row reaches the shell,
and `addRoom` allows. Measured: `LongShellTopicCommandsTests.should add topic
and list at least one` passes, and it adds two rooms.

**`send` denies for the shell**, because a room is an object of another domain
and only an owner row holds its rights. One shell failure comes from `addUser`.
**Six came from `getRoomByName` until 2026-10-02**, and the owner removed that
route's check. See `CHAT-wbcbptiq` and `CHAT-dgjhljbl`.

## Two expression shapes that could not be evaluated

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
`hasAccessToId` and by the property form.

**`CHAT-eoqkbqve` repaired every remaining site**, measured on 2026-10-01.
Five checks moved to `hasAccessToId`: `deleteRoom`, `getRoom`, `roomMembers`,
`listenTopic` and `messageById`. Two moved to the property form: `joinRoom`
and `leaveRoom`. One was replaced by the literal `false`: `getRoomByName`,
for the reason in the section above. **That literal is gone, removed by the
owner on 2026-10-02.** So seven checks carry an expression now, and
`getRoomByName` carries none.

`SendCheckExpressionTests` reads each annotation from the compiled interface
and evaluates it through SpEL, so a regression in any of the seven fails
there. It pins the absence on `getRoomByName` too. **Without that repair a
wired check refuses every caller**, and it does so with `EL1004E` and no
stated cause.

## After turning the checks on

**The checks run now.** This section is what a deployment refuses. Measured on
2026-10-01 on branch `chat-znprrzhn-enforcement`, commit `601ed380`.

**`send` and `deleteRoom` deny for a caller that owns no room.** The server
writes an owner row at room creation, and that row carries `*` on the room
key. So the creator of a room may send to it and may delete it. Any other
caller denies. A room with no owner row denies to every caller.
**Read this paragraph as of 2026-10-01.** Since 2026-10-02 a member that
joined a room may also send to it. See `The room member` above.

**`addUser` denies for every caller but `Admin`**, because creating a user is
the work of an Admin. `Admin` holds `*` on every domain root since 2026-10-01.

**`getRoomByName` carries no check since 2026-10-02.** The owner removed the
literal deny, and the route answers every caller that matches a room.
**Read this paragraph as of 2026-10-01:** it read "denies every caller by
construction. The route is unusable until `CHAT-dgjhljbl` lands."

`listRooms` allows since `CHAT-mahevldm`, measured on 2026-09-24. `addRoom`
allows since 2026-10-01. An earlier version of this line named `addRoom` and
`send` together, and that was true while no row granted `NEW` on a topic.

## The MCP adapter account

`CHAT-werokcbb` gave the MCP adapter a dedicated account, measured on
2026-10-01. Three facts define it.

1. **`Agent` is a plain user.** `userinit.yml` declares the handle, and startup
   creates the account. It holds no `ChatIdentity`. That type stays a closed set
   of `ADMIN` and `ANON`, and nothing here opens it.
2. **Its reach equals the floor.** No grant row names the agent as its
   principal. `InitialUsersService` writes one `*` row per domain root for the
   `Admin` key, and the agent key is not that key. The agent is in no other
   caller's actor set, so those rows do not reach it.
3. **The adapter's read rests on a floor row, not on an agent row.** The read
   is permitted by `{user: User, target: MessageTopic, role: GET}`. **A later
   change to that row would remove the adapter's read, and no agent-scoped row
   would exist to restore it.** The `send` rule is the same shape. This is
   risk 4 of the design.

### The agent against the administrator

The two matrices differ at three operations. `AnonymousAuthorizationMatrixTests`
pins the pair, and it pins the difference set.

| Operation | Agent | Admin |
|---|---|---|
| `getRoom`, `GET` | allow | allow |
| `listRooms`, `GET_ALL` | allow | allow |
| `addRoom`, `NEW` | allow | allow |
| `whoami`, `User FIND` | allow | allow |
| `messageById`, `Message GET` | allow | allow |
| `send` to a room the caller does not own | **deny** | allow |
| `addUser`, `User NEW` | **deny** | allow |
| `deleteRoom`, `REM` | **deny** | allow |

**The last three rows are the agent's narrowing, and they are the whole of it.**
The scope ceiling applies here as it does everywhere: the actor set carries the
`Anon` key and the `User` root, so no grant change can narrow a caller below the
floor. The agent therefore also holds `addRoom`, `listRooms` and `messageById`.
