# Message pointer design

Issue: `CHAT-erkoxybm`. Date: 2026-10-10. Status: the owner approved this written spec on 2026-10-10.

## Purpose

A pointer is a message that names one target. The target is a message or a topic. A pointer carries no content of its own.

A user posts a pointer into a room. Listeners of that room receive the pointer in history and live. A listener can follow the pointer to its target, if the listener can read the target.

## Owner decisions

The owner made these decisions on 2026-10-10.

1. **A pointer lives in a host room.** `key.from` is the creator. `key.dest` is the host room. The field `target` names the target.
2. **`FORWARD` is a new permission.** Creation checks `FORWARD` on the host room. It is not a new domain and it has no root key.
3. **The creator must be able to read the target.** A target that the creator cannot read answers the same error as a missing target.
4. **A pointer uses the backends `P`, `I`, and `U`.** It uses no `V`. A pointer is stored, it appears in history, and recall never returns it.
5. **One nullable `kind` column marks a pointer row.** The column holds the domain name of the target.
6. **A pointer to a pointer is refused at creation.** No stored chain can exist.
7. **No reader fetches a preview of the target.** A reader shows the target domain and the target id.
8. **Security checks the room of a message.** `messageById` checks `SUBSCRIBE` on the `dest` of the returned message. This rule applies to every message, not to pointers alone.
9. **A topic target uses `SUBSCRIBE`.** It does not use `GET`.
10. **Tests cover the persistence stores, the creation checks, and the `messageById` rule.** Routes, the index, the shell, and MCP get no pointer test.
11. **The message value type stays `String`.** A pointer is a `Message<T, String>` with an empty `data` and a separate `target` field. This replaces an earlier decision of the same day to bind the value type to `Any`. That decision needed changes at 206 test sites in 96 test files, and at every Spring injection point of `CompositeServiceBeans<T, String>`.

## Domain type

```kotlin
@JsonTypeName("PointerMessage")
data class PointerMessage<T>(
    override val key: MessageKey<T>,   // from = creator, dest = host room
    val target: Key<T>,                // the target: id and root
) : Message<T, String> {
    override val data: String get() = ""
    override val record: Boolean get() = true
}
```

- `PointerMessage` goes into the `@JsonSubTypes` list of `Message`, beside `JoinAlert` and `LeaveAlert`.
- The `target` key keeps its own JSON wrapper.
- `data` is always the empty string. It carries no content. A pointer is told apart by its type, never by its `data`.
- The pointer and its target share the type `T`. So the compiler refuses a pointer across key types.
- The target root is the `Message` root or the `MessageTopic` root. No other root is legal.

### The request

```kotlin
@JsonTypeName("MessagePointerRequest")
data class MessagePointerRequest<T>(
    val dest: T,
    val targetId: T,
    val targetDomain: String,
    val requestId: String,
) : RequestResponse<T>()
```

- `targetDomain` accepts `Message` and `MessageTopic`. Any other value answers 400 or `KeyInputException`.
- The request carries no creator. The server binds the creator to the authenticated user, as `submit` does.
- `requestId` follows the rules of `submit`. A retry with the same ID and the same target recovers the same receipt.

### The wire shape

A pointer serializes as a `PointerMessage` wrapper. It holds `key`, `data` with the value `""`, `record`, and `target`. The wire shape of a text message does not change.

### The type boundary

The message value type `V` stays `String` in every composition. No service signature, bean type, or injection point changes its type arguments.

| Boundary | Text message | Pointer |
|---|---|---|
| `CommandSubmission<T, String>` | `content` is the text. `target` is null. | `content` is `""`. `target` is the target key. |
| `CommandFingerprint.of` | The fields of today | The fields of today, plus the target root id and the target id. The operation name is a field, so a pointer and a text cannot collide. |
| `MemoryDomainCommandBus.stage` | `Message.create` | `PointerMessage`. It requires `RECORD_POINTER` and a non-null `target`. |
| `AcceptedCommand<T, String>.message` | A text message | A `PointerMessage` |
| `P`, `I`, `U` | As today | The backend reads the type, as the storage section states. |
| `V` | Embedded | Not an obligation, so not reached |
| `MessageReindexService` | Sent to the indexer | Skipped |

`CommandSubmission` gains one field, `target: Key<T>? = null`. A text submission leaves it null. The bus refuses a `RECORD_POINTER` submission with a null `target`, and it refuses any other operation with a non-null `target`.

## Routes

| Transport | Route | Request ID |
|---|---|---|
| RSocket | `message.message-pointer` with `MessagePointerRequest` | The field `requestId` |
| REST | `POST /message/pointer/{roomId}`, JSON body `{"targetDomain": "...", "targetId": "..."}` | The header `Idempotency-Key` |

The REST body carries `targetId` as a JSON string. A JSON body cannot carry the key type, so the composite converts every target id with `TypeUtil.exactFrom`. A value that does not convert exactly answers 400 with `KeyInputException`. The same conversion repairs an RSocket id that the decoder read as a narrower number.

Both routes return `MessageSendResult`. The outcomes and the REST status codes are the same as for `submit`.

## Creation checks

The composite runs these checks in this order.

1. The caller is not `Anon`. An anonymous caller is refused, as for `submit`.
2. The caller holds `FORWARD` on the host room. Otherwise the route answers 403 or `0x403`.
3. `targetDomain` is `Message` or `MessageTopic`. Otherwise the route answers 400.
4. The server reads the target through the composite services. It applies the read rule of the next section.
5. A missing target and an unreadable target both answer 404. The message is `no such message` or `no such topic`.
6. A message target that is a `PointerMessage` answers 400. The message is `a pointer cannot target a pointer`.
7. The server admits one command with the operation `RECORD_POINTER`.

Step 4 leaks nothing. Step 6 runs after the read check, so the caller can already read the target.

## The read rule

**Security reads the message, and then checks its room.** The rule replaces the `GET` check on the message key.

```kotlin
@PostAuthorize("@chatAccess.hasAccessToId(returnObject.key.dest, 'SUBSCRIBE')")
override fun messageById(req: ByIdRequest<T>): Mono<out Message<T, V>>
```

The check reads `key.dest` alone. `dest` is a raw id, so the expression uses `hasAccessToId`. The two argument `hasAccessTo` takes a `Key`, and a raw id fails there with `EL1004E`. It does not read the target of a pointer. The `key.dest` of a pointer is its host room. Following a pointer is a separate read, and that read gets its own check.

### The access sites

Two sites check `GET` on a message today. Both change in one commit.

| Site | Today | After |
|---|---|---|
| `MessageServiceAccess.messageById`, `chat-security`. RSocket `message-by-id` uses it. | `@PreAuthorize` with `GET` on `#req.id` | `@PostAuthorize` with `hasAccessToId` and `SUBSCRIBE` on `returnObject.key.dest` |
| `ChatMessageServiceRestMapping.restMessageById`, `chat-webflux`. REST `GET /message/id/{id}` uses it. | `@PreAuthorize` with `GET` on `#id.key.id` | `@PostAuthorize` with `hasAccessToId` and `SUBSCRIBE` on `returnObject.key.dest` |

**The REST site needs its own change.** `restMessageById` calls `messageById` on the same object. That call crosses no proxy, so the check of `messageById` does not run there. A REST `GET` check that stays would also refuse a caller before the room check runs.

The MCP adapter reads a message through REST `GET /message/id/{id}` in `MessagingClient`. So the REST site supplies its check. **The MCP room check in `MessagingToolService` stays as a second guard.** It also limits a read to the configured rooms, and the server check does not do that.

The `GET` checks of `TopicServiceAccess` and `ChatTopicServiceRestMapping` name a topic, not a message. They do not change.

### Removal of the `GET` row

**The row `{Anon, Message, GET}` leaves `userinit.yml` in the same commit as the two site changes.** It must not leave earlier. A `GET` check that stays would refuse every caller once the row is gone.

Before the removal, an audit reads every main source for a `GET` check on a message key. On 2026-10-10, the two sites above were the only ones. The audit runs again at implementation time.

| Action | Check |
|---|---|
| Read a pointer in its host room | `SUBSCRIBE` on the host room. `listenTopic` and `listMessages` already check this. |
| Follow a message pointer with `messageById` | `SUBSCRIBE` on the `dest` of the target message |
| Follow a topic pointer with a listen | `SUBSCRIBE` on the target topic. The listen route already checks this. |
| Create a pointer | The two follow checks. A denial maps to 404, as step 5 states. |

**This changes `messageById` for every message.** Before this change, the shipped row `{Anon, Message, GET}` let any caller read any message by id. After it, a caller needs `SUBSCRIBE` on the room of the message.

- The `messageById` row of `docs/ANONYMOUS-AUTHORIZATION.md` moves from allow to deny for a caller that is not a member. That document records the new rule and the removal of the `GET` row.
- No deployment exists, so the change interrupts no running service. The next deployment gets the new rule.

**The store read occurs before the denial.** `@PostAuthorize` needs the message to read its `dest`. A read has no effect, so this is safe.

A denied `messageById` answers 403, and that tells the caller that the id exists. Only the creation path maps a denial to 404.

## Grants

- `FORWARD` joins the permission names beside `SEND` and `SUBSCRIBE`.
- `ShippedGrantPolicies.ROOM_JOIN` grants `FORWARD` to the member. `ROOM_LEAVE` sets it to expire at the time of the leave.
- The owner `*` row of a room covers `FORWARD`. The `Admin` `*` rows on the domain roots cover it too.
- No row for `FORWARD` goes into `userinit.yml`.
- The typed operation policy schema of `CHAT-zcxgrtqc` must accept `FORWARD`.
- `docs/ANONYMOUS-AUTHORIZATION.md` gains a pointer row. An anonymous caller is denied. A member is allowed. A caller that is not a member is denied. The agent is denied unless it joined.

## Command bus

- `CommandOperation` gains `RECORD_POINTER`.
- **`V` is not an obligation of a pointer command.** `MemoryDomainCommandBus` already removes `U` from the obligations of an import with `publish=false`. It removes `V` from a `RECORD_POINTER` command the same way.
- The dispatcher runs only the obligations, so `MessageVectorHandler` never receives a pointer.
- The status store records only the obligations. So the status of a pointer command lists `P`, `I`, and `U`, and it does not list `V`.
- The caller waits for the configured requirement minus `V`. The import route already subtracts `U` the same way. With the default `P,I`, nothing changes.
- For `RECORD_POINTER`, the bus builds a `PointerMessage` in place of `Message.create`.
- `MessageReindexService` reads the stored messages. It skips a `PointerMessage`, so a rebuild never sends a pointer to the indexer.
- A pointer writes no grant and no membership. It starts no join alert, no leave alert, and no vector work.

## Storage

| Backend | Change |
|---|---|
| Cassandra persistence | `chat_message_id` gains `kind text`. The writer puts the target id in `text` and the target domain name in `kind`. |
| Cassandra index | `chat_message_user` and `chat_message_topic` store a null `text` for a pointer. History resolves keys through `chat_message_id`, so these tables need no `kind`. |
| Memory persistence | No change. The store holds the object. |
| Redis persistence | No code change. Jackson reads the `PointerMessage` subtype through `@JsonSubTypes`. |
| Lucene index | `IndexEntryEncoder.ofMessage` writes `data` for every message today, even when it is empty. **For a pointer, the encoder omits the `data` field.** It writes the `id`, `topic`, and `user` fields. So history by room finds the pointer, and a text search does not. |
| Vector | `MessageReindexService` skips a `PointerMessage`. |

### The `kind` column

| Value | Meaning |
|---|---|
| null | A text message. Every current write path stays the same. |
| `Message` | A pointer to a message |
| `MessageTopic` | A pointer to a topic |

The values are `ChatDomain.wireName` values. The reader builds the target root from `RootKeys` for that domain. The reader converts the id in `text` with the exact id conversion. A value that does not convert fails the read. It does not truncate.

Both `keyspace-*.cql` files gain the column. The Cassandra `StoreShapeCheck` requires it. The no-migration rule applies. A store without the column fails its start, and the operator recreates the store.

## Readers

No reader fetches the target.

- The shell `messages` command prints `→ message <id>` or `→ topic <id>` after the time and the sender. It does not print the room of a target message, because that needs a read of the target. The shell maps the target root to a domain with `RootKeys.domainOfRoot`.
- REST NDJSON and RSocket send the `PointerMessage` JSON shape.
- An MCP history item and an MCP message item for a pointer carry `kind: "pointer"`, `targetId`, and `targetRoot`. They carry no `text`. The adapter holds no root keys, so it cannot name the target domain. A text item does not change. Before this change, the adapter refused a whole history that held any wrapper other than `message`. `docs/MCP-ADAPTER.md` describes the item.
- `messageById` on a pointer id returns the pointer itself.

## Tests

The tests cover the persistence stores, the creation checks, and the `messageById` rule.

| Test | Proof |
|---|---|
| Memory persistence | A pointer to a message and a pointer to a topic each survive a write and a read. |
| Redis persistence | The same round trip, through Jackson |
| Cassandra persistence, `long` and `uuid` | The same round trip. A text message reads back with a null `kind`. A row with an unknown `kind` fails the read. |
| Cassandra shape check | A store without `kind` fails the check. |
| `messageById` security, RSocket and REST | A member of the room reads the message. A caller that is not a member is denied. Removing either `@PostAuthorize` rule fails its deny case. |

### The creation checks

One test class drives the composite with method security on. It covers each refusal of the creation checks. After each refusal, the test reads the message store and finds no pointer.

| Case | Expected |
|---|---|
| An anonymous caller | Refused, as for `submit` |
| A caller without `FORWARD` on the host room | 403 or `0x403` |
| A `targetDomain` other than `Message` or `MessageTopic` | 400 |
| A target message that does not exist | 404, `no such message` |
| A target message in a room where the caller has no `SUBSCRIBE` | 404, `no such message` |
| A target topic that does not exist | 404, `no such topic` |
| A target topic where the caller has no `SUBSCRIBE` | 404, `no such topic` |
| A target message that is a pointer | 400, `a pointer cannot target a pointer` |
| A member with `FORWARD` and a readable target, the control | One pointer is stored. |

Three mutations, each run alone, must each fail their own cases:

1. Remove the `FORWARD` check.
2. Remove the read check on the target.
3. Remove the pointer-to-pointer refusal.

The owner first limited tests to the persistence stores on 2026-10-10. A review the same day recommended this class, and the owner accepted it.

A test of a non-`String` text value is not needed. The value type stays `String`, so a transport cannot admit an object as the text.

## Out of scope

- A preview of the target.
- A resolve route.
- Removal of a pointer.
- A target of another key type.
- A target domain other than `Message` and `MessageTopic`.
- Tests for the transport routes, the index, the shell, and MCP.

## Risks

- **The routes, the index, the shell, and MCP have no pointer test.** A defect there reaches `master` unseen. The owner accepted this on 2026-10-10.
- **The Redis and Kafka pub/sub codecs have no pointer test.** A pointer that does not serialize there fails `U` after `P` stores it. The room history still shows it.
- **A reader that ignores the type shows a pointer as an empty message.** The pointer `data` is `""`. The shell, REST, and MCP readers in this repository each branch on `PointerMessage`. An outside client may not.
- **The `messageById` change can refuse a reader that worked before.** The shell and MCP read history through `listMessages` and the listen route. Those routes already check `SUBSCRIBE`. A client that reads a message by id from a room it did not join gets 403 after this change.
