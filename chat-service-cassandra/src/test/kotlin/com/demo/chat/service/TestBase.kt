package com.demo.chat.service

import com.demo.chat.domain.EventKey
import org.mockito.Mockito
import reactor.core.publisher.Mono
import java.util.*

// KLUDGE needed to get mockito to talk with Kotlin (type soup remember me?)
object TestBase

object TestKeyService : KeyService {
    override fun <T> id(kind: Class<T>): Mono<EventKey> = Mono.just(EventKey.create(UUID.randomUUID()))

    override fun <T> key(kind: Class<T>, create: (eventKey: EventKey) -> T): Mono<T> =
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
