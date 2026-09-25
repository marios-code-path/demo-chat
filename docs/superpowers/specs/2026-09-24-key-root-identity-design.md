# Root identity on keys: design for review

Issue `CHAT-avduuqwp`. Status: **second revision, after the owner chose a root
on the minted key on 2026-09-25. No code changed.**

This issue blocks `CHAT-rfzsnbco`, the two-target scan. That scan blocks the
closure work in `CHAT-ylfxsthp`. It now also holds `CHAT-bafkgkko`, stable
root keys.

## Owner decisions

On 2026-09-24:

1. The server does not trust a domain that a caller asserts.
2. One typed domain registry. `MessageKey` maps to `Message`.
   `TopicMembershipByKey` maps to `TopicMembership`. `KeyCredential` and the
   generic `Key` stay unresolved until each has an ownership contract.

On 2026-09-25, replacing the choice of option C as the first stage:

3. **A minted key carries its root key.** This is option A.
4. **The key service owns the root keys and their state.** It creates them
   once, stores them, and reads them back on every later start. This folds
   `CHAT-bafkgkko` into this issue.
5. **The root travels on the wire, and the server verifies it.** A client can
   read the root. The server never uses an inbound root without a check.
6. **`kind` is eliminated.** The class name relationship goes away in code, in
   schema and in stored data. See part 10.

## What changed from the first two versions

- Option C, resolution on the server with no change to `Key`, is no longer the
  first stage. Its validation rules stay, because option A needs them too.
- Stable roots were a separate prerequisite. They are now part of this design,
  because the key service owns the root state.

## The question the target scan asks

A check names one target key. The scan must also read the grants on the domain
root of that target. So the check must answer one question:

**Which domain root covers this target key?**

With this design, a key that the server minted or read from its own store
answers from its own `root` field. A key that crossed a trust boundary answers
only after the server verifies it.

## Measured state

Read from source at master `3527e7cb` on 2026-09-24. Nothing below was run
unless the line says so.

### 1. A key carries no domain

`Key<T>` holds `id` and `empty`. `MessageKey<T>` adds `from` and `dest`.
Equality compares `id` alone.

### 2. The key service records the domain beside the id

`IKeyService.key(kind: Class<S>)` mints a key and stores `kind.simpleName`
for it.

| Backend | Where the domain name is stored |
|---|---|
| Memory | `KeyServiceInMemory.kindMap`, an id to name map |
| Redis | the hash `chat:keys`, id to name |
| Cassandra | the `kind` column of the `keys` table |

Every persistence store mints through this path.

### 3. Nothing in production reads the domain back

`IKeyService.kind(key)` has no production caller. Two tests call it.

### 4. Nothing compares a stored name with a root key name

`RootKeys` maps a class simple name to a root key. No production code compares
a stored name with it.

### 5. The stored names drift from the root key names

| Mint site | Name stored | Name in `RootKeys` |
|---|---|---|
| `MembershipPersistenceCassandra.key` | `TopicMembershipByKey` | `TopicMembership` |
| `MembershipPersistenceRedis.key` | `TopicMembership` | `TopicMembership` |
| `PubSubRestMapping.sendRestMessage` | `MessageKey` | `Message` |
| `MessagePersistence*.key` | `Message` | `Message` |
| `SecretsRestMapping` | `KeyCredential` | none |
| `UserCommands` in `chat-shell` | `Key` | none |

### 6. The domain root keys are not stable across a restart

With `app.rootkeys.create=true`, `RootKeysSupplier` calls `keyService.key(k)`
for each class in `KnownRootKeys` on every start. So each start mints six new
root keys. Other nodes take them from consul kv or from `/actuator/rootkeys`.

`Admin` and `Anon` are users. On a restart `InitialUsersService` finds the
existing user, so those two keys are stable.

`InitialUsersService` writes the shipped grants again on each start, against
the new roots. The earlier rows stay in the store and reach nothing. A grant
written at run time on a domain root does not survive a restart. Read from
source, not observed.

### 7. The wire shape accepts an added field

A key serializes as `{"key":{"id":…}}`. A `MessageKey` adds `from` and `dest`.
`KeyDeserializer` (Jackson 2) and `KeyAssembly` (Jackson 3) read named fields
and ignore the rest. `DomainWireShapeTests` and `E2eeWireShapeTests` pin the
current shapes.

### 8. Each store rebuilds keys from ids

| Store | How a key is stored |
|---|---|
| Cassandra | 19 tables per keyspace. Entity tables hold id columns, and the code rebuilds keys with `Key.funKey`. |
| Redis | Entities are Jackson JSON. |
| Lucene | `key.id` as text in `key`, and as an exact term in `_key`. |
| Memory | Objects in maps. |

### 9. Keys are built in many places

`Key.funKey(` appears 63 times in main source across 13 modules.

### 10. Two inbound routes let a client name the domain at mint

- `IKeyRestMapping.restKey` calls `key(Class.forName(req.kind))`. The client
  names the class, and `Class.forName` loads any class on the classpath that
  the client names. `IKeyController` implements this route.
- `IKeyServiceMapping.key` takes a `Class<S>` from an RSocket payload.
  `KeyServiceController` implements this route.
- No production class implements `IKeyServiceAccess`, so nothing checks either
  route today.

### 11. Both stores already create a row only when it is absent

`CassandraNodeIdClaimStore` uses `IF NOT EXISTS`. `RedisNodeIdClaimStore` uses
`setIfAbsent`. The root key state can use the same pattern.

### 12. Two more `kind` columns, and both are dead

Measured on 2026-09-25 at master `3527e7cb`, from source.

- `event_key_meta` is a Cassandra user defined type with one field, `kind`. It
  is declared in `keyspace-long.cql` and `keyspace-uuid.cql`. **No code
  references it.**
- `chat_secret.kind` maps to `CredKey.kind`. `CredentialSecretsStoreCassandra`
  writes the constant `"CRED"`, and `findByKeyId` never reads it. Two
  repository tests write `"CREDENTIAL"`.

The vector document metadata also has a field named `kind`, with the value
`"message"`, in `MessageDocumentMapper` and the recall filters. That is a
different concept, and this issue does not touch it.

## Design

### Part 1: the typed domain registry

One registry names every domain and its accepted mint classes. It replaces
`KnownRootKeys` as the list of domains.

| Domain | Accepted mint classes |
|---|---|
| `User` | `User` |
| `Message` | `Message`, `MessageKey` |
| `MessageTopic` | `MessageTopic` |
| `TopicMembership` | `TopicMembership`, `TopicMembershipByKey` |
| `AuthMetadata` | `AuthMetadata` |
| `KeyValuePair` | `KeyValuePair` |

`KeyCredential` and the generic `Key` have no domain.

### Part 2: the key service owns the root keys

The key service holds one root key per registry domain.

1. **On start**, the key service reads the stored root key of each domain.
2. **When a domain has none**, it creates one with a conditional write. Two
   nodes that start at once agree, because only one write succeeds and both
   read the winner.
3. **`RootKeys` becomes a read view** of that state. Nothing else writes a
   domain root.
4. **`Admin` and `Anon` stay users.** `InitialUsersService` keeps finding them.
   They are identities, not domains, so the registry does not list them.

Storage per backend:

| Backend | Root key state |
|---|---|
| Redis | a hash, domain to root id, written with `HSETNX` |
| Cassandra | a table keyed by domain, written with `IF NOT EXISTS` |
| Memory | a map in the process. **It is not stable across a restart**, because the whole store is not. |

**`app.rootkeys.create` changes meaning.** It no longer mints on every start.
A node that reaches the store reads or creates the roots. The kv and HTTP
publish paths stay for a process that does not reach the store, such as the
shell. Decision 2 below asks whether to keep the property at all.

### Part 3: the key carries its root

- `Key<T>` gains `root: T?`. `MessageKey<T>` carries it too.
- **The root of a domain root key is its own id.**
- **A key with no domain has `root = null`.** That covers `KeyCredential`, the
  generic `Key`, and every key built with `Key.funKey(id)` from an id alone.
- **Equality stays on `id` alone.** A key read with its root and the same key
  read without it must be equal, or every map and set keyed by `Key` splits.

### Part 4: mint

`IKeyService.key(kind)` does four things.

1. Map `kind` through the registry. Refuse an unlisted class.
2. Read the root of that domain from the key service state.
3. Write the registry row with the root id, not the class name.
4. Return the key with `root` set.

For `KeyCredential` and the generic `Key`, the key service writes the row with
no root and returns `root = null`. Decision 3 below asks whether to refuse
these mints instead.

`restKey` maps the request name through the registry, and it never calls
`Class.forName`. The RSocket `key` route applies the same registry check.

### Part 5: the wire

A key serializes as `{"key":{"id":…,"root":…}}`. A key with `root = null`
leaves the field out.

Both deserializers read `root` when it is present. A payload without it
decodes to `root = null`. So an old client reads a new payload, and a new
server reads an old payload.

`DomainWireShapeTests` and `E2eeWireShapeTests` change. A wire change makes
the shell integration image stale, so the gate for this work is
`build-health.sh --ci`.

### Part 6: verification

**An inbound root is a claim, not a fact.** It arrives in a request body, an
RSocket payload or a path, and the caller controls it.

- Before an inbound key reaches authorization or persistence, the server reads
  the registry row of its id.
- **A root that differs from the row is refused.** A key with no row is
  refused.
- **A missing root is filled from the row.** A client that does not know the
  root is not an attacker.

**This keeps one registry read for every key that crosses a trust boundary.**
The root on the key saves that read for a key that the server minted in the
same process, and for a key that a typed store read from its own table. It
does not remove the read for inbound keys.

### Part 7: persistence

A typed store checks the root of every key it writes.

- `add(entity)` refuses an entity whose key root is not the root of the store
  domain.
- The key reached `add` either from mint in the process, so its root is set by
  the server, or from verification in part 6.

A typed store stamps the root on the keys it reads. A table holds one domain,
so the root comes from the store domain and no column is needed.

**Two stores hold keys of any domain.** `AuthMetadata` rows hold a principal
and a target. The key-value store holds any value type. These need a stored
root, or a registry read when they are read back. Decision 4 below asks which.

Before part 7 is enforced, every `add` path must be measured. A path that
builds a key from caller input with `Key.funKey` would be refused. That list
does not exist yet.

### Part 8: the check

The target scan reads `target.root`.

- A verified or server built key carries its root. The scan reads the grants on
  that root and on the target.
- **A key with `root = null` has no domain root, and the domain root grants do
  not apply to it.** The target grants still apply.

### Part 9: migration

The first start after this change finds no root key state. It creates the
roots once. Two things follow.

- **Every root minted by an earlier start is orphaned.** The grants that name
  those roots stop reaching anything. `InitialUsersService` writes the shipped
  grants against the new roots, so the shipped matrix survives. A run time
  grant on an old root is lost. That is already true after every restart today.
- **Registry rows written before this change hold class names.** Part 10
  rewrites them once.

### Part 10: `kind` is eliminated

The owner decided on 2026-09-25 that the class name relationship goes away.
Nothing maps a class name after the first start.

| Where | Today | After |
|---|---|---|
| `IKeyService.kind(key)` | answers the class name, and no production code calls it | removed. `root(key)` replaces it. |
| `IKeyService.key(kind: Class<S>)` | records `kind.simpleName` | maps the class through the registry and records the root id |
| Memory registry | `kindMap`, id to class name | id to root id |
| Redis registry | hash `chat:keys`, id to class name | id to root id |
| Cassandra `keys.kind` column, `CSKey.kind` | class name | replaced by a `root` column |
| `KindRequest(kind)` on `restKey` | a class name from the client | a domain name, mapped through the registry |
| `event_key_meta` type | dead | removed from both keyspace scripts |
| `chat_secret.kind` column, `CredKey.kind` | the constant `"CRED"`, never read | removed |

**The one-time rewrite.** At the first start after this change, the key
service reads each registry row once.

1. A row that holds a registry class or alias takes the root id of that
   domain.
2. A row that holds `KeyCredential`, `Key` or any other name takes no root.
3. The aliases exist only inside this rewrite. After it, no code maps a class
   name.

The rewrite must finish before the node serves a request. It must be safe to
run on two nodes at once, because each row maps to one value whatever node
writes it.

**A Cassandra column removal needs a schema step.** The keyspace scripts create
the schema for a new store. An existing store needs `ALTER TABLE` to add `root`
and to drop `kind`. The rewrite must run between those two steps.

## Decisions for the owner

1. **Approve the design above**, or name the part to change.
2. **`app.rootkeys.create`.** Remove it, because the key service always reads
   or creates the roots? Or keep it as a guard, so only a named node may create
   them? This document recommends keeping it as a guard. A node without it that
   finds no roots fails at start.
3. **`KeyCredential` and generic `Key` mints.** Record them with no root, as
   this document recommends, or refuse them until each has an ownership
   contract.
4. **Keys of any domain inside a row.** Store a root column beside each such
   key in `AuthMetadata` and the key-value store, or read the registry when the
   row is read back. This document recommends the column for `AuthMetadata`,
   because the target scan reads those rows on every check.
5. ~~**Registry rows with class names.**~~ **Closed on 2026-09-25.** The owner
   eliminated `kind`, so a map on read is not allowed. Part 10 rewrites the rows
   once.
6. **How an existing Cassandra store gets the schema change.** No migration
   tool exists in this repository. The keyspace scripts create a store, and
   they do not alter one. Decide between an `ALTER TABLE` step run by the key
   service at start, and an operator script.

## Tests

- The registry lists every domain, and every alias maps to its domain.
- Two key service instances started against one empty store create one root
  per domain, and both read the same roots.
- A restart reads the stored roots and creates none.
- Mint sets `root` for each listed class. A root key has itself as its root.
- Mint refuses an unlisted class. `restKey` refuses an unknown name and loads
  no class.
- A key serializes with `root`, and a payload without `root` decodes to
  `root = null`. Both Jackson generations.
- A key with and without `root` are equal and hash alike.
- An inbound key with a wrong root is refused. An inbound key with no root is
  filled from the registry. An inbound key with no registry row is refused.
- A typed store refuses a key whose root is another domain root.
- A key with `root = null` gets no domain root grant.
- A removed key fails verification on memory, Redis and Cassandra.
- The Cassandra integration covers the root key table and any new column.
- The one-time rewrite maps each alias to its domain root, maps an unknown name
  to no root, and gives the same result when two nodes run it at once.
- No production source names `kind` for a key after this change. One test reads
  the source tree and fails on `IKeyService.kind`, `CSKey.kind`, `CredKey.kind`
  or `KindRequest.kind`.

## Not measured

- A restart of a node with `app.rootkeys.create=true`. Finding 6 is read from
  source.
- The cost of one registry read per inbound key, on any backend.
- The list of `add` paths that build a key outside `key()`.
- Whether any client decodes a key with a reader stricter than the two
  deserializers in finding 7.
- Whether a Kafka or Redis pub/sub payload carries keys that bypass the two
  deserializers.
