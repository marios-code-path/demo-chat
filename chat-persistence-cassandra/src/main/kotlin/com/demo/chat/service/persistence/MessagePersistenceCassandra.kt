package com.demo.chat.service.persistence

import com.demo.chat.domain.Key
import com.demo.chat.domain.Message
import com.demo.chat.domain.cassandra.ChatMessageById
import com.demo.chat.domain.cassandra.ChatMessageByIdKey
import com.demo.chat.repository.cassandra.ChatMessageRepository
import com.demo.chat.service.IKeyService
import com.demo.chat.service.MessagePersistence
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

// TODO: Convert me to STREAM
open class MessagePersistenceCassandra<T>(private val keyService: IKeyService<T>,
                                          private val messageRepo: ChatMessageRepository<T>)
    : MessagePersistence<T, String> {

    override fun key(): Mono<out Key<T>> =
            keyService.key(Message::class.java)

    override fun rem(key: Key<T>): Mono<Void> = messageRepo.rem(key)

    override fun get(key: Key<T>): Mono<out Message<T, String>> =
            messageRepo.findByKeyId(key.id)

    override fun all(): Flux<out Message<T, String>> = messageRepo.findAll()

    override fun add(ent: Message<T, String>): Mono<Void> =
            key()
                    .flatMap {
                        // TODO: We will probably need to creep here
                        // so our repos can send data<Any> as data<String>, etc..
                        messageRepo.add(ChatMessageById(ChatMessageByIdKey(it.id,
                                ent.key.id, ent.key.dest, Instant.now()),
                                ent.data, ent.record))
                    }
}