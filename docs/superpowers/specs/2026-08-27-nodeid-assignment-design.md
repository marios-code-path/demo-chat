# nodeId Assignment — Design

Stage 1 of `CHAT-koufkrsl`. Stage 2 is `CHAT-wyssrokr`.

## Goal

Make `app.nodeid` an explicit and validated deployment input. Remove every code path
that derives a node id or supplies one by default.

## Scope

In scope: the value of `app.nodeid`, its validation, and every launch surface that
must now carry it.

Out of scope: enforcement across deployments. Nothing in this design detects that two
deployments chose the same node id. That work is `CHAT-wyssrokr`.

## Background

A `Long` key is a Snowflake value. `SnowflakeGenerator` uses 1 unused bit, 41 epoch
bits, 10 node bits, and 12 sequence bits. Ten node bits give 1024 distinct values.

Two generators that share a node id, emit in the same millisecond, and hold the same
sequence value emit the same `Long`. Those values are entity keys. In a shared store
the result is a silent overwrite, not an error.

## Why UUID deployments carry the same risk

`UUIDKeyGenerator` does not make random values. It hashes the Snowflake `Long`:

```kotlin
override fun nextId(): UUID =
    UUID.nameUUIDFromBytes(idGenerator.nextId().toString().encodeToByteArray())
```

`UUID.nameUUIDFromBytes` is a name-based UUID. The UUID is a pure function of the
`Long`. So a `Long` collision becomes a UUID collision. Redis and memory both pass
the node id into `UUIDKeyGenerator`. Only the Cassandra UUID path ignores it, because
it uses `CassandraUUIDKeyGenerator`, which takes no argument.

## The three collision paths this design closes

1. **The default is a derivation, not a value.** All three `KeyGenConfiguration`
   classes read `@Value("\${app.nodeid:0}")`. `LongKeyGenerator` and `UUIDKeyGenerator`
   both map `0` to `SnowflakeGenerator()`, which derives. So an unset property
   silently switches to MAC-derived node ids. A node id of 0 also cannot be requested,
   because 0 and "unset" look the same.

2. **The derivation collides by birthday.** `createNodeId()` joins the MAC address of
   every network interface, takes `String.hashCode()`, and masks to 10 bits. Across
   1024 values a duplicate becomes more likely than not at about 38 hosts.

3. **The derivation collides for certain under containers.** Docker derives a
   container MAC from the container IP on the default bridge. Two containers that hold
   the same IP on different hosts derive the same MAC and the same node id. The common
   case is 172.17.0.2 on each host. This is the deployment topology in use.

## Decisions

| Decision | Choice | Reason |
|---|---|---|
| Staging | Assignment now. Store-side claim later. | Assignment closes the container collision at once. The claim needs its own design. |
| Which deployments | Every deployment that generates keys locally. | The requirement follows the code that reads the value. |
| Derivation | Delete it. | Any name for the derivation keeps the collision reachable. |
| Value 0 | Legal and explicit. | 0 stops being a sentinel once the default is gone. |
| Structure | One validated type in `chat-core`. | Three copies of one contract let the bad default sit unnoticed in all three. |

## The contract

`app.nodeid` is required for every deployment that activates a `KeyGenConfiguration`.

- The value is an integer from 0 to 1023 inclusive.
- 0 is legal and explicit.
- An unset value fails at startup.
- An empty or non-numeric value fails at startup.
- A value out of range fails at startup.
- There is no default and no derivation.

The Cassandra UUID configuration also receives the validated value. It does not use
it. The injection is deliberate and keeps one contract across all three backends. A
comment must record that the validation is intentional.

## Components

### New: `NodeId` in `chat-core`

A value type that holds one validated `Int`. It owns the range rule and the error
text. A pure `parse(raw: String?)` function does the work, so tests need no Spring
context.

A `@Configuration` class in `chat-core` supplies one validated `NodeId` bean. That
class reads `@Value("\${app.nodeid}")` with no default and calls `parse`.

Do not put that class on a component-scanned path. A globally scanned bean would exist
in every process, including processes that generate no keys, which contradicts the
scope of this design. Each of the three `KeyGenConfiguration` classes pulls it in with
`@Import` instead. The bean then exists exactly where a key generator is active, and
the rule and the message still live in one place.

### `SnowflakeGenerator`

Delete the no-argument constructor. Delete `createNodeId()`. Remove the
`java.net.NetworkInterface` and `java.security.SecureRandom` imports. Keep the
existing range `require(...)`, because it states the invariant of the class.

### `LongKeyGenerator` and `UUIDKeyGenerator`

Both hold this sentinel:

```kotlin
private val idGenerator: IKeyGenerator<Long> = when (nodeId) {
    0 -> SnowflakeGenerator()
    else -> SnowflakeGenerator(nodeId)
}
```

Delete the `when` in both classes. Pass the node id straight to
`SnowflakeGenerator(nodeId)`.

### The three `KeyGenConfiguration` classes

Replace the `@Value("\${app.nodeid:0}")` field with the injected `NodeId`. Add the
`@Import` described above. The files are:

- `chat-persistence-cassandra/src/main/kotlin/com/demo/chat/config/persistence/cassandra/KeyGenConfiguration.kt`
- `chat-persistence-redis/src/main/kotlin/com/demo/chat/config/persistence/redis/KeyGenConfiguration.kt`
- `chat-persistence-memory/src/main/kotlin/com/demo/chat/config/persistence/memory/KeyGenConfiguration.kt`

## Launch surface

`shell-scripts/chat-build` builds the launch flags. Its `main_flags()` emits
`-Dapp.key.type` today. It must also emit `-Dapp.nodeid`.

`shell-scripts/golden` holds 15 `.flags` files. `shell-scripts/test-flags.sh` compares
emitted flags against them. Every golden that gains the flag must be regenerated with
`./shell-scripts/test-flags.sh --update`.

`README-chat-build.md` states that a regenerated golden needs an explanation. The
commit message must give one.

**Emit the flag in all 15 goldens.** Twelve goldens name a key backend through
`app.service.core.key`. Three do not: `gateway-client`, `rest-client`, and
`authserv-client`. Those three are not proof that no key generator starts, because the
memory `KeyGenConfiguration` uses `matchIfMissing = true`. An absent selector still
activates it when the memory module is on the classpath. Emitting the flag everywhere
avoids a fragile classpath analysis. Where no `KeyGenConfiguration` is active, Spring
ignores the property.

## Failure mode

Every existing deployment meets this error on its first boot, so the text must say
what to do:

```
app.nodeid is required and has no default. Set it to an integer in 0..1023,
unique across every deployment that writes to this store. Got: <raw|unset>
```

The message must name the property, give the range, state that no default exists, and
show what it received.

## Testing

Unit tests for `NodeId.parse` cover an unset value, an empty value, whitespace, a
non-numeric value, a negative value, 0, 1023, and 1024.

A context test asserts that a deployment without `app.nodeid` fails, and that the
message names the property.

A second context test asserts that a deployment with a valid value starts.

Existing Spring tests gain the property wherever they activate a
`KeyGenConfiguration`. The repository holds 35 test classes that start a context. The
exact set that needs the property cannot be read from the source, because
`matchIfMissing = true` makes it a classpath question. Run the suite and let it name
them.

## Migration and risk

This is a breaking change with no grace period. `app.nodeid` is set nowhere today.
It appears only in the three `@Value` declarations. No yml file, no properties file,
no test, and no deploy config sets it. So every deployment currently derives its node
id, and every one of them must gain the property before it starts.

The size of the test change is not knowable by reading. This matches the
"Task 7 of the plan is unbounded" entry in the forward register. Run the full suite
and let it name the failures rather than predict them.

## Verified facts

Each line below was read from current source, not assumed.

- `SnowflakeGenerator()` derives a node id from network interfaces, and falls back to
  `SecureRandom`.
- `LongKeyGenerator(0)` means derive.
- `UUIDKeyGenerator(0)` means derive.
- `UUIDKeyGenerator` hashes the Snowflake `Long`, so a `Long` collision becomes a UUID
  collision.
- Redis and memory pass the node id into `UUIDKeyGenerator`.
- The Cassandra UUID path uses `CassandraUUIDKeyGenerator` and ignores `app.nodeid`.
- `app.nodeid` is set in no configuration file anywhere in the repository.
- `shell-scripts/chat-build` emits `-Dapp.key.type` and does not emit `-Dapp.nodeid`.
- 12 of the 15 goldens name a key backend.

## Correction carried into stage 2

An earlier note on `CHAT-koufkrsl` said a store-side claim fits an existing pattern,
because root keys already live in the store and are read at startup. That is not
correct. Root keys travel four ways. `app.rootkeys.create` makes them locally.
`publish.scheme=kv` and `consume.scheme=kv` use consul KV. `consume.scheme=http` reads
a peer actuator. All four are registry-scoped or peer-scoped. None is store-backed.

A store-side claim is therefore a new mechanism, not an extension of one. This does
not change the reasoning for stage 2, because only the shared store spans deployments.
It does change the cost. The correction is recorded on `CHAT-wyssrokr`.
