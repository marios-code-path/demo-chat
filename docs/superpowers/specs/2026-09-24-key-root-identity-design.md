# Root identity on keys: design for review

Issue `CHAT-avduuqwp`. Status: **draft for owner review. No code changed.**

This issue blocks `CHAT-rfzsnbco`, the two-target scan. That scan blocks the
closure work in `CHAT-ylfxsthp`.

The owner proposed `Key<T>` with `root_id: T` in the PR #134 review. This
document treats that as a proposal. It measures the current state first, then
compares three options.

## The question the target scan asks

A check names one target key. The scan must also read the grants on the domain
root of that target. So the check must answer one question:

**Which domain root covers this target key?**

The answer must be available at check time. It must not come from caller input
that the server has not verified.

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
  nothing.

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

## Three options

### A. `root_id: T` on `Key` (the owner proposal)

The key carries the id of its domain root. A check reads it with no store
read.

- **Needs stable root keys first.** Finding 6 means a stored `root_id` goes
  stale at the next start of the creating node. Root keys must be written once
  and read back after that.
- **Every construction site must carry it.** That is 63 `funKey` sites, every
  `MessageKey.create`, and every deserializer.
- **Every store must carry it, or derive it.** Old Redis JSON and old Cassandra
  rows do not hold it.
- **A key from a client carries a root that the server cannot trust.** A caller
  can label a message key as a topic key. So the server must check the value
  against its own record. The record exists already, as finding 2 shows.

### B. A fixed domain name on `Key`

The key carries a domain name from one registry, not a root key id. For
example a string or an enum.

- Removes the stability problem, because a name is a constant.
- Keeps every other cost of option A: 63 sites, the wire change, the stores,
  and the trust problem.

### C. Resolve the domain on the server, with no change to `Key`

At check time the server reads the domain from its own key registry:
`IKeyService.kind(key)`, then `RootKeys` by that name.

- **No wire change and no storage schema change.**
- **No caller input is trusted.** The registry is the record of the server.
- **One registry read per checked target.** A key never changes domain, so a
  cache is safe.
- **The names must agree first.** One registry of domain names must serve both
  the mint sites and `RootKeys`. Finding 5 lists the drift that must be
  repaired. The rows already written with a drifted name need a mapping or a
  rewrite.
- **It does not need stable root keys.** The registry answers a name, and
  `RootKeys` answers the root key of the current start.

## Recommendation

**Option C first.** It answers the question of the target scan. It needs no
stable roots, no wire change and no trust in caller input.

**Option C does not follow the issue text exactly.** The issue says to replace
the kind relationship and to add no backwards compatibility for it. Option C
formalizes that relationship as the resolver, and it repairs the names. That
choice is the owner's.

Option A or B stays available as a later step. The issue also asks to route by
root identity and to resolve a key to its owning service. A client that routes
a key without a server round trip needs the domain on the key. The target scan
does not.

## Decisions for the owner

1. **Option A, B or C.**
2. **Must domain root keys be stable across a restart?** Option A needs it. It
   also closes the grant leak in finding 6. It can be its own issue.
3. **Does the server trust a domain carried on an inbound key?** For A and B,
   this document recommends never. The server checks the value against its
   registry.
4. **One registry of domain names.** `KnownRootKeys` is the nearest existing
   list. Decide the names for `MessageKey`, `TopicMembershipByKey`,
   `KeyCredential` and `Key`. Each is either mapped to a domain or refused at
   mint.

## Tests the chosen option needs

These come from the issue. Each option satisfies them in its own way.

- A new key resolves to the expected domain root.
- Memory, Redis and Cassandra store and recover the domain.
- A wrong domain is refused.
- The domain resolves to the expected typed service.
- No production path compares a domain name as free text.

For option A or B, add:

- The domain survives JSON and RSocket serialization.
- A payload without the domain is accepted or refused, as decided.
- The Cassandra integration covers the new column or type.

For option C, add:

- Every mint site stores a name that `RootKeys` holds. One test reads every
  `key(Class)` call site.
- A key that the registry does not know denies at the check.

## Not measured

- A restart of a node with `app.rootkeys.create=true`. Finding 6 is read from
  source.
- The cost of one registry read per checked target, on any backend.
- Whether any client decodes a key with a reader stricter than the two
  deserializers named in finding 7.
