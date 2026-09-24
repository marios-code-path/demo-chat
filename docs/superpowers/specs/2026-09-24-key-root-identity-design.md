# Root identity on keys: design for review

Issue `CHAT-avduuqwp`. Status: **revised after the first owner review on
2026-09-24. No code changed.**

This issue blocks `CHAT-rfzsnbco`, the two-target scan. That scan blocks the
closure work in `CHAT-ylfxsthp`.

The owner proposed `Key<T>` with `root_id: T` in the PR #134 review. The first
version of this document measured the current state and compared three
options. The owner chose option C as the first stage and recorded four
decisions. This revision records them, corrects two claims, and states the
stage 1 contract.

## Owner decisions, 2026-09-24

1. **Option C is the first stage.** It does not complete the original scope.
   Routing by root identity and a domain on the key stay open.
2. **Durable authorization needs stable roots.** `CHAT-bafkgkko` tracks that
   work. Nothing here claims correctness across a restart until it is done.
3. **The server does not trust a domain that a caller asserts.**
4. **One typed domain registry.** `MessageKey` maps to `Message`.
   `TopicMembershipByKey` maps to `TopicMembership`. `KeyCredential` and the
   generic `Key` stay unresolved until each has an ownership contract.

## Two corrections to the first version

**1. The registry is not a trusted origin.** The first version said that option
C trusts no caller input, because the registry is the record of the server.
That was wrong. The registry records the class that the mint call named, and
a client can name that class. See finding 10. A registry entry alone does not
prove that an entity belongs to that domain.

**2. A cache is not safe by assertion.** The first version said a cache is safe
because a key never changes domain. That ignores `IKeyService.rem`. A removed
key must not keep resolving. The stage 1 contract below states the rules.

## The question the target scan asks

A check names one target key. The scan must also read the grants on the domain
root of that target. So the check must answer one question:

**Which domain root covers this target key?**

The answer must be available at check time. It must come from a record that
the server validated, not from caller input.

## Two separate problems

**Domain resolution** answers which domain a key belongs to. Option C gives
this answer after a restart, because the registry holds a name and a name does
not change.

**Grant continuity** keeps a grant that names a root key valid after a
restart. Option C does not give this. A grant stores the root key id, and the
next start holds a different root key id. See finding 6. `CHAT-bafkgkko`
owns this problem.

## Measured state

Read from source at master `3527e7cb` on 2026-09-24. Nothing below was run
unless the line says so.

### 1. A key carries no domain

`Key<T>` holds `id` and `empty`. `MessageKey<T>` adds `from` and `dest`.
Nothing on a key names its domain.

### 2. The key service records the domain beside the id

`IKeyService.key(kind: Class<S>)` mints a key and stores `kind.simpleName`
for it. All three backends do this.

| Backend | Where the domain name is stored |
|---|---|
| Memory | `KeyServiceInMemory.kindMap`, an id to name map |
| Redis | the hash `chat:keys`, id to name |
| Cassandra | the `kind` column of the `keys` table |

Every persistence store mints through this path. The memory store calls
`keyService.key(entityClass)`. The Redis and Cassandra stores name the class
directly.

### 3. Nothing in production reads the domain back

`IKeyService.kind(key)` has **no production caller**. Two tests call it:
`TestKeyServiceBase` and the Cassandra `KeyServiceTests`.

### 4. Nothing compares a stored name with a root key name

`RootKeys` maps a class simple name to a root key. The key service stores the
same kind of name. **No production code compares the two.** The string
matching that the issue names is a shared naming convention. It is not a
comparison that runs.

### 5. The stored names drift from the root key names

| Mint site | Name stored | Name in `RootKeys` |
|---|---|---|
| `MembershipPersistenceCassandra.key` | `TopicMembershipByKey` | `TopicMembership` |
| `MembershipPersistenceRedis.key` | `TopicMembership` | `TopicMembership` |
| `PubSubRestMapping.sendRestMessage` | `MessageKey` | `Message` |
| `MessagePersistence*.key` | `Message` | `Message` |
| `SecretsRestMapping` | `KeyCredential` | none |
| `UserCommands` in `chat-shell` | `Key` | none |

So a lookup by the stored name would fail today for a Cassandra membership and
for a message sent over REST.

### 6. The domain root keys are not stable across a restart

With `app.rootkeys.create=true`, `RootKeysSupplier` calls
`keyService.key(k)` for each class in `KnownRootKeys`, **on every start**. So
each start mints six new root keys: `User`, `Message`, `MessageTopic`,
`TopicMembership`, `AuthMetadata` and `KeyValuePair`. Other nodes take them
from consul kv or from `/actuator/rootkeys`.

`Admin` and `Anon` are different. They are users. On a restart
`InitialUsersService` fails to add the user again and finds the existing one,
so those two keys are stable.

Two consequences. Neither was observed with a restart.

- A `root_id: T` stored in a key, a row or a grant names a root that the next
  start does not hold.
- `InitialUsersService` writes the shipped grants again on each start, against
  the new roots. The rows from the earlier start stay in the store and reach
  nothing. A grant written at run time on a domain root does not survive.

### 7. The wire shape accepts an added field

A key serializes as a wrapper object: `{"key":{"id":…}}`. A `MessageKey` adds
`from` and `dest`. `KeyDeserializer` (Jackson 2) and `KeyAssembly` (Jackson 3)
read named fields and ignore the rest. So an old reader ignores an added field.
A new reader must accept a payload that does not carry it.

`DomainWireShapeTests` and `E2eeWireShapeTests` pin the current shapes. Any
change to the key on the wire changes both.

### 8. Each store rebuilds keys from ids

| Store | How a key is stored |
|---|---|
| Cassandra | 19 tables per keyspace. Entity tables hold id columns, and the code rebuilds keys with `Key.funKey`. |
| Redis | Entities are Jackson JSON, for example `UserPersistenceRedis.writeValueAsString`. |
| Lucene | `key.id` as text in `key`, and as an exact term in `_key`. |
| Memory | Objects in maps. |

### 9. Keys are built in many places

`Key.funKey(` appears 63 times in main source across 13 modules. The largest
counts are `chat-service-composite` 17, `chat-webflux` 9 and
`chat-index-cassandra` 9.

### 10. Two inbound routes let a client name the domain at mint

Found in the first owner review, and confirmed from source.

- **HTTP.** `IKeyRestMapping.restKey` reads `req.kind` from the request body
  and calls `key(Class.forName(req.kind))`. So the client names the class that
  the registry records. `Class.forName` also loads any class on the classpath
  that the client names, and that runs its static initializer.
  `IKeyController` implements this route.
- **RSocket.** `IKeyServiceMapping.key` takes a `Class<S>` from the payload.
  `KeyServiceController` implements this route.
- `IKeyServiceAccess.key` carries
  `@PreAuthorize("@chatAccess.hasAccessToDomainByKind(#kind, 'NEW')")`. **No
  production class implements `IKeyServiceAccess`.** So nothing checks either
  route today.

## Three options

### A. `root_id: T` on `Key` (the owner proposal)

The key carries the id of its domain root. A check reads it with no store
read.

- Needs stable root keys first. See finding 6 and `CHAT-bafkgkko`.
- Every construction site must carry it. That is 63 `funKey` sites, every
  `MessageKey.create`, and every deserializer.
- Every store must carry it, or derive it. Old Redis JSON and old Cassandra
  rows do not hold it.
- A key from a client carries a root that the server cannot trust. The server
  must check the value against a record that it validated.

### B. A fixed domain name on `Key`

The key carries a domain name from one registry, not a root key id.

- Removes the stability problem, because a name is a constant.
- Keeps every other cost of option A.

### C. Resolve the domain on the server, with no change to `Key`

At check time the server reads the domain from its own key registry, then the
root key from `RootKeys`.

- No wire change and no storage schema change.
- One registry read per checked target.
- **It is trustworthy only when mint and persistence are validated.** The
  stage 1 contract states both.
- It gives domain resolution after a restart. It does not give grant
  continuity.

## Stage 1 contract: option C

### The typed domain registry

One registry names every domain. It replaces `KnownRootKeys` as the list of
domains, and it holds the aliases.

| Domain | Accepted mint classes |
|---|---|
| `User` | `User` |
| `Message` | `Message`, `MessageKey` |
| `MessageTopic` | `MessageTopic` |
| `TopicMembership` | `TopicMembership`, `TopicMembershipByKey` |
| `AuthMetadata` | `AuthMetadata` |
| `KeyValuePair` | `KeyValuePair` |

`KeyCredential` and the generic `Key` have no domain. A key minted for either
resolves to no domain. The owner defines their ownership contracts later.

`RootKeys` takes its domain names from this registry. A free text name never
reaches `RootKeys`.

### Rule 1: validate at mint

- `IKeyService.key` accepts a class that the registry lists, and it records
  the domain, not the class name.
- Any other class is refused at mint. This covers the two inbound routes in
  finding 10.
- `restKey` maps the request name through the registry. It does not call
  `Class.forName`. An unknown name is refused before any class loads.
- The RSocket `key` route applies the same registry check.

The two unresolved classes, `KeyCredential` and `Key`, have live mint sites in
`SecretsRestMapping` and `chat-shell`. Stage 1 must decide per site: refuse
the mint, or record the key with no domain. This document recommends **record
with no domain**, so the site keeps working and the key always denies at a
domain check.

### Rule 2: validate at persistence

A registry entry records what the mint call named. It does not prove that an
entity belongs to that domain. So each typed store checks the key it receives.

- `add(entity)` resolves the key of the entity through the registry.
- The store refuses the entity when the resolved domain differs from its own
  domain.
- The store refuses a key that the registry does not know.

Rule 1 limits which domains exist. Rule 2 binds an entity to one of them.
Together they make the registry a validated record.

Before stage 1 enforces rule 2, it must measure every `add` path. A path that
builds a key with `Key.funKey` from caller input, rather than from `key()`,
would fail rule 2. That list does not exist yet.

### Rule 3: resolve at check time

`resolve(key)` reads the registry, then `RootKeys`. A key with no domain, an
unknown key and a removed key each resolve to nothing. **Nothing denies.**

### Rule 4: removal and caching

`IKeyService.rem` removes a registry entry. A removed key must resolve to
nothing on every node.

- **Stage 1 does not cache.** A cache on one node does not see a `rem` on
  another node.
- A later cache needs its own design. It must bound how long a removed key can
  still resolve, and it must show that ids are never reused. Snowflake and
  UUID ids are not reused by design, and nothing here measured that.
- The cost of one registry read per checked target is not measured. Measure it
  before any cache is proposed.

### Rule 5: rows that already exist

Registry rows written before stage 1 hold class names. The aliases map
`MessageKey` and `TopicMembershipByKey` on read, so no rewrite is needed.
Rows that hold `KeyCredential`, `Key` or another name resolve to no domain,
and they deny.

### What stage 1 does not deliver

- Grant continuity across a restart. `CHAT-bafkgkko` owns it.
- A domain on the key, and routing by root identity. These are options A or B,
  in a later stage.
- Ownership contracts for `KeyCredential` and the generic `Key`.

## Tests for stage 1

- Every registry domain has a root in `RootKeys`, and every alias resolves to
  its domain.
- A key minted for each listed class resolves to the expected root.
- A key minted for `KeyCredential` or `Key` resolves to no domain, and a
  domain check denies it.
- Mint refuses an unlisted class. `restKey` refuses an unknown name and loads
  no class.
- A typed store refuses an entity whose key resolves to another domain, and an
  entity whose key the registry does not know.
- A removed key resolves to no domain on memory, Redis and Cassandra.
- A row written with an old alias name resolves to its domain.
- No production path compares a domain name as free text.

## Not measured

- A restart of a node with `app.rootkeys.create=true`. Finding 6 is read from
  source.
- The cost of one registry read per checked target, on any backend.
- Whether ids are ever reused on any backend.
- The list of `add` paths that build a key outside `key()`.
- Whether any client decodes a key with a reader stricter than the two
  deserializers named in finding 7.
