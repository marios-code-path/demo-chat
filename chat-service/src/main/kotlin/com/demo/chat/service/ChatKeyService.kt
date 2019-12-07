package com.demo.chat.service

import com.demo.chat.domain.UUIDKey
import reactor.core.publisher.Mono

/**
 * create underlaying key using some external source, or operations in process
 * TODO: remove key in possible scenarios !
 * key generation is used to combine a created ID() with model object's consturctor
 */
interface  KeyService {
    fun <T> id(kind: Class<T>): Mono<UUIDKey>
    fun rem(key: UUIDKey): Mono<Void>
    fun exists(key: UUIDKey): Mono<Boolean>
    fun <T> key(kind: Class<T>, create: (key: UUIDKey) -> T): Mono<out T>
}