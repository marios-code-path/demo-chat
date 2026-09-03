package com.demo.chat.service.vector

import com.demo.chat.domain.Key
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import reactor.core.publisher.Mono

interface MessageVectorIndexer<T> {
    fun add(message: Message<T, String>): Mono<Void>
    fun remove(key: Key<T>): Mono<Void>
}

data class MessageRecallHit<T>(
    val key: MessageKey<T>,
    val score: Double?,
)