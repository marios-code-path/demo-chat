# Imported message identity design

Issue: `CHAT-zxrgxdlx`. Parent: `CHAT-emwhnlay`.

Status: Accepted on 2026-10-15. Implementation merged through PR #241 on 2026-10-16.

## Goal

Give an imported message a derived key id. The id comes from the room id and
the source message id. A rerun recomputes the same id and reads it directly.
One read then proves a repeat. The sender, millisecond time and text match
retires.

## The defect this closes

The import route mints a fresh message id at every call. It guards a rerun by
matching three stored values: the sender, the millisecond time and the text.
Two turns with those three values equal therefore collapse. A rerun can skip a
message that it should import, or import one that it should have skipped. The
import design of `CHAT-ugsreefu` already records that collapse.

## The identity

    id = UUID.nameUUIDFromBytes("<topicId>:<messageUuid>".encodeToByteArray())

`topicId` is the room id as canonical UUID text. `messageUuid` is the source
message id from the Claude export. A colon separates them.

`UUIDKeyGenerator` in `chat-core` already mints name-based UUIDs, so the
pattern is proven in this repository.

### Why the room id is in the input

One export record reaches two topics. Rule 4 of the work order puts a shared
parent into every thread that uses it. A fork therefore imports one record into
two rooms. The room id makes the two imported messages distinct. The
conversation uuid is absent on purpose, because the room name carries it.

### Why the generation is absent from the input

The generation is an operator argument. It enters the topic name alone. A
computed counter would create a fresh room at every rerun, and that breaks the
rerun criterion of `CHAT-emwhnlay`.

## The request identity

The import route uses `import:<source-message-id>` as the command request id.
The command bus keys a request on the owner and the request id. Its fingerprint
includes the destination, so one request id cannot name two rooms.

**Option A is accepted.** The caller sends a room-scoped source id, and the
derivation hashes that value directly. The shell has sent
`<roomId>:<turn.uuid>` since PR #226, which is the frozen derivation input. So
one spelling serves the request id and the derivation.

Option B is rejected. Option B would scope the id inside the route and would
then change the identity of imports that already exist. No deployment has
imported anything, so this cost is small, and Option A needs no compatibility
treatment at all.

The route treats `messageId` as an opaque value. It stays that way.

## The key-type rule

A derived UUID cannot be a `bigint`. The long key type and the Claude import
are therefore incompatible by construction. A silent mint would hide the loss
of the rerun guarantee.

The refusal is a general seam, and it is not an import special case. Each
feature declares the key types that it supports. A deployment that enables the
feature with another key type refuses at startup. The refusal names the
feature, the measured key type, the supported set and the reason.

**Claude import supports the `uuid` key type alone.** The derivation produces a
name-based UUID, and a long column cannot hold one.

The check runs in a `BeanFactoryPostProcessor`, so it lands before any
singleton is built. This is the `CommandBusValidation` idiom, and it is why the
vector selector check moved off `SmartInitializingSingleton`.

`app.message.import` is a new switch. It declares that a deployment imports
Claude threads. Nothing reads it as an off switch in this work.

## The store change

`chat_uuid` refuses a name-based UUID today. Four tables declare
`msg_id TIMEUUID`:

- `chat_message_id`
- `chat_message_user`
- `chat_message_topic`
- `chat_message_index_by_id`

A `TIMEUUID` column accepts a version 1 UUID alone. `UUID.nameUUIDFromBytes`
produces a version 3 UUID. So each of the four columns moves to `UUID`.

`keyspace-long.cql` does not change. Its four columns are `BIGINT`.

`CassandraStoreShapeCheck.idType` and `IndexServiceConfiguration.idType` both
map a `UUID` key type to `timeuuid`. Both move to `uuid`. **The rule exists
twice, so both sites move together.**

A version 1 UUID still fits a `uuid` column. So the live
`CassandraUUIDKeyGenerator` keeps working, and the change widens the column.

The no-migration rule of 2026-09-25 stands. An existing UUID store fails the
start, and the operator recreates it.

## The source-to-chat map

The id is reproducible only with the room id in hand. A name-based UUID is one
way, so the reverse direction is lost without a record.

The importer writes `claude-import-map-<conversationUuid>-<generation>.json`
beside the export file. It holds:

- the conversation uuid and the generation;
- the export path;
- `topics`, one entry for each leaf uuid with its topic name and room id;
- `messages`, one entry for each source id with its message id, room id and
  topic name.

A second run merges into the existing file. It does not replace it. A write
failure fails the run, because a lost map is a lost reverse lookup.

A change to the derivation input orphans every imported message. Generate the
map before any such change.

## Tests

Tests cover the declaration table, the derivation, the bus rules, the composite
read, the store shape, the map and one real UUID deployment. Routes, the index
and the MCP adapter get no new test.

## Acceptance

- A UUID deployment imports a Claude conversation. A rerun imports nothing new.
- A long deployment with `app.message.import` set refuses at startup, and the
  message names the feature and the supported set.
- The derived id reads back by its key on a Cassandra UUID store.
- The map file resolves a source id to a message id after a rerun.

## Limits recorded, not fixed

1. **An existing UUID keyspace fails the start.** Its four message id columns
   are `timeuuid`. The operator recreates the store.
2. **No deployment sets `app.message.import`.** The import stays uuid-only and
   test-only after this work.
3. **A pre-change import is not recognized by the derived read.** Its stored id
   was minted, so a rerun after the change imports a duplicate. The map is the
   mitigation, and no deployment has imported anything yet.
4. **The read and the write are not atomic.** Two concurrent imports of one
   source can both submit. The stores offer no conditional write on the message
   key. This is the same limit recorded for the room name race.
5. **`app.key.type` is a launch value, not a store fact.** The seam reads the
   declaration. It does not detect a store built for another key type.
