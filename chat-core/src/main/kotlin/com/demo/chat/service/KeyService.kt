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
interface  UUIDKeyService : IKeyService<UUID>{
    override fun <T> id(kind: Class<T>): Mono<out Key<UUID>>
    override fun rem(key: Key<UUID>): Mono<Void>
    override fun exists(key: Key<UUID>): Mono<Boolean>
    override fun <T> key(kind: Class<T>, create: (key: Key<UUID>) -> T): Mono<out T>
}

interface  IKeyService <K> {
    fun <T> id(kind: Class<T>): Mono<out Key<K>>
    fun rem(key: Key<K>): Mono<Void>
    fun exists(key: Key<K>): Mono<Boolean>
    fun <T> key(kind: Class<T>, create: (key: Key<K>) -> T): Mono<out T>
}