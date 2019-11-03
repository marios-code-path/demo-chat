package com.demo.chat.service

import com.demo.chat.domain.EventKey
import reactor.core.publisher.Mono

/**
 * create underlaying key using some external source, or operations in process
 * TODO: remove key in possible scenarios !
 * key generation is used to combine a created ID() with model object's consturctor
 */
interface  KeyService {
    fun <T> id(kind: Class<T>): Mono<EventKey>
    fun rem(key: EventKey): Mono<Void>
    fun exists(key: EventKey): Mono<Boolean>
    fun <T> key(kind: Class<T>, create: (eventKey: EventKey) -> T): Mono<out T>
}