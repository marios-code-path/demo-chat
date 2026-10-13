package com.demo.chat.service.command

import reactor.core.publisher.Mono

/**
 * The read right of the current caller on a pointer target. See
 * `CHAT-erkoxybm`.
 *
 * The composite reads no security context. A call inside the composite
 * crosses no security proxy, so the composite asks this port. An empty or
 * false answer denies.
 */
interface PointerTargetAccess<T> {
    fun canSubscribe(roomId: T): Mono<Boolean>
}
