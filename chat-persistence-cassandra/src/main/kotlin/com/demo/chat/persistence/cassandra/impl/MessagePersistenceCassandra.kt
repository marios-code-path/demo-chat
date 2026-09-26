package com.demo.chat.persistence.cassandra.impl

import com.demo.chat.domain.Key
import com.demo.chat.domain.Message
import com.demo.chat.domain.SimpleMessageKey
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.persistence.cassandra.domain.ChatMessageById
import com.demo.chat.persistence.cassandra.domain.ChatMessageByIdKey
import com.demo.chat.persistence.cassandra.repository.ChatMessageRepository
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.core.MessagePersistence
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

/** Each read maps a row under the root of the MESSAGE domain. See `CHAT-avduuqwp`. */
// TODO: Convert me to STREAM
open class MessagePersistenceCassandra<T : Any>(
    private val keyService: IKeyService<T>,
    private val rootKeys: RootKeys<T>,
    private val messageRepo: ChatMessageRepository<T>
) : MessagePersistence<T, String> {
    override fun key(): Mono<out Key<T>> =
        keyService.key(ChatDomain.MESSAGE)

    override fun rem(key: Key<T>): Mono<Void> = messageRepo.rem(key)

    override fun get(key: Key<T>): Mono<out Message<T, String>> =
        messageRepo.findByKeyId(key.id).map(::message)

    override fun all(): Flux<out Message<T, String>> = messageRepo.findAll().map(::message)

    override fun add(ent: Message<T, String>): Mono<Void> =
        key()
            .flatMap {
                // TODO: We will probably need to creep here
                // so our repos can send data<Any> as data<String>, etc..
                messageRepo.add(
                    ChatMessageById(
                        ChatMessageByIdKey(it.id,
                            ent.key.id, ent.key.dest, Instant.now()),
                        ent.data, ent.record)
                )
            }

    private fun message(row: ChatMessageById<T>): Message<T, String> = Message.create(
        SimpleMessageKey(row.key.id, rootKeys.of(ChatDomain.MESSAGE).id, row.key.from, row.key.dest, row.key.timestamp),
        row.data,
        row.record,
    )
}
