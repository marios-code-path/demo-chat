# The authorization grant policy

Issue `CHAT-zhjltbky`, under the parent `CHAT-ltvfmcvh`.

**Status: draft, and it waits for the owner review.** Nothing in this document
is implemented.

Measured on 2026-10-01 at master `09d4c9a6`.

## The stipulation

The owner took four decisions on 2026-10-01. Each one answers a question that
`CHAT-zhjltbky` recorded, and each one was measured first.

1. **The `Anon` floor stays.** The actor set holds the `Anon` key and the
   `User` root. Every grant that names either key reaches every caller.
2. **Four operations deny an anonymous caller.** They are `addRoom`, `send`,
   `addUser` and `deleteRoom`.
3. **The room owner grant is written server-side at room creation.** The
   client stops writing it.
4. **This issue delivers the configuration policy and its tests.** The typed
   schema stays with `CHAT-zcxgrtqc`.

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

## What this issue changes

### 1. `userinit.yml` keeps its nine rows, and loses two dead keys

The nine rows state decision 2 correctly already. `Anon` holds `User: FIND`,
`User: PUT` and `Message: GET`. The `User` root holds the `MessageTopic` and
`Message` rows.

Two edits follow.

- Remove `rolesAllowed` and `wildcard`. They read as policy and they are not.
- Add a comment that names decision 2, so a reader cannot mistake the absence
  of a `NEW` row for an oversight.

**`UserInitConfigBindingTests` is the guard.** It reads the shipped file from
disk. Rule two already refuses a key that nothing binds, so the removal needs
its own check: the two keys must be absent.

### 2. The `role: '-'` concept leaves the shipped surface

No code subtracts a permission. The draft holds two spellings of one outcome
for a denial. `docs/superpowers/specs/2026-09-23-operation-policy-draft.md`
records both, and it states that neither has code behind it.

**This issue removes the concept from the shipped surface.** A denial is the
absence of a grant. That is the whole rule.

### 3. The room owner grant moves to the server

`TopicCommands.addTopic` writes a grant today, in the shell client. It names
the room key with `*` and it never expires.

The server takes that duty. The room creation path writes one `*` row against
the new room key, for the identity of the caller that created the room.

**Two decisions inside this one.**

**Where the identity comes from.** `chat-service-composite` does not depend on
`chat-security`, and `ContextIdentity` is the only live reader of the security
context. So the composite must not read the context itself. It takes a
`Supplier<out Publisher<Key<T>>>`, and `chat-security` supplies the bean. That
is the shape `TopicServiceAccess` already takes, so the pattern is not new.

**Which class writes the row.** Not `TopicServiceAccess`. That wrapper is
inert, because no deployment sets `app.service.composite.security`. It also
carries the refusal defect of `CHAT-ruapxetl`. A grant written there would
exist only in a composition that no launch script starts.

So `TopicServiceImpl.addRoom` writes the row, after the persistence, the
index, and the pubsub open all succeed.

**The no-identity case is decided, and it is silent on purpose.** A caller
with no identity owns no room. The server writes no grant, and the room has no
owner. This matches the policy, because `addRoom` denies a caller with no
identity once the checks are wired. It also keeps `chat-shell` working today,
where the checks are not wired and no credential is sent.

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
- **Instance access checks.** `CHAT-znprrzhn` holds the wiring. It depends on
  this issue, because the checks must not be enabled before the grants are
  aligned.
- **The discarded refusal in the programmatic wrappers.** `CHAT-ruapxetl`.
- **A token that expires.** No deployed transport validates one.

## Evidence plan

Each test names the decision it holds.

| Test | What it pins |
|---|---|
| `UserInitConfigBindingTests` | The shipped file parses, binds, and holds neither dead key |
| `AnonymousAuthorizationMatrixTests` | The six matrix rows, and a seventh for the room owner |
| A new owner grant test | The server writes one `*` row for the creating identity |
| A new no-identity test | Room creation with no identity writes no grant, and no error |
| A new expression test | Both send sites check the room, not the message |
| `CassandraAuthorizationMatrixTests` | The same rows against a real store and index |

**The owner row is the new measurement.** It is the first row that a shipped
composition answers allow for a write, so it carries the most risk.

## Open, and for the owner

1. **Does a room owner allow `deleteRoom`?** The `*` row covers every
   permission of the room, so a literal reading allows it. Decision 2 denies
   an anonymous caller, and it says nothing about an owner.
2. **Does a second `*` writer ever exist again?** A future client that writes
   `*` would create a second owner with no error. One writer answers this
   today. A check at the source would answer it in general, and it is not in
   this scope.
3. **The pending edit to `userinit.yml`.** The main checkout holds an
   uncommitted change that removes `{ user: Anon, target: User, role: PUT }`.
   That row is the only path for two of the eleven moved checks. This spec
   describes the committed nine-row file.
