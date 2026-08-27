# Domain Serialization: Drop the Dead Wrapper

Remove `@JsonTypeInfo(WRAPPER_OBJECT)` from the three domain types that declare no
subtype. Delete the redis rebind workaround. Update the REST contract tests.

Status: design, approved 2026-08-25. Issue: CHAT-gjggodpa.

## The problem

The chat domain interfaces carry `@JsonTypeInfo(WRAPPER_OBJECT)` and
`@JsonTypeName` uniformly. chat-core also registers a custom `JsonDeserializer`
for the same types. The two mechanisms overlap.

For `User`, `MessageTopic` and `TopicMembership` the wrapper carries no
polymorphism. None of the three declares a subtype. The wrapper feeds only
Jackson's own type machinery.

The round trip is asymmetric. A `User` serialised at root writes
`{"user":{...}}`. As the `data` of a `KeyValuePair` it goes through the erased
type parameter `E` and is written flat. Reading the flat form back makes Jackson
read the first property name as a type id and fail.

`KeyValuePersistenceRedis.rebind` hides that failure. It re-applies the declared
wrapper before `convertValue`. The REST path makes the asymmetry systematic.
`KVRequest.data` is `Any`. Every domain object posted over REST is stored flat.
In-process writes keep the wrapper. Two writers, two formats, one store.

## Decisions

| # | Decision |
|---|----------|
| 1 | Drop the annotation from the three types with no subtypes |
| 2 | The store is disposable: flush it, no migration, no compat shim |
| 3 | Hard cut: no compat period, update the in-repo tests |
| 4 | CHAT-ejhtsarb stays a separate issue |
| 5 | The six E2EE types defer to a follow-up issue |

## Design

### Keep the wrapper where it is load-bearing

`Key`, `Message` and `KeyValuePair` declare subtypes. Their wrappers are
load-bearing. `KeyDeserializer` unwraps `node.get("key").get("key")`. The
subtype ids select the concrete class. Keep the annotations on these three.

### Drop the wrapper where it is dead weight

Remove `@JsonTypeInfo` and `@JsonTypeName` from:

- `User` in `chat-core/src/main/kotlin/com/demo/chat/domain/User.kt`
- `MessageTopic` in `chat-core/src/main/kotlin/com/demo/chat/domain/MessageTopic.kt`
- `TopicMembership` in `chat-core/src/main/kotlin/com/demo/chat/domain/TopicMembership.kt`

The custom deserializers stay. They already read the flat shape.
`UserDeserializer` reads `name`, `handle` and `imageUri` flat and unwraps only
the `Key`. `TopicDeserializer` and `MembershipDeserializer` do the same. The
`Key` wrapper stays, so their `node.get("key").get("key")` unwraps still hold.

### Delete the workaround

`rebind` exists only for the asymmetry. Delete the method. Use plain
`convertValue` in `typedGet`, `typedAll` and `typedByIds`. Update the class KDoc
that describes the asymmetry.

### Update the REST contract

`LongUserRestTests` pins the wrapper at `$.user.name` and `$.[0].user.name`.
Update both to `$.name` and `$.[0].name`.

## Wire format change

| type | before | after |
|------|--------|-------|
| `User` | `{"user":{...}}` | `{...}` |
| `TopicMembership` | `{"membership":{...}}` | `{...}` |
| `MessageTopic` | `{"topic":{...}}` | see note |

The change applies to REST responses and to stored JSON in redis, cassandra and
memory. The store is disposable. Flush it. No migration.

Note on `MessageTopic`: it extends `KeyValuePair`, which keeps its own
`@JsonTypeInfo`. Once `MessageTopic` loses its annotation, Jackson annotation
inheritance may apply the `KeyValuePair` wrapper instead of the `topic` wrapper.
The exact resulting shape is not fixed by this spec. The implementation must
assert it in a test and record the outcome here.

## Verification state

The verified experiment removed the annotation from `User` only. It passed:
chat-core 43, chat-client-rsocket 57, chat-persistence-redis 46, with plain
`convertValue` and no rebind.

The `MessageTopic` and `TopicMembership` removals are not yet test-verified.
The implementation must run the full suite after all three removals and confirm
no new failures. Watch the `MessageTopic` inheritance note above.

## What does not change

- The custom deserializers for the three types
- The annotations on `Key`, `Message`, `KeyValuePair`
- `RequestResponse` (uses `As.PROPERTY`, a different mechanism)
- The six E2EE types in `EncryptedEnvelope.kt` (deferred)
- The other persistence backends (no workaround there)
- The rsocket client (verified clean, 57 pass)

## Verification

- chat-core: 43 tests pass
- chat-client-rsocket: 57 tests pass
- chat-persistence-redis: 46 tests pass, including the typed domain test with
  plain `convertValue`
- chat-webflux: the 2 pre-existing failures stay, no new failures.
  `LongUserRestTests` passes with the updated assertions
- Run `mvn -o -pl chat-core,<module> test`. Never `-pl <module>` alone.
- Full build health per `docs/BUILD-HEALTH.md`

## Follow-ups

- Create a follow-up issue for the six E2EE types: `DeviceRegistration`,
  `PreKeyBundle`, `EncryptedEnvelope`, `ConversationEpoch`, `FrankingTag`,
  `Presence`
- Update `forward-register.md`: move CHAT-gjggodpa out of the deferred table,
  record the E2EE follow-up
- Check drift bindings on the edited files. Update prose first, then
  `drift link`

## Out of scope

- CHAT-ejhtsarb (KVRequest binds key as Integer) — same erasure class, separate
  spec
- The six E2EE types — follow-up issue
- Stored JSON migration — the store is disposable