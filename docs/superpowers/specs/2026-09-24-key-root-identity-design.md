# Root identity on keys: design for review

Issue `CHAT-avduuqwp`, with its child `CHAT-bafkgkko`. Status: **approved
on 2026-09-25. No code changed.**

This issue blocks `CHAT-rfzsnbco`, the two-target scan. That scan blocks the
closure work in `CHAT-ylfxsthp`.

## Owner decisions

1. **`Key<T>` is `{ id: T, root: T }`.** The root is required. No key exists
   without one.
2. **The key service owns the root keys and their state.**
3. **The root travels on the wire, and the server verifies it.** The server
   never trusts a root that a caller asserts.
4. **Mint takes a typed domain identifier.** The class name registry and its
   aliases are removed. `kind` is eliminated.
5. **A mint for a type with no domain contract is refused.** That covers
   `KeyCredential` and the generic `Key` until each has a contract.
6. **`AuthMetadata` stores the verified roots of its principal and its target.**
7. **No migration and no compatibility.** The fresh store schemas change
   directly. There is no rewrite, no backfill, and no reader for a payload
   without a root. Development data that does not match must be recreated.
8. **Stable roots still matter after initialization.** Concurrent creation,
   restart recovery, key type isolation, and protection against root deletion
   are required.

## The question the target scan asks

A check names one target key. The scan must also read the grants on the domain
root of that target. With this design the key answers from its own `root`,
once the server has verified that root.

## Measured state

Read from source at master `3527e7cb` on 2026-09-24 and 2026-09-25. Nothing
below was run.

1. **A key carries no domain.** `Key<T>` holds `id` and `empty`.
   `MessageKey<T>` adds `from` and `dest`. Equality compares `id` alone.
2. **The key service records a class name beside the id**, in a memory map, the
   Redis hash `chat:keys`, and the Cassandra `keys.kind` column.
   `IKeyService.kind(key)` has no production caller.
3. **The recorded names drift** from the `RootKeys` names at four mint sites:
   `TopicMembershipByKey`, `MessageKey`, `KeyCredential` and `Key`.
4. **The domain root keys are minted again on every start** with
   `app.rootkeys.create=true`. `Admin` and `Anon` are users, and they are
   stable. A grant written at run time on a domain root does not survive a
   restart.
5. **Two inbound routes let a client name the class at mint.**
   `IKeyRestMapping.restKey` calls `Class.forName(req.kind)`, which also loads
   any class the client names. `IKeyServiceMapping.key` takes a `Class` from an
   RSocket payload. No production class implements `IKeyServiceAccess`, so
   nothing checks either route.
6. **Both stores already create a row only when it is absent**, for the node id
   lease: `IF NOT EXISTS` in Cassandra, `setIfAbsent` in Redis.
7. **The Redis key registry is not separated by key type.** `chat:keys` has no
   key type segment. The node id lease uses `chat:nodeclaim:<keyType>:…`. So a
   `long` and a `uuid` deployment on one Redis share the key registry today.
8. **`node_claim` is absent from the truncate scripts on purpose.** A test
   cleanup must not delete a live lease.
9. **Two dead `kind` columns.** The Cassandra type `event_key_meta` has no code
   reference. `chat_secret.kind` holds the constant `"CRED"`, and nothing reads
   it.
10. **Keys are built in many places.** `Key.funKey(` appears 63 times in main
    source across 13 modules. `MessageKey.create(` appears 12 times.
    `Key.emptyKey(` appears twice: `InitialUsersService` and `TopicCommands`.
11. **The wire shape is `{"key":{"id":…}}`.** `DomainWireShapeTests` and
    `E2eeWireShapeTests` pin it.

## Design

### Part 1: typed domains

A closed type names every domain. For example:

```kotlin
enum class ChatDomain { USER, MESSAGE, MESSAGE_TOPIC, TOPIC_MEMBERSHIP, AUTH_METADATA, KEY_VALUE_PAIR }
```

- Mint takes a `ChatDomain`. It never takes a `Class` or a free text name.
- `RootKeys` is keyed by `ChatDomain`.
- A `@PreAuthorize` expression that names a domain as text, such as
  `hasAccessToDomain('User', …)`, parses it into a `ChatDomain`. An unknown
  name denies. It never reaches a map lookup as text.
- `Admin` and `Anon` are identities, not domains. `RootKeys` exposes them
  through their own typed accessors. Their root is the `USER` root, because
  each is a user.
- `KeyCredential` and the generic `Key` have no `ChatDomain`. A mint for either
  is refused. The live sites are `SecretsRestMapping` and `UserCommands` in
  `chat-shell`. Both fail until each type has a domain contract.

### Part 2: the non-null key contract

```kotlin
interface Key<T> {
    val id: T
    val root: T
}
```

**Every key has a root.** Five rules follow.

1. **A root key is its own root.** `root == id`.
2. **Equality compares `id` and `root`.** A key with the right id and a wrong
   root is not equal to the real key. That surfaces a forged root at every map
   and set, rather than merging it with the real key.
3. **`Key.funKey(id)` is removed.** The factory takes both values. There is no
   default root.
4. **An empty key carries the root of its intended domain.** `emptyKey` has two
   live sites, and each names the domain of the key it stands for.
5. **`MessageKey` has a root, the `MESSAGE` root.** Its `from` and `dest` stay
   raw ids.

**Where a root comes from.** Each of the 63 `funKey` sites, 12
`MessageKey.create` sites and 2 `emptyKey` sites falls into one of four
sources.

| Source | The root comes from | Trusted |
|---|---|---|
| Mint | the key service, for the requested `ChatDomain` | yes |
| A typed store or index read | the domain of that store or index. A table holds one domain. | yes |
| A raw id whose domain is fixed by its position | the root of that domain, for example `MessageKey.dest` is a topic | only when the id came from a trusted source |
| A raw id from a caller, such as a path segment or a request field | the key registry, by id | yes, after the lookup. An unknown id is refused. |

A site that fits none of the four is a defect. The implementation plan must
list all 77 sites and name the source of each before the factory changes.

### Part 3: the key service owns the roots

**State.** One root id per `ChatDomain`, per key type, per store.

| Backend | Root state | Key registry |
|---|---|---|
| Cassandra | table `root_keys (domain text PRIMARY KEY, id <T>)` in each keyspace | `keys (id, root)` |
| Redis | hash `chat:rootkeys:<keyType>`, domain to id | hash `chat:keys:<keyType>`, id to root |
| Memory | a map in the process | a map in the process |

**Concurrent creation.** On start the key service reads the root state. For a
domain with no root it generates an id and writes it with a conditional write:
`IF NOT EXISTS` in Cassandra, `HSETNX` in Redis. Then it reads the state again
and uses what it finds. Two nodes that start at once use the one root that won.

**Restart recovery.** A restart reads the stored roots and creates none. The
roots are the same before and after, so a grant on a root survives. The memory
backend is the exception, because its whole store is lost at a restart.

**Key type isolation.** Cassandra separates key types by keyspace already.
Redis carries the key type in both hash names. This also repairs finding 7,
which the key registry shares today. A `long` deployment and a `uuid`
deployment on one Redis each have their own roots and their own registry.

**Protection against root deletion.**

- `IKeyService.rem` refuses a key whose id is a root.
- No application code deletes or updates root state after creation.
- `root_keys` is absent from the truncate scripts, for the same reason as
  `node_claim`.
- A root is verified from the root state, not from the key registry. So a
  truncated `keys` table does not break the roots.

**Initialization order.** The key service loads or creates the roots before
any other bean asks for `RootKeys`. A node that cannot read or create the roots
fails at start. It never serves with a partial set.

**`app.rootkeys.create` is removed.** A node that reaches the store loads or
creates the roots. The kv and HTTP publish paths stay for a process that does
not reach the store, such as the shell.

### Part 4: mint

`IKeyService.key(domain: ChatDomain)`:

1. Read the root of `domain` from the root state.
2. Generate an id.
3. Write the registry row `id → root`.
4. Return `Key(id, root)`.

`restKey` takes a `ChatDomain` in its request body. An unknown value fails
deserialization, so no class loads. The RSocket `key` route takes a
`ChatDomain` too.

### Part 5: the wire

A key serializes as `{"key":{"id":…,"root":…}}`. **`root` is required.** A
payload without it fails to decode. `DomainWireShapeTests` and
`E2eeWireShapeTests` change. The shell image must be rebuilt, so the gate is
`build-health.sh --ci`.

### Part 6: verification of inbound keys

**An inbound root is a claim.** Before an inbound key reaches authorization or
persistence, the server reads the registry row of its id.

- A root that differs from the row is refused.
- A key with no row is refused, unless its id is a root in the root state and
  its root equals its id.

This costs one registry read for every key that crosses a trust boundary. A
key that the server minted, or that a typed store read, needs no read.

### Part 7: persistence

- A typed store refuses a key whose root is not the root of its domain.
- A typed store stamps its domain root on every key it reads. No per-row root
  column is needed where the table holds one domain.
- **`AuthMetadata` stores `principal_root` and `target_root`**, because a grant
  names keys of any domain. `authorize` verifies both roots before it writes.
  The target scan reads `target_root` from the row with no registry read.
- The key-value store holds values of any type, but its own keys are all in
  `KEY_VALUE_PAIR`. So it needs no extra column.

Every `add` path must be listed before part 7 is enforced. A path that builds a
key from caller input without verification is a defect.

### Part 8: schema, and recreation of development data

The fresh store schemas change directly. There is no migration step.

| File | Change |
|---|---|
| `keyspace-long.cql`, `keyspace-uuid.cql` | `keys (id, root)` replaces `keys (id, kind)`. `root_keys` is added. `auth_metadata` and its two index tables gain `principal_root` and `target_root`. `event_key_meta` and `chat_secret.kind` are removed. |
| `truncate-long.cql`, `truncate-uuid.cql` | `root_keys` stays absent. |
| Redis | `chat:keys:<keyType>` and `chat:rootkeys:<keyType>` replace `chat:keys`. |

**A store with incompatible development data must be recreated.** The key
service checks this at start and fails with a message that says so. The check:

- Cassandra: the `keys` table has no `root` column, or `root_keys` is absent.
- Redis: the hash `chat:keys` exists without a key type segment.

**The check only detects the old shape. It does not repair it.**

### Part 9: `kind` is eliminated

| Where | After |
|---|---|
| `IKeyService.kind(key)` | removed |
| `IKeyService.key(kind: Class<S>)` | replaced by `key(domain: ChatDomain)` |
| `KindRequest(kind)` | replaced by a `ChatDomain` field |
| `CSKey.kind`, `keys.kind` | replaced by `root` |
| `CredKey.kind`, `chat_secret.kind` | removed |
| `event_key_meta` | removed |
| `KnownRootKeys` | replaced by `ChatDomain` |

The vector document metadata field `kind: "message"` is a different concept,
and this issue does not touch it.

## Decisions for the owner

**All closed on 2026-09-25.** The owner approved the design direction at
`054db2d0`.

1. **Approved.**
2. **`app.rootkeys.create` is removed.** Conditional creation makes the guard
   unnecessary for a node that reaches the authoritative store. Such a node
   loads the complete root set before it serves a request.
3. **Equality compares `id` and `root`.** Confirmed.

Closed earlier: rootless mints are refused. `AuthMetadata` stores both roots.
No migration and no compatibility.

## Requirements for the implementation plan

The owner added these on 2026-09-25.

1. **Equality is consistent across every key implementation.** That includes
   `ChatMessageKey`, the Cassandra key classes, and every other `Key`
   implementation. `hashCode` matches `equals` in each. Empty key equality is
   defined explicitly.
2. **Equality does not verify a root.** Inbound keys are verified before
   authorization, and that includes the self authority shortcut in
   `AuthMetadataAccessBroker`. An unverified key must never reach
   `principal == target`.
3. **The inventory covers implementations, not only factory calls.** The 77
   sites leave out generated constructors, deserializers, and every class that
   implements `Key`. The plan lists all of them.
4. **An unsupported operation fails deliberately.** The credential and generic
   mint callers are replaced with an explicit refusal. The change leaves no
   compile failure and no accidental exception.
5. **The Cassandra start check reads the complete required schema.** That
   includes the authorization root columns in `auth_metadata` and in both of
   its index tables.

**Every construction path and every inbound path gets its root source before
any factory changes.**

## Tests

- Two key service instances on one empty store create one root per domain, and
  both read the same roots. Cassandra and Redis.
- A restart reads the stored roots and creates none.
- A `long` and a `uuid` key service on one Redis have separate roots and
  separate registries.
- `rem` refuses a root key. A truncate leaves the roots in place.
- Mint returns a key whose root is the root of its domain. A root key has
  itself as its root.
- Mint for `KeyCredential` or `Key` fails. `restKey` refuses an unknown domain
  value and loads no class.
- A key serializes with `root`. A payload without `root` fails to decode. Both
  Jackson generations.
- Two keys with one id and different roots are not equal.
- An inbound key with a wrong root is refused. An inbound id with no registry
  row is refused.
- A typed store refuses a key of another domain, and stamps its root on read.
- `authorize` refuses a grant with an unverified root. The target scan reads
  `target_root` from the row.
- The key service fails at start on a store with the old shape, and the message
  says to recreate it.
- No production source names `kind` for a key.

## Not measured

- A restart of a node, on any backend.
- The cost of one registry read per inbound key.
- The list of `add` paths, and the source of each of the 77 key construction
  sites.
- Whether a Kafka or Redis pub/sub payload carries keys that bypass the two
  deserializers.
