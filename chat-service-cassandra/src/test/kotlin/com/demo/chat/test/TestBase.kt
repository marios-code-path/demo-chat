package com.demo.chat.test

import com.demo.chat.domain.Key
import com.demo.chat.domain.UUIDKey
import com.demo.chat.service.IKeyService
import com.demo.chat.service.UUIDKeyService
import org.mockito.Mockito
import reactor.core.publisher.Mono
import java.util.*

object TestBase

object TestKeyService : IKeyService<UUID> {
    override fun exists(key: Key<UUID>): Mono<Boolean> = Mono.just(true)

    override fun <T> key(kind: Class<T>): Mono<out Key<UUID>> = Mono.just(Key.anyKey(UUID.randomUUID()))

    override fun rem(key: Key<UUID>): Mono<Void> = Mono.never()
}

fun <T> anyObject(): T {
    Mockito.anyObject<T>()
    return uninitialized()
}

fun <T> uninitialized(): T = null as T

private val ALPHA_NUMERIC_STRING = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
fun randomAlphaNumeric(size: Int): String {
    var count = size
    val builder = StringBuilder()
    while (count-- != 0) {
        val character = (Math.random() * ALPHA_NUMERIC_STRING.length).toInt()
        builder.append(ALPHA_NUMERIC_STRING[character])
    }
    return builder.toString()
}