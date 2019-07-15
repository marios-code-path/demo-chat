package com.demo.chat.service

import com.datastax.driver.core.utils.UUIDs
import com.demo.chat.domain.EventKey
import reactor.core.publisher.Mono

object KeyServiceCassandra : KeyService {
    override fun id(): Mono<EventKey> =
        Mono.just(EventKey
                .create(UUIDs.timeBased()))

    override fun <T> key(kind: Class<T>, create: (eventKey: EventKey) -> T): Mono<T> =
            id().map { create(it) }

}