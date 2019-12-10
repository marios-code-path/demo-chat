package com.demo.chat.service

import com.demo.chat.domain.Key
import com.demo.chat.domain.UUIDKey
import reactor.core.publisher.Mono
import java.util.*

/**
 * create underlaying key using some external source, or operations in process
 * TODO: remove key in possible scenarios !
 * key generation is used to combine a created ID() with model object's consturctor
 */
interface  UUIDKeyService : IKeyService<UUID, UUIDKey>{
    override fun <T> id(kind: Class<T>): Mono<UUIDKey>
    override fun rem(key: UUIDKey): Mono<Void>
    override fun exists(key: UUIDKey): Mono<Boolean>
    override fun <T> key(kind: Class<T>, create: (key: UUIDKey) -> T): Mono<out T>
}

interface  IKeyService <I, K: Key<I>> {
    fun <T> id(kind: Class<T>): Mono<out K>
    fun rem(key: K): Mono<Void>
    fun exists(key: K): Mono<Boolean>
    fun <T> key(kind: Class<T>, create: (key: K) -> T): Mono<out T>
}