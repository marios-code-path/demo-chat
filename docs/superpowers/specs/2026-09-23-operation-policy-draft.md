# The operation policy, a draft

**This is a design input. No code loads it.** Written on 2026-09-23 from the
owner draft that was written into
`shared-deploy-configuration/src/main/config/userinit.yml`. That file is
configuration, it must parse, and it must match the schema that binds it, so
the draft lives here instead.

`CHAT-zhjltbky` holds the policy decision. `CHAT-zcxgrtqc` holds the typed
schema this draft needs.

Read `docs/ANONYMOUS-AUTHORIZATION.md` first. It measures what the current
grants allow.

## The stipulation

**Everything is denied implicitly.** The permissions in `userinit.yml` cover a
small prototype slice of the known operation surface.

The finer grained policy happens mainly **at creation**. Other sites need
their own decision. `close` is one, because it shuts everyone out except the
holder of `*`.

So the policy applies at command sites.

## The shape

    OperationPolicy { operations: List<Operation> }
    Operation        { authorizations: List<DefaultAuthorization> }
    DefaultAuthorization {
        command: String
        user:    T
        target:  T
        role:    String
        expire:  Enum
    }

An `OperationClass` is a child of `OperationPolicy`, so the minimum
composition is one `OperationPolicy` holding a list of `Operation`.

## The matching rules

The order of matching is:

1. `property_name` or `User`.
2. The match, then `{ROLE}` or `{ID}`.
3. For `ROLE`, the property named must have type `T`. It becomes the
   `principal` side of the AuthMetadata query. The right side of the
   expression is the query on the `role` property.

So `user: dest{ROLE=*}` reads as

    findAccessFor(dest, where role = '*').principal

For `ID`, the value matches one of `ACTIVE`, `ROOT`, `ANON` or `ADMIN`.
`ROOT` names the root key of the **User domain**. `ANON` and `ADMIN` name the
root keys of the anonymous user and the admin user. `ACTIVE` names the caller
that is logged in.

## What a root key is

**A root key is evidence of a domain, and it acts as the tangible root
representation of that domain.** The owner stated this on 2026-09-23.

So `RootKeys` holds one key per domain, and that key stands for the domain
itself rather than for any one object inside it. `User`, `Message`,
`MessageTopic`, `TopicMembership` and `AuthMetadata` each have one.

`Anon` and `Admin` are different. Each names one user, and
`InitialUsersService` merges the created user key over the generated one. So
`Anon` and `Admin` are objects of the User domain, not domains.

## How a root key grant works

**A grant on a domain root covers every object of that domain.** The owner
decided this on 2026-09-23. It is a required change, because the code does
not do it today.

A permission is `{ principal: T, target: T, role: String }`. When either side
names a domain root, that side expands to every object of the domain.

The shipped row

    { user: User, target: Message, role: SEND }

therefore reads: **given any Message object as the target, any User object as
the principal holds `SEND`.**

Two things invalidate an expanded grant:

1. Role expiry.
2. A subtractive un-grant, written as `role: '-'`.

### Roots already appear at the check sites

The owner noted this on 2026-09-23, naming `TopicServiceAccess` line 11.
`hasAccessToDomain` passes `rootKeys.getRootKey(domain)` as the target, so a
root key is already one side of five checks.

**Five checks name a root as the target.**

| Site | Check |
|---|---|
| `TopicServiceAccess:11` | `hasAccessToDomain('MessageTopic', 'NEW')` |
| `TopicServiceAccess:17` | `hasAccessToDomain('MessageTopic', 'ALL')` |
| `UserServiceAccess:11` | `hasAccessToDomain('User', 'NEW')` |
| `UserServiceAccess:14` | `hasAccessToDomain('User', 'FIND')` |
| `UserServiceAccess:17` | `hasAccessToDomain('User', 'FIND')` |

**Nine checks name an object key**, through `hasAccessTo`. They are
`MessageServiceAccess` lines 14, 17 and 20, and `TopicServiceAccess` lines 14,
20, 23, 26, 29 and 32.

So the expansion is needed on a different side at each group.

- At the five root target checks, the target already equals the root that a
  `target: <Domain>` row names. **Only the principal side fails**, because a
  caller holds its own key and the row names the `User` root.
- At the nine object target checks, **the target side fails**, because a
  `target: <Domain>` row names the root and the check names one object.

`Anon:User:FIND` allows `whoami` today for this reason. Both sides match
already, because `Anon` is always in the actor set and the check names the
User root.

### What this changes

`docs/ANONYMOUS-AUTHORIZATION.md` measured the current behaviour. Under this
rule the measured matrix moves, because the four `user: User` rows reach
every user rather than nobody.

### What it needs first

**A `Key<T>` carries no domain today.** The interface in
`chat-core/src/main/kotlin/com/demo/chat/domain/KeyValuePair.kt` declares
`id` and `empty` and nothing else. `CSKey` in the cassandra module carries a
`kind`, and that is a persistence type, while `AuthMetadata.principal` and
`AuthMetadata.target` hold the domain `Key<T>`.

So the expansion cannot ask which domain a key belongs to. `CHAT-avduuqwp`
holds a stable root identity on keys, and this rule depends on it.

## The proposed entries

`key.id` names the new entity of the command. For `add` on a user, that is the
new user.

### users

| command | user | target | role | expire | Why |
|---|---|---|---|---|---|
| `add` | `key.id` | `key.id` | `*` | none | A user holds every right over itself |
| `add` | `User{ID=ANON}` | `key.id` | `-` | none | An anonymous caller cannot see this user |

### messageTopic

| command | user | target | role | expire | Why |
|---|---|---|---|---|---|
| `add` | `User{ID=ACTIVE}` | `key.id` | `*` | none | The caller that created the room owns it |
| `add` | `User{ID=ROOT}` | `key.id` | `JOIN` | now | A new room is not joinable |
| `add` | `User{ID=ROOT}` | `key.id` | `MEMBERS` | now | A new room hides its members |
| `close` | `User{ID=ROOT}` | `key.id` | `*` | now | Revoke everyone except the holder of `*` |

### message

| command | user | target | role | expire | Why |
|---|---|---|---|---|---|
| `add` | `from` | `key.id` | `REM` | none | The sender may remove the message |
| `add` | `dest{ROLE=*}` | `key.id` | `REM` | none | The owner of the topic may remove the message |

### topicMembership

| command | user | target | role | expire | Why |
|---|---|---|---|---|---|
| `add` | `member` | `key.id` | `*` | none | A membership belongs to its member |

## The owner question, recorded and not decided

**Should a policy grant only narrow permissions, or may one user give another
`*` on a topic?**

The owner view in the draft: keep one place where `*` is applied, which is
initialization and system definition. `*` then becomes a stable indicator of
ownership.

That reading makes `dest{ROLE=*}` above meaningful, because it finds the owner
of a topic by looking for the holder of `*`.

## What the current code does not support

Each line is measured on 2026-09-23 at master `42cd6a69`. **Every one of them
must close before any of this reaches configuration.**

1. **`operationPolicy` has no binding type.** `UserInitializationProperties`
   binds `passwordEncoder`, `initialRoles` and `initialUsers`. Spring ignores
   an unknown key, so the expressions would have no effect and no error.
2. **`RoleDefinition` binds `user`, `target` and `role` only.** It has no
   `expire` and no `expires`. The draft uses both spellings.
3. ~~**`*` is not a wildcard.**~~ **Repaired under `CHAT-rgdcyxlv`.** The
   summarizer expands a wildcard row to the asked permission. The `Admin` grant
   and the `*` that `TopicCommands.addTopic` writes now match an operation.
   The shipped matrix did not move, because no operation names the `Admin` key
   as its target.
4. **`rolesAllowed` and `wildcard` bind and are never read.**
   `InitialUsersService` reads `initialRoles.roles` alone.
5. **`role: '-'` has no denial meaning.** The permission check answers whether
   a list contains a string. Nothing subtracts.
6. **A grant on a domain root does not cover the objects of that domain**, and
   **a principal alias does not exist.** `docs/ANONYMOUS-AUTHORIZATION.md`
   measures both. The owner decided on 2026-09-23 that the expansion must
   happen. See `How a root key grant works` above. **A key carries no domain,
   so this one cannot close alone.**

## What a typed schema must define

`CHAT-zcxgrtqc` carries these.

- The wildcard, and where it may be written.
- Denial, and how it composes with a grant.
- Expiry, including what `none` and `now` mean as values.
- Principal aliases, meaning `ACTIVE`, `ROOT`, `ANON` and `ADMIN`.
- Object grant expansion, meaning whether a domain root reaches its objects.
- Binding tests, then authorization tests, before the policy is enabled.

## Every user is covered, including Anon and Admin

The owner decided this on 2026-09-23. A domain root grant reaches **every**
object of the domain. `Anon` and `Admin` are objects of the User domain, so
both are covered.

**That is why the explicit `Anon` rows exist.** They are how an anonymous
caller receives something of its own, and a subtractive row is how an
anonymous caller is held back.

## Order decides the outcome

A subtractive un-grant is written `role: '-'`. Its effect depends on its
place in the order and on its expiry.

| Sequence | Outcome |
|---|---|
| A standing `*`, then a `-` that has expired | `*` stands |
| A standing `*`, then a `-` that has not expired | The `-` clobbers `*` |

**A live `-` clobbers every permission before it**, for that principal,
whether the principal is a root or an object. The effect is to remove all
permission granted up to that point.

So expiry is read per row first. An expired row takes no part. The surviving
rows are then read in order, and a `-` resets what came before it.

**This is not what the code does.** `AuthSummarizer` groups by permission,
sorts inside each group, keeps the last of each group, and only then drops
expired rows. There is no order across permissions, and nothing subtracts.

## The scan must read two targets

A permission scan must query **the given target and the domain of that
target**. `CoreAuthorizationService` performs one lookup today, at lines 50,
62, 75 and 81, each `findBy(queryForTarget.apply(...))`. Each needs the
domain lookup beside it.

## The proposal: a root id on the key

The owner proposed extending `Key<T>` with `root_id: T`. A key would then
carry the root of its own domain, **and `kind: String` could go away**.

That is the mechanism `CHAT-avduuqwp` asks for, and it is what makes the two
target scan possible, because a scan can read the domain from the key it
already holds.

**The blast radius is real.** Recorded so nobody meets it by surprise.

- `Key<T>` declares `id` and `empty` today. Every implementation gains a
  field, including `Key.funKey`, `MessageKey`, and the cassandra `CSKey` that
  carries `kind` now.
- The wire shape changes. `Key<T>` keeps its own `@JsonTypeInfo` wrapper
  inside the seven E2EE types, and the serializer tests pin the current
  shape.
- Stored data predates the field. Every persisted key and every stored
  `AuthMetadata` carries no `root_id`.

## Two questions that stay open

1. **What defines the order of grants?** The rule above depends on a total
   order over the rows of one principal. `AuthSummarizer` sorts by a
   comparator that reads `key.id`, and it sorts only inside one permission
   group. `AuthMetadata` carries no sequence and no timestamp. Key id order,
   configuration order and a new sequence field are all candidates, and they
   differ once a grant is written at run time.
2. **How does stored data reach the new key shape?** A key written before
   `root_id` exists carries none. The candidates are a migration, a default
   read at load, or a rebuild.

## The close decision

**The owner decided on 2026-09-23. A close keeps the access of the owner.**

The order of the rows and the specificity of the principal give that result.
The policy needs no ownership exception.

### The evaluation model is replacement

An earlier note in this draft said that an expired row takes no part in
evaluation. **That reading is wrong.** It made `expire: now` look like a row
that does nothing.

Read `AuthSummarizer.computeAggregates`, measured at master `579a23ba`.

| Line | What it does |
|---|---|
| 54 | Keeps a row only if its principal is in the actor set |
| 61 | Groups the rows by the permission string |
| 62 | Sorts each group and keeps the last row |
| 63 | Drops that row if it expired |

So the last row of a group decides the permission. **An expired last row
removes the permission.** Line 63 carries the owner comment that states the
intent: "removing 0L allows us to overlay negative permission".

A row is not an addition. **A row replaces the answer for the principals that
it names.**

This corrects two sentences of the section `Order decides the outcome` above.
That section says that expiry is read per row first, and that an expired row
takes no part. Neither holds. Expiry is read **after** the sort, at line 63,
and an expired last row subtracts. The same section says that nothing
subtracts, and that is wrong for the same reason.

### Write the broad rows first

The narrow rows come last. The last row that matches a caller decides.

The `messageTopic` `add` entry takes this order.

| command | user | target | role | expire | Why |
|---|---|---|---|---|---|
| `add` | `User{ID=ROOT}` | `key.id` | `JOIN` | now | Nobody can join a new room |
| `add` | `User{ID=ROOT}` | `key.id` | `MEMBERS` | now | Nobody can list the members |
| `add` | `User{ID=ACTIVE}` | `key.id` | `*` | none | The caller that created the room owns it |

The `close` entry keeps one row, as the owner drafted it.

| command | user | target | role | expire | Why |
|---|---|---|---|---|---|
| `close` | `User{ID=ROOT}` | `key.id` | `*` | now | Remove every permission, except from the holder of `*` |

### How the owner survives the close

Both rows name the same target. Both rows name `*`, so both reach every
permission group of the caller that created the room.

The comparator decides which row is last.

- **For the creator**, the actor filter keeps both rows. The comparator places
  the root principal before the object principal. The creator row is last, it
  holds `*`, and it does not expire. The creator keeps every permission.
- **For a caller that holds no row of its own**, the actor filter removes the
  creator row, because that principal is not in the actor set. The close row is
  last, it expired, and line 63 drops it. That caller holds nothing.

### The specificity rule does not close the room, and this is open

**A direct grant to a third caller survives the close.** Measured on
2026-09-24 against the production `AuthSummarizer` at `500fa09f`.

A row such as `{principal: B, target: room, role: JOIN}` carries an object
principal. The close row carries a root principal. Specificity places the
object principal last, so `B` reads a live `JOIN` row and joins the room after
the close.

So specificity delivers "every caller loses the room, except a caller that
holds a narrower row". The owner asked for "every caller loses the room,
except the owner". **Those are the same sentence only while no third caller
holds a direct grant.**

Two rules cannot both hold at one specificity order.

1. The creator row must beat the close row, because ownership survives.
2. The close row must beat every other object row, because a close closes.

Both rows of rule 2 carry an object principal, and rule 1 needs the object
principal to win. **A time order cannot repair this**, because specificity
decides before time.

Three candidate mechanisms, none chosen.

| Mechanism | What it costs |
|---|---|
| A close resolves the current holders and writes one expired row for each | The write is proportional to the holders, and it is not atomic |
| A row carries an explicit precedence value, and a close writes the highest | A new field on `AuthMetadata`, and a value that a policy author must choose |
| A close keeps the owner by an exception in the rule, and beats every other row by time | Ownership stops being "the holder of `*`", so it needs its own field |

### The owner decided on 2026-09-24

**A close ends every access to the room, except the access of the owner.** A
direct grant to a third caller does not survive.

So the close row must beat every earlier row on that room, whatever principal
that row names. Specificity cannot state this, because the row it must beat
and the row it must lose to both name an object principal.

**The evaluator needs explicit state that distinguishes ownership from ordinary grants.**
A precedence value on the row is one candidate. The room-level state proposal
below is another candidate.

- A close writes at the highest precedence, so every earlier row sits below
  it.
- Inside one precedence, the rule above still holds. Specificity decides
  first, and time breaks a tie.
- A close writes two rows at that precedence: `{User{ID=ROOT}, key.id, '*',
  now}` and `{dest{ROLE=*}, key.id, '*', none}`. The second names an object
  principal, so specificity places it last and the owner keeps the room.

### Precedence is a candidate, and it is not chosen

**A review on 2026-09-24 corrected an earlier version of this section.** That
version said a close removes the access of every caller "in one write", and it
named the mechanism race free. Both statements were wrong, and the first one
contradicted the two row form written beside it.

**A constant write count is not atomicity.** Two rows are two writes. A
precedence field does not join them.

Four contracts must exist before this mechanism is reviewable. None exists.

1. **Atomic visibility.** A reader must never see the broad denial without the
   owner row. The store offers no conditional or grouped write today.
2. **Crash recovery.** A process that stops between the two writes leaves the
   room denied to the owner as well. The recovery path has no design.
3. **Precedence authority.** "The highest precedence" is not a rule. A fixed
   reserved level needs a rule about who may write at that level. A computed
   maximum needs coordination between two writers that compute it at once.
4. **Owner selection.** `dest{ROLE=*}` answers a set. It names one owner only
   while the policy guarantees that one caller holds `*` on a target. The
   draft records that question above, under `The owner question, recorded and
   not decided`, and it is still open.

**Contract 4 is closed. The owner decided on 2026-09-24: one owner per
target.**

- In valid state, `dest{ROLE=*}` identifies one owner. Concurrent enforcement
  remains open. The evaluator must not select an arbitrary holder from invalid state.
- **A second `*` grant on a target is refused at the source.** `*` is written
  at creation and by system definition, which is the owner view recorded above
  under `The owner question, recorded and not decided`. That question is now
  answered, and the answer is no.
- A transfer replaces the holder. It must not open a window with no owner or
  with two owners.
- A moderator takes narrow grants. A moderator never takes `*`.
- The shipped row `{user: Admin, target: Admin, role: '*'}` meets the rule. It
  names one holder on that target.

**The refusal is check then act, and it is not atomic.** `addRoom` already
carries the same shape for a topic name, and the register records that two
concurrent adds can both pass it. The stores offer no conditional write on the
authorization index, so this rule inherits that limit. Record it. Do not read
the rule as a guarantee against a concurrent second `*`.

Three contracts stay open: atomic visibility, crash recovery, and precedence
authority. With one owner and a creation time owner row, the first two move
from the close to creation and to transfer.

One variant is recorded and not chosen. The `add` command could write the
owner row at the high precedence, so the close writes one row rather than two.

**This variant changes the atomicity requirement rather than avoiding it.** A
review on 2026-09-24 made that precise. A close needs one write only while
ownership already exists in the store. So the requirement moves to creation,
and creation then needs a rule that stops any use of the room before the
ownership row commits. A transfer of ownership needs its own rule, because it
writes a new owner and it must not open a window with no owner or two owners.

It removes contract 1 and contract 2 from the close. It does not remove them
from the system. It removes neither contract 3 nor contract 4.

### The sweep, and a fair comparison

The alternative is a sweep. A close reads the current holders and writes one
expired row for each. It needs no new field.

**The earlier rejection of the sweep was not fair.** It named non atomicity,
and the precedence mechanism is not atomic either.

What separates them is the size and the shape of the window, and not its
presence. The sweep writes one row per holder, so the window grows with the
room. A grant written inside that window survives the close. The precedence
mechanism writes two rows whatever the room holds.

**Neither is chosen.** Define the four contracts first.

`CHAT-lbhmzccn` cannot land before this is settled, because the comparator
cannot state either answer on its own.

### The rank rule, decided by the owner on 2026-09-24

**A `*` row outranks every row that names one permission.** The owner stated
this rule, and it removes the need for a new field and for a new store
protocol.

The rank has three levels. The highest ranked row decides, and the expiry of
that row is read after it wins.

1. **A wildcard row beats a row that names one permission.**
2. **`ENTITY` beats `DOMAIN_ROOT`.** This is the specificity order, and the
   owner stated it as `[ Domain_ROOT, ENTITY ]`.
3. **Later beats earlier.** This is the time order that `CHAT-ojbgbznh`
   supplies.

#### The rule answers all four cases

| Case | Rows | Winner | Answer |
|---|---|---|---|
| The owner after a close | `{owner, room, '*', never}` and `{ROOT, room, '*', now}` | Both are wildcards, so `ENTITY` wins | The owner keeps the room |
| A third caller after a close | `{B, room, JOIN, never}` and `{ROOT, room, '*', now}` | The wildcard wins | `B` holds nothing |
| The owner example | `{USER, TARGET, '*', never}` and `{USER, TARGET, REMOVE, now}` | The wildcard wins | The second row has no effect |
| Ending a wildcard | A second `{USER, TARGET, '*', now}` | Both are wildcards at one specificity, so time wins | Nothing |

**One close row closes the room.** There is no second row and no window
between two writes.

#### What this retires

- **The precedence field is not needed.** The rank already carries the
  dimension that specificity could not.
- **The room level LWT proposal is not needed for the close.** Its remaining
  value is elsewhere, in single ownership under concurrency.
- Contracts 1, 2 and 3 above do not apply to a close any more. A close is one
  write.

#### Two consequences to read before implementing

1. **An expired wildcard denies every permission.** The rank selects the
   wildcard row first, and only then reads the expiry. So a wildcard that
   reaches its own expiry closes the target to every caller, including the
   owner. The owner named this as one of the two ways to end a wildcard.
   **The other reading breaks the close**, because a close is an expired
   wildcard row, and a rule that dropped it before the rank would leave the
   third caller holding `JOIN`.
2. **`AuthSummarizer.expand` erases the fact this rule needs.** `CHAT-rgdcyxlv`
   rewrites the permission of a wildcard row to the asked permission, so the
   comparator can no longer tell a wildcard row from a row that named that
   permission. The rank needs that bit. Either keep the origin on the expanded
   row, or rank before the rewrite.

#### The administrator invariant

**The owner confirmed consequence 1 on 2026-09-24, and named the row that
makes it safe.** A closed resource stays administrable through one invariant
row:

    { ADMIN, ENTITY_ROOT, '*', never }

**The rank places it correctly.** `ADMIN` is an object of the `User` domain,
so it is an `ENTITY` principal. A close row names a `DOMAIN_ROOT` principal.
Both rows are wildcards, so level 2 decides, and `ENTITY` wins. `ADMIN`
therefore keeps every permission on a closed target, exactly as the owner
does.

A resource is temporary for an ordinary caller. It is never beyond
administration.

**The invariant does not reach a closed room today.**
`CoreAuthorizationService` scans one target. Line 75 reads
`authIndex.findBy(queryForTarget.apply(uidB))`, so a check on room `R`
collects only the rows whose target is `R`. The invariant row names the domain
root as its target, so it is never collected, and the rank never sees it.

**So the target side of root expansion is now load bearing.** This draft
already records it above, under `The scan must read two targets`. It was
written there as the reason the four `user: User` rows of `userinit.yml` reach
nobody. It is now also the reason an administrator cannot recover a closed
resource. `CHAT-rfzsnbco` holds it, and it waits on `CHAT-avduuqwp`.

**One rule follows, and it protects single ownership.** Owner selection must
read the **exact** target and never the domain root. Without that rule,
`dest{ROLE=*}` on room `R` would answer both the owner of `R` and `ADMIN`, and
the single owner decision of 2026-09-24 would no longer hold.

#### What still stands between this rule and a working close

**The close row names `User{ID=ROOT}` as its principal, and the actor filter
removes it today.** `AuthSummarizer` line 54 keeps a row only when its
principal is in the actor set, and `CoreAuthorizationService` builds that set
from the anonymous key, the caller and the target. So the close row never
reaches a third caller. `CHAT-mahevldm` holds this, and it waits on
`CHAT-avduuqwp`.

So the order of work is the rank rule in `CHAT-lbhmzccn`, then the two sides of
root expansion. `CHAT-mahevldm` carries the principal side, and
`CHAT-rfzsnbco` carries the target side. Both wait on `CHAT-avduuqwp`, which
puts a domain on a key.

The two cassandra defects in `CHAT-rmxxtwtu` stand between any of them and a
measurement against that backend.

### Room-level LWT proposal, recorded on 2026-09-24

**This is a design proposal, not an implemented storage guarantee.** It
addresses atomic visibility, crash recovery, and transition authority together.
It does not authorize a precedence field or a wire change.

#### Coordinate the room

The coordination key is the room identity. It is not the tuple
`(source, destination, grant)`. Closure affects every principal and permission
on that room. Independent grant heads cannot enforce unique ownership or
atomic closure across those grants.

The proposed authoritative record is:

```text
room_authorization
room_id | revision | owner_id | lifecycle | operation_id
```

The physical key must include the deployment partition wherever that partition
separates otherwise identical room identifiers. This layout is conceptual, not
a final CQL schema.

Creation conditionally establishes the owner and lifecycle together. Closure
conditionally changes lifecycle and retains the owner. Transfer conditionally
replaces the owner against the revision used for its authorization decision.

Cassandra lightweight transactions provide conditional, linearizable updates.
They supply the proposed serialization point for these transitions.
See [Cassandra guarantees](https://cassandra.apache.org/doc/stable/cassandra/architecture/guarantees.html).

#### Atomic visibility and evaluation

The evaluator reads owner and lifecycle from one authoritative state. A closed
room preserves owner access and denies every other caller. Ordinary grants
cannot override closure.

This proposal replaces the separate closure and owner-preservation rows with
one lifecycle transition. Precedence becomes an evaluation rule rather than a
caller-supplied value on each grant.

Creation must prevent room use before authorization state commits. A room
record in another table does not commit atomically with this record merely
because both records name the same room. Creation needs a publication protocol
and recovery for partial publication.

Missing ownership or multiple legacy owners must not cause arbitrary owner
selection. The response and repair procedure remain design decisions.

#### Crash recovery and retries

A process crash cannot commit half of one LWT state transition. However, a
timeout or lost response can leave the caller uncertain about its result.
The writer must reconcile that outcome before retrying.

A single `operation_id` in HEAD is insufficient for durable retry evidence.
A later transition can replace it before the earlier caller retries. The
design must define durable operation receipts, retention, and retry behavior.

Complete history also needs durable event capture with the commit. If revision
18 disappears from HEAD before projection, an asynchronous logger cannot
reconstruct it from revision 19. The final design must specify how committed
events survive until projection succeeds.

The append-only history can remain a projection. It cannot establish current
authorization or guarantee complete audit history without that capture protocol.

#### Transition authority and consistency

LWT checks expected state. It does not determine whether a caller may close a
room, transfer ownership, or create a grant.

The application must define authorized transitions and validate their callers.
The conditional write must check the revision used for that validation. All
relevant writers must follow this protocol, including initialization and repair.
An unconditional write must not bypass it.

Readers need an explicit consistency contract. An eventual read or stale cache
can allow access after closure. Cassandra supports serial reads for LWT state.
An earlier version of this line cited the Go driver. This repository uses the
Java driver, so that citation is removed rather than replaced. Take the
consistency level from the Java driver documentation when this is designed.

The design must select consistency levels and their datacenter scope. It must
also define cache behavior and operations already authorized when closure commits.
A state CAS alone does not make a later message write atomic with authorization.

#### Current storage boundary, and two live defects

The current Cassandra mappings do not implement this protocol. **They also do
not hold more than one grant per target.** Measured on 2026-09-24 at
`f75be89b`.

**Defect one. The cassandra authorization index keeps one row per target and
one row per principal.**

`keyspace-long.cql` and `keyspace-uuid.cql` declare
`auth_metadata_target` with `PRIMARY KEY (target)` and
`auth_metadata_principal` with `PRIMARY KEY (principal)`.
`AuthMetadataByTarget` and `AuthMetadataByPrincipal` carry one `@PrimaryKey`
each and no clustering column. So a second grant on one target **overwrites**
the first.

`CoreAuthorizationService` reads through `authIndex.findBy(queryForTarget)`,
so on this backend a target answers at most one grant.

Three consequences.

1. **Every mechanism in this draft needs several rows on one target.** Order,
   replacement, close and precedence all do. None of them can work on this
   backend until the mapping changes.
2. **The shipped `userinit.yml` does not survive.** It writes four rows whose
   target is the `User` root key. One survives.
3. **`docs/ANONYMOUS-AUTHORIZATION.md` does not describe this backend.** That
   matrix was measured with a map store, which keeps every row.

**Defect two. `AuthMetadataIndex.rem` removes nothing.** It calls
`deleteById(key.id)` on both repositories, and `deleteById` uses the primary
key of the entity, which is the target and the principal. So it deletes the
row whose target equals a grant key id. This is the same shape as the
`TopicIndex.rem` trap that the register already records.

`AuthMetadataIndex.add` saves the target and principal entries sequentially.
The shared persistence API supplies no conditional aggregate transition.
Adding a revision field alone cannot provide the required guarantees.

**Both defects are in shipped code and neither belongs to this design.**
`CHAT-rmxxtwtu` holds them. Fix them before measuring any mechanism here
against Cassandra.

The shared authorization interface must define the transition contract.
Each supported backend must provide equivalent guarantees or explicitly reject
the unsupported composition. Cassandra LWT cannot establish guarantees for other backends.

#### Required evidence before implementation is accepted

1. Race two creation attempts for one room. Prove that only one owner commits.
2. Close a room with a direct non-owner grant. Prove that only the owner retains access.
3. Race transfer with closure. Prove that readers observe valid owner and lifecycle pairs.
4. Crash during creation publication. Prove that no usable room lacks committed ownership.
5. Lose a successful transition response. Prove that retry cannot duplicate the operation.
6. Commit a later transition before retry. Prove that earlier operation evidence remains available.
7. Stop history projection between revisions. Prove that every committed event remains recoverable.
8. Test stale reads, caches, and concurrent grants against the chosen closure boundary.
9. Reject unauthorized transitions and caller-supplied precedence overrides.
10. Run the same transition contract tests for every supported backend.

The remaining design decisions are publication, retry receipts, durable event
capture, read consistency, transition authority, and invalid-state recovery.
`CHAT-lbhmzccn` remains blocked on the policy mechanism.
`CHAT-ojbgbznh` must not add precedence merely because this candidate exists.
Clock stamps alone do not provide aggregate serialization or atomic publication.

### Earlier implementation decomposition

These three items describe the earlier comparator proposal, measured at master
`579a23ba`. The LWT candidate requires a revised decomposition if selected.

1. ~~**`*` must expand to the permission set during evaluation.**~~ **Done.**
   `AuthSummarizer.expand` rewrites a wildcard row to the permission that the
   caller asks. The row then joins the group of that permission, so the order
   and the expiry decide the answer. `AuthMetadataAccessBroker` passes the
   permission through `AuthorizationService.getAuthorizationsAgainst`, and it
   still reads `permissions.contains(perm)`. `CHAT-rgdcyxlv`.

   **A wildcard expands to the asked permission, and not to a permission set.**
   A closed set cannot work here. The creator of a room must hold permissions
   that no row names, and no code holds a list of every permission.
2. **The comparator must sort by principal specificity first, and then by
   time.** `AuthSummarizer` takes the comparator from its caller, and the
   shipped comparator reads `key.id`. `CHAT-ojbgbznh` carries the clock for
   the time part.
3. **The actor set must hold the domain root.** `CoreAuthorizationService`
   builds that set from the anonymous key, the caller and the target, at lines
   63, 70, 76 and 82. A `User{ID=ROOT}` principal never passes the filter at
   line 54. `CHAT-avduuqwp` carries the root on the key.

### `-` and `expire: now` do not answer the same

**An earlier version of this section said that they do. That was wrong.**
Measured on 2026-09-24 against the production `AuthSummarizer` at `500fa09f`,
for a `JOIN` row followed by a second row.

| Second row | The evaluator answers |
|---|---|
| A live `-` row | `[JOIN, -]` |
| An expired `*` row | `[]` |

`-` is an ordinary permission string. Only `*` expands, so a `-` row groups
under `-` and it never reaches the `JOIN` group. The `JOIN` grant stands, and
the `-` row stands beside it as a permission that no operation asks for.

So `-` still means nothing, exactly as item 5 of
`What the current code does not support` records.

`CHAT-zcxgrtqc` must state the rule for `-`, or remove the spelling. A
normalization that turned `-` into an expired row would have to say so.
