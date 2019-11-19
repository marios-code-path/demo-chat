package com.demo.chat.test

import com.demo.chat.domain.EventKey
import com.demo.chat.service.KeyService
import org.mockito.Mockito
import reactor.core.publisher.Mono
import java.util.*

object TestBase

object TestKeyService : KeyService {
    override fun exists(key: EventKey): Mono<Boolean> = Mono.just(true)

    override fun <T> id(kind: Class<T>): Mono<EventKey> = Mono.just(EventKey.create(UUID.randomUUID()))

    override fun <T> key(kind: Class<T>, create: (eventKey: EventKey) -> T): Mono<out T> =
            id(kind).map { create(it) }

    override fun rem(key: EventKey): Mono<Void> = Mono.never()
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