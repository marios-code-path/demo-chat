package com.demo.chat.service

import com.demo.chat.domain.Key
import com.demo.chat.domain.UUIDKey
import reactor.core.publisher.Mono
import java.util.*

/**
 * create underlaying key using some external source, or operations in process
 */
interface  UUIDKeyService : IKeyService<UUID>{
    override fun <T> key(kind: Class<T>): Mono<out Key<UUID>>
    override fun rem(key: Key<UUID>): Mono<Void>
    override fun exists(key: Key<UUID>): Mono<Boolean>
}

interface  IKeyService <K> {
    fun <T> key(kind: Class<T>): Mono<out Key<K>>
    fun rem(key: Key<K>): Mono<Void>
    fun exists(key: Key<K>): Mono<Boolean>
}