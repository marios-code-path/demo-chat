package com.demo.chat.service.persistence

import com.demo.chat.domain.*
import com.demo.chat.repository.cassandra.ChatMessageRepository
import com.demo.chat.service.IKeyService
import com.demo.chat.service.UUIDKeyService
import com.demo.chat.service.TextMessagePersistence
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

open class TextMessagePersistenceCassandra<T>(private val keyService: IKeyService<T>,
                                              private val messageRepo: ChatMessageRepository<T>)
    : TextMessagePersistence<T> {

    override fun key(): Mono<out Key<T>> =
            keyService.key(TextMessage::class.java)

    override fun rem(key: Key<T>): Mono<Void> = messageRepo.rem(key)

    override fun get(key: Key<T>): Mono<out TextMessage<T>> =
            messageRepo.findByKeyId(key.id)

    override fun all(): Flux<out TextMessage<T>> = messageRepo.findAll()

    override fun add(message: TextMessage<T>): Mono<Void> =
            messageRepo.add(message)
}