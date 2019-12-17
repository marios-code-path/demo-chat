package com.demo.chat.service.index

import com.demo.chat.domain.*
import com.demo.chat.domain.cassandra.ChatMessageByTopic
import com.demo.chat.domain.cassandra.ChatMessageByTopicKey
import com.demo.chat.domain.cassandra.ChatMessageByUser
import com.demo.chat.domain.cassandra.ChatMessageByUserKey
import com.demo.chat.repository.cassandra.ChatMessageByTopicRepository
import com.demo.chat.repository.cassandra.ChatMessageByUserRepository
import com.demo.chat.service.TextMessageIndexService
import com.demo.chat.service.TextMessageIndexService.Companion.TOPIC
import com.demo.chat.service.TextMessageIndexService.Companion.USER
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.data.cassandra.core.query.Query
import org.springframework.data.cassandra.core.query.Update
import org.springframework.data.cassandra.core.query.where
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.*

class TextMessageIndexCassandra<T>(val cassandra: ReactiveCassandraTemplate,
                                   val byUserRepo: ChatMessageByUserRepository<T>,
                                   val byTopicRepo: ChatMessageByTopicRepository<T>) : TextMessageIndexService<T> {
    override fun add(ent: TextMessage<T>, criteria: Map<String, String>): Mono<Void> =
            cassandra
                    .batchOps()
                    .insert(ChatMessageByUser(
                            ChatMessageByUserKey(
                                    ent.key.id,
                                    ent.key.userId,
                                    ent.key.dest,
                                    Instant.now()
                            ),
                            ent.data,
                            ent.visible
                    ))
                    .insert(ChatMessageByTopic(
                            ChatMessageByTopicKey(
                                    ent.key.id,
                                    ent.key.userId,
                                    ent.key.dest,
                                    Instant.now()
                            ),
                            ent.data,
                            ent.visible
                    ))
                    .execute()
                    .map {
                        if (!it.wasApplied())
                            throw ChatIndexException(ent.key.id.toString())
                    }
                    .then()

    override fun rem(ent: TextMessage<T>): Mono<Void> =
            cassandra
                    .batchOps()
                    .delete(Query.query(where("msg_id").`is`(ent.key.id), where("user_id").`is`(ent.key.userId)),
                            Update.empty().set("visible", false),
                            ChatMessageByUser::class.java
                    )
                    .delete(Query.query(where("msg_id").`is`(ent.key.id), where("topic_id").`is`(ent.key.dest)),
                            Update.empty().set("visible", false),
                            ChatMessageByTopic::class.java
                    )
                    .execute()
                    .map {
                        if (!it.wasApplied())
                            throw ChatIndexException(ent.key.id.toString())
                    }
                    .then()

    override fun findBy(query: Map<String, T>): Flux<out UserMessageKey<T>> {
        val searchFor = query.keys.first()
        return when (searchFor) {
            TOPIC -> findByTopic(query[searchFor] ?: error("Missing Topic"))
            USER -> findByUser(query[searchFor] ?: error("Missing User"))
            else -> Flux.empty()
        }.map {
            it.key
        }
    }

    fun findByTopic(topic: T) = byTopicRepo.findByKeyTopicId(topic)
    fun findByUser(uid: T) = byUserRepo.findByKeyUserId(uid)
}