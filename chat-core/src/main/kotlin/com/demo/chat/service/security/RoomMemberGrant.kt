package com.demo.chat.service.security

import com.demo.chat.domain.Key
import reactor.core.publisher.Mono

/**
 * Writes the membership rows of one room member: `SEND` and `SUBSCRIBE`.
 *
 * **The owner decided this rule on 2026-10-02.** A join grants the member
 * `SEND` and `SUBSCRIBE` on the room, and the grants never expire. A leave sets
 * both grants to expire at the time of the leave. See `CHAT-mfveaecc` for `SEND`
 * and `CHAT-lfaajjcj` for `SUBSCRIBE`.
 *
 * **The port exists so the composite never reads the authorization store.**
 * `chat-service-composite` does not depend on `chat-security`. This is the same
 * shape as [RoomOwnerGrant].
 *
 * **The `Anon` key and the `User` root receive no row.** Both keys are in the
 * actor set of every query. A row for either key would let every caller send
 * to the room and listen to it.
 */
interface RoomMemberGrant<T> {

    /** Grant [member] `SEND` and `SUBSCRIBE` on [room], with no expiry. */
    fun grantMembership(member: Key<T>, room: Key<T>): Mono<Void>

    /** Set every `SEND` and `SUBSCRIBE` row of [member] on [room] to expire now. */
    fun expireMembership(member: Key<T>, room: Key<T>): Mono<Void>
}
