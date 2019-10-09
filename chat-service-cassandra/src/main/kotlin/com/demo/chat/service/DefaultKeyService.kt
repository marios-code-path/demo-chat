package com.demo.chat.service

import com.datastax.driver.core.utils.UUIDs
import com.demo.chat.domain.EventKey
import reactor.core.publisher.Mono

object KeyServiceCassandra : KeyService {
    override fun rem(key: EventKey): Mono<Void> = Mono.never()

    override fun <T> id(kind: Class<T>): Mono<EventKey> = Mono.just(EventKey.create(UUIDs.timeBased()))

    override fun <T> key(kind: Class<T>, create: (eventKey: EventKey) -> T): Mono<T> =
            id(kind).map { create(it) }
}