package com.demo.chat.service.index

import com.demo.chat.codec.Codec
import com.demo.chat.domain.ChatIndexException
import com.demo.chat.domain.Key
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.domain.cassandra.ChatMessageByTopic
import com.demo.chat.domain.cassandra.ChatMessageByTopicKey
import com.demo.chat.domain.cassandra.ChatMessageByUser
import com.demo.chat.domain.cassandra.ChatMessageByUserKey
import com.demo.chat.repository.cassandra.ChatMessageByTopicRepository
import com.demo.chat.repository.cassandra.ChatMessageByUserRepository
import com.demo.chat.service.TextMessageIndexService
import com.demo.chat.service.TextMessageIndexService.Companion.DATA
import com.demo.chat.service.TextMessageIndexService.Companion.TOPIC
import com.demo.chat.service.TextMessageIndexService.Companion.USER
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.data.cassandra.core.query.Query
import org.springframework.data.cassandra.core.query.Update
import org.springframework.data.cassandra.core.query.where
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

class MessageCriteriaCodec<T> : Codec<Message<T, out Any>, Map<String, String>> {
    override fun decode(record: Message<T, out Any>): Map<String, String> {
        return when (record.key) {
            is MessageKey<T> -> mapOf(
                    Pair(USER, record.key.from.toString()),
                    Pair(TOPIC, record.key.dest.toString()),
                    Pair(DATA, record.data.toString())
            )
            else -> mapOf(
            )
        }
    }
}

class TextMessageIndexCassandra<T>(
        val criteriaCodec: Codec<Message<T, Any>, Map<String, String>>,
        val cassandra: ReactiveCassandraTemplate,
        val byUserRepo: ChatMessageByUserRepository<T>,
        val byTopicRepo: ChatMessageByTopicRepository<T>) : TextMessageIndexService<T> {
    override fun add(ent: Message<T, Any>): Mono<Void> =
            with(criteriaCodec.decode(ent)) {
                cassandra
                        .batchOps()
                        .insert(ChatMessageByUser(
                                ChatMessageByUserKey(
                                        ent.key.id,
                                        this[USER],
                                        this[TOPIC],
                                        Instant.now()
                                ),
                                this[DATA] ?: "",
                                false
                        ))
                        .insert(ChatMessageByTopic(
                                ChatMessageByTopicKey(
                                        ent.key.id,
                                        this[USER],
                                        this[TOPIC],
                                        Instant.now()
                                ),
                                this[DATA] ?: "",
                                false
                        ))
                        .execute()
                        .map {
                            if (!it.wasApplied())
                                throw ChatIndexException(ent.key.id.toString())
                        }
                        .then()
            }

    override fun rem(ent: Key<T>): Mono<Void> =
            cassandra
                    .batchOps()
                    .delete(Query.query(where("msg_id").`is`(ent.id)/*, where("user_id").`is`(ent.key.from)*/),
                            Update.empty().set("visible", false),
                            ChatMessageByUser::class.java
                    )
                    .delete(Query.query(where("msg_id").`is`(ent.id)/*, where("topic_id").`is`(ent.key.dest)*/),
                            Update.empty().set("visible", false),
                            ChatMessageByTopic::class.java
                    )
                    .execute()
                    .map {
                        if (!it.wasApplied())
                            throw ChatIndexException(ent.id.toString())
                    }
                    .then()

    override fun findBy(query: Map<String, T>): Flux<out MessageKey<T>> {
        val searchFor = query.keys.first()
        return when (searchFor) {
            TOPIC -> findByTopic(query[searchFor] ?: error("Missing Topic"))
            USER -> findByUser(query[searchFor] ?: error("Missing User"))
            else -> Flux.empty()
        }.map {
            it.key
        }
    }

    fun findByTopic(topic: T) = byTopicRepo.findByKeyDest(topic)
    fun findByUser(uid: T) = byUserRepo.findByKeyFrom(uid)
}