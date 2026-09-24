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
3. **`*` is not a wildcard.** `AuthMetadataAccessBroker` line 19 reads
   `permissions.contains(perm)`, a literal list check. No main source file
   expands a wildcard. So the existing `Admin` grant and the `*` that
   `TopicCommands.addTopic` writes match no operation.
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

This also corrects the section `Order decides the outcome` above. That
section says that nothing subtracts. An expired last row subtracts today.

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
- **For any other caller**, the actor filter removes the creator row, because
  that principal is not in the actor set. The close row is last, it expired,
  and line 63 drops it. That caller holds nothing.

**The comparator carries the whole decision.** It is the one place that keeps
ownership through a close.

### What the code must gain

Three changes. Each one is measured at master `579a23ba`.

1. **`*` must expand to the permission set during evaluation.**
   `AuthSummarizer` groups by the permission string, so a `*` row lands in a
   group named `*`. It cannot replace a `JOIN` row. **This is the one reason
   the close row does nothing today.**
2. **The comparator must sort by principal specificity first, and then by
   time.** `AuthSummarizer` takes the comparator from its caller, and the
   shipped comparator reads `key.id`. `CHAT-ojbgbznh` carries the clock for
   the time part.
3. **The actor set must hold the domain root.** `CoreAuthorizationService`
   builds that set from the anonymous key, the caller and the target, at lines
   63, 70, 76 and 82. A `User{ID=ROOT}` principal never passes the filter at
   line 54. `CHAT-avduuqwp` carries the root on the key.

### `-` and `expire: now` answer the same

Under replacement, a live `-` row and an expired row both leave the empty set.
So this draft holds two spellings of one outcome. They differ in what they
record, and not in what they answer.

`CHAT-zcxgrtqc` must keep one spelling, or state the difference.
