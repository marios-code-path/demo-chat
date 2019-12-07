package com.demo.chat.service.persistence

import com.demo.chat.domain.UUIDKey
import com.demo.chat.domain.TextMessage
import com.demo.chat.domain.TextMessageKey
import com.demo.chat.repository.cassandra.ChatMessageRepository
import com.demo.chat.service.KeyService
import com.demo.chat.service.TextMessagePersistence
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

open class TextMessagePersistenceCassandra(private val keyService: KeyService,
                                           private val messageRepo: ChatMessageRepository)
    : TextMessagePersistence {

    override fun key(): Mono<out UUIDKey> =
            keyService.id(TextMessageKey::class.java)

    override fun rem(key: UUIDKey): Mono<Void> = messageRepo.rem(key)

    override fun get(key: UUIDKey): Mono<out TextMessage> =
            messageRepo.findByKeyId(key.id)

    override fun all(): Flux<out TextMessage> = messageRepo.findAll()

    override fun add(message: TextMessage): Mono<Void> =
            messageRepo.add(message)
}