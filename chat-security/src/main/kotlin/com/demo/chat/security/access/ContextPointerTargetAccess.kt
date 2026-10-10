package com.demo.chat.security.access

import com.demo.chat.service.command.PointerTargetAccess
import reactor.core.publisher.Mono

/** `SUBSCRIBE` on a room, through the same check as the access expressions. See `CHAT-erkoxybm`. */
class ContextPointerTargetAccess<T>(private val access: SpringSecurityAccessBrokerService<T>) : PointerTargetAccess<T> {
    override fun canSubscribe(roomId: T): Mono<Boolean> = access.hasAccessToId(roomId, "SUBSCRIBE")
}
