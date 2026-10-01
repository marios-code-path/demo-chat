package com.demo.chat.service.security

import com.demo.chat.domain.Key
import reactor.core.publisher.Mono

/**
 * Writes the ownership row of one room.
 *
 * **The port exists so the composite never reads the security context.**
 * `chat-service-composite` does not depend on `chat-security`, and
 * `ContextIdentity` is the only live reader of that context. See
 * `CHAT-zhjltbky`.
 *
 * The implementation writes one wildcard row against [roomKey] for the
 * identity of the caller that created the room. A caller with no identity owns
 * no room, and that is not an error.
 *
 * **The `Anon` root key owns no room either.** An unauthenticated caller
 * reaches that key rather than no identity, and the actor set of every query
 * holds it. A row for it would make every caller an owner. See
 * `ContextRoomOwnerGrant` and `docs/ANONYMOUS-AUTHORIZATION.md`.
 *
 * **One writer holds this port.** `*` is singular per target, so two writers
 * would give one room two owners and the rank would then answer by time alone.
 */
interface RoomOwnerGrant<T> {
    fun grantOwner(roomKey: Key<T>): Mono<Void>
}
