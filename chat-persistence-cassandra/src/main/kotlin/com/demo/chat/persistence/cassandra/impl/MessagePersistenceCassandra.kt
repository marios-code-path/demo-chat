package com.demo.chat.persistence.cassandra.impl

import com.demo.chat.service.core.StoreDomain

import com.demo.chat.domain.ChatException
import com.demo.chat.domain.Key
import com.demo.chat.domain.Message
import com.demo.chat.domain.PointerMessage
import com.demo.chat.domain.SimpleMessageKey
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.persistence.cassandra.domain.ChatMessageById
import com.demo.chat.persistence.cassandra.domain.ChatMessageByIdKey
import com.demo.chat.persistence.cassandra.repository.ChatMessageRepository
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.core.MessagePersistence
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/** Each read maps a row under the root of the MESSAGE domain. See `CHAT-avduuqwp`. */
// TODO: Convert me to STREAM
open class MessagePersistenceCassandra<T : Any>(
    private val keyService: IKeyService<T>,
    private val rootKeys: RootKeys<T>,
    private val messageRepo: ChatMessageRepository<T>,
    private val typeUtil: TypeUtil<T>,
) : MessagePersistence<T, String> {
    override fun key(): Mono<out Key<T>> =
        keyService.key(ChatDomain.MESSAGE)

    override fun rem(key: Key<T>): Mono<Void> = messageRepo.rem(key)

    override fun get(key: Key<T>): Mono<out Message<T, String>> =
        messageRepo.findByKeyId(key.id).map(::message)

    override fun all(): Flux<out Message<T, String>> = messageRepo.findAll().map(::message)

    /**
     * The default answers nothing, so the room history of a Cassandra
     * deployment was always empty, with no error. See `CHAT-xcmpudyb`.
     */
    override fun byIds(keys: List<Key<T>>): Flux<out Message<T, String>> =
        if (keys.isEmpty()) Flux.empty() else messageRepo.findByKeyIdIn(keys.map { it.id }).map(::message)

    /** The key must be in MESSAGE before the write. See `CHAT-avduuqwp`, T5. */
    override fun add(ent: Message<T, String>): Mono<Void> =
        StoreDomain.requireKey(ent.key, ChatDomain.MESSAGE, rootKeys).then(Mono.defer { write(ent) })

    /**
     * The row keeps the supplied id, sender, destination and timestamp. The
     * write mints nothing. It minted a new id and stored the message id as the
     * sender, so a stored message could not be read by its own key. See
     * `CHAT-avduuqwp`, E20.
     *
     * A pointer stores its target id in `text` and its target domain in
     * `kind`. A text message leaves `kind` null. See `CHAT-erkoxybm`.
     */
    private fun write(ent: Message<T, String>): Mono<Void> =
        messageRepo.add(
            ChatMessageById(
                ChatMessageByIdKey(ent.key.id, ent.key.from, ent.key.dest, ent.key.timestamp),
                if (ent is PointerMessage<T>) typeUtil.toString(ent.target.id) else ent.data,
                ent.record,
                if (ent is PointerMessage<T>) targetDomain(ent.target).wireName else null,
            )
        )

    private fun message(row: ChatMessageById<T>): Message<T, String> {
        val key = SimpleMessageKey(row.key.id, rootKeys.of(ChatDomain.MESSAGE).id, row.key.from, row.key.dest, row.key.timestamp)
        val kind = row.kind ?: return Message.create(key, row.data, row.record)
        val domain = ChatDomain.parse(kind)?.takeIf { it in POINTER_TARGETS }
            ?: throw ChatException("Message ${row.key.id} has the unknown kind '$kind'.")
        return PointerMessage(key, Key.of(typeUtil.exactFrom(row.data), rootKeys.of(domain).id))
    }

    private fun targetDomain(target: Key<T>): ChatDomain =
        rootKeys.domainOfRoot(target.root)?.takeIf { it in POINTER_TARGETS }
            ?: throw ChatException("A pointer target must carry the Message root or the MessageTopic root. The root is ${target.root}.")

    private companion object {
        val POINTER_TARGETS = setOf(ChatDomain.MESSAGE, ChatDomain.MESSAGE_TOPIC)
    }
}
