package com.demo.chat.service.security

import com.demo.chat.domain.Key
import reactor.core.publisher.Mono

/**
 * Writes the `SEND` row of one room member.
 *
 * **The owner decided this rule on 2026-10-02.** A join grants the member
 * `SEND` on the room, and the grant never expires. A leave sets that grant to
 * expire at the time of the leave. See `CHAT-mfveaecc`.
 *
 * **The port exists so the composite never reads the authorization store.**
 * `chat-service-composite` does not depend on `chat-security`. This is the same
 * shape as [RoomOwnerGrant].
 *
 * **The `Anon` key and the `User` root receive no row.** Both keys are in the
 * actor set of every query. A `SEND` row for either key would let every caller
 * send to the room.
 */
interface RoomMemberGrant<T> {

    /** Grant [member] `SEND` on [room], with no expiry. */
    fun grantSend(member: Key<T>, room: Key<T>): Mono<Void>

    /** Set every `SEND` row of [member] on [room] to expire now. */
    fun expireSend(member: Key<T>, room: Key<T>): Mono<Void>
}
