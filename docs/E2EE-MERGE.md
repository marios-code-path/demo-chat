# E2EE Merge — Agent-Bridge Plan × demo-chat

Merged the Claude Code × Codex planning dialogue (`~/agent-planning/plan.md`) into the demo-chat Kotlin/Spring Boot multi-module substrate.

## What was merged

The plan described a TypeScript/Node.js WebSocket chat with E2EE, presence, and message persistence. We translated every concept into Kotlin, matching demo-chat's existing architecture patterns (domain interfaces, reactive `Mono`/`Flux` service contracts, `@ConditionalOnProperty` deployment wiring, multi-module Maven structure).

## New modules

### 1. `chat-crypto` — E2EE Service Implementations

In-memory implementations of the six E2EE service contracts defined in `chat-core`:

| Service | Interface | Purpose |
|---------|-----------|---------|
| Device registry | `DeviceService<T>` | Per-device registration (identity keys, signed prekeys) |
| Pre-key distribution | `PreKeyService<T>` | One-time pre-key pool with refill signaling (X3DH) |
| Encrypted message relay | `EncryptedMessageService<T>` | Blind relay — stores ciphertext only, routes by metadata |
| Conversation sequence | `ConversationSeqService<T>` | Per-conversation monotonic seq allocation (no gaps) |
| Conversation epochs | `ConversationEpochService<T>` | Membership boundary tracking (prevents history leaks) |
| Franking | `FrankingService<T>` | Server-generated proof tags for abuse reporting |

**Wiring:** `@ConditionalOnProperty(prefix = "app.service.crypto", name = ["backend"], havingValue = "memory")`

### 2. `chat-presence` — Presence Detection

In-memory presence service with heartbeat TTL simulation:

| Service | Interface | Purpose |
|---------|-----------|---------|
| Presence | `PresenceService<T>` | Heartbeat tracking, online/away/offline derivation, typing indicators, presence fan-out via Reactor `Sinks` |

**Wiring:** `@ConditionalOnProperty(prefix = "app.service.presence", name = ["backend"], havingValue = "memory")`

### 3. `chat-deploy-e2ee` — Deployment Wiring

Master E2EE deployment configuration that imports crypto and presence beans:

```
app.service.e2ee.enabled=true     → activates E2EE configuration
app.service.crypto.backend=memory → in-memory crypto (dev)
app.service.presence.backend=memory → in-memory presence (dev)
```

Follows the same `@ConditionalOnProperty` pattern as `chat-deploy-memory` and `chat-deploy-cassandra`.

## New domain types in `chat-core`

Added to `com.demo.chat.domain`:

| Type | File | Role |
|------|------|------|
| `DeviceRegistration<T>` | `EncryptedEnvelope.kt` | Per-device identity + public keys |
| `PreKeyBundle<T>` | `EncryptedEnvelope.kt` | One-time X3DH pre-key bundle |
| `EncryptedEnvelope<T>` | `EncryptedEnvelope.kt` | Per-device ciphertext (server never sees plaintext) |
| `ConversationCursor<T>` | `EncryptedEnvelope.kt` | Per-conversation sequence allocator |
| `ConversationEpoch<T>` | `EncryptedEnvelope.kt` | Membership boundary |
| `FrankingTag<T>` | `EncryptedEnvelope.kt` | Abuse-report proof tag |
| `Presence<T>` | `EncryptedEnvelope.kt` | Ephemeral presence state |
| `MessageKind` (enum) | `EncryptedEnvelope.kt` | PAIRWISE, SENDER_KEY, MLS |
| `PresenceState` (enum) | `EncryptedEnvelope.kt` | ONLINE, AWAY, OFFLINE, TYPING |
| `HistoryVisibility` (enum) | `EncryptedEnvelope.kt` | SINCE_JOIN (v1), SHARED (future) |

## New service interfaces in `chat-core`

Added to `com.demo.chat.service.core` (`E2EEServices.kt`):

- `DeviceService<T>`
- `PreKeyService<T>`
- `EncryptedMessageService<T>`
- `ConversationSeqService<T>`
- `ConversationEpochService<T>`
- `FrankingService<T>`
- `PresenceService<T>`

All follow the existing pattern: reactive (`Mono`/`Flux`), generic over key type `T` (Long via Snowflake, or UUID — selected by `app.key.type`).

## Architecture mapping (plan → demo-chat)

| Plan concept | demo-chat implementation |
|---|---|
| Blind relay + ordered log | `EncryptedMessageService` — stores ciphertext only |
| Postgres `conversation_cursors` table | `ConversationSeqService` — in-memory AtomicLong (prod: Postgres row-lock) |
| `device_inbox` table | `EncryptedEnvelope` stored per `(deviceId, conversationId, seq)` |
| X3DH + Double Ratchet (libsignal) | `PreKeyService` + `DeviceService` — server stores public keys only |
| Per-device encrypted envelopes | `EncryptedEnvelope<T>` with `messageKind` (PAIRWISE, SENDER_KEY, MLS) |
| Conversation epochs | `ConversationEpochService` — membership boundary tracking |
| Franking tags | `FrankingService` — SHA-256 proof tags for abuse reporting |
| Presence via heartbeat TTL | `PresenceService` — in-memory TTL simulation (prod: Redis EXPIRE) |
| `HistoryVisibility.SINCE_JOIN` | `HistoryVisibility` enum — SHARED cut from v1 |
| No-plaintext CI canary | (TODO — needs test infrastructure) |
| Socket tickets (not access tokens) | (TODO — needs RSocket security refactor) |

## Key design decisions (from the Codex dialogue)

1. **Server is a blind relay** — stores ciphertext and routing metadata only. No server-side full-text search, no plaintext push bodies.
2. **Postgres-authoritative ordering** — seq allocation inside the same transaction as message write (in-memory for now, Postgres row-lock for production).
3. **Total order for v1** — causal/Lamport ordering deferred. Server-visible `seq` is transport order.
4. **`SHARED` history cut from v1** — only `SINCE_JOIN` shipped, per Codex Round 2 must-fix #1.
5. **Franking tags bind per-envelope** — not just the nullable `messages.ciphertext`, but the actual delivered envelope hash.
6. **Push notifications generic only** — `{ conversation_id, seq }` payload, no NSE ratchet advancement in v1.
7. **Pre-key refill signaling** — when pool drops below threshold (5), server signals device to refill.

## What still needs work

From Codex Round 2 "must-fix items before coding starts":

- [ ] Remove `SHARED` from v1 `history_visibility` schema (already enum-only, needs enforcement)
- [ ] Add explicit membership/fetch authorization fields and predicates
- [ ] Define the history gap/tombstone wire contract for retention and deletes
- [ ] Finalize franking schema and protocol fields (per-envelope tags)
- [ ] Change WebSocket `Hello`/`Reauth` to use short-lived socket tickets
- [ ] Decide v1 notifications are generic only (enforce in push service)
- [ ] Specify sender-key distribution ordering and repair UX
- [ ] Add attachment reservation/quota tables and lifecycle
- [ ] Define abuse-report plaintext storage controls and retention
- [ ] Define connection metadata retention before implementation emits logs

## How to activate E2EE

Add to `application.yml` (or via `-D` flags):

```yaml
app:
  service:
    e2ee:
      enabled: true
    crypto:
      backend: memory
    presence:
      backend: memory
```

Or via command line, where `--e2ee` sets all three:

```bash
chat-build core --memory --e2ee --run --notls --long --init users,rootkeys
```

## Module dependency graph

```
chat-deploy-e2ee
  ├── chat-crypto (in-memory E2EE services)
  │   └── chat-core (domain types + service interfaces)
  └── chat-presence (in-memory presence service)
      └── chat-core
```

The new modules are pure additions — no existing code was modified (except `pom.xml` to register the new modules). The `@ConditionalOnProperty` gating means the new services are dormant unless explicitly enabled.
