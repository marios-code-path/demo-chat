package com.demo.chat.service.index

import com.demo.chat.domain.*
import com.demo.chat.repository.cassandra.ChatMessageByTopicRepository
import com.demo.chat.repository.cassandra.ChatMessageByUserRepository
import com.demo.chat.service.ChatMessageIndexService
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.data.cassandra.core.query.Query
import org.springframework.data.cassandra.core.query.Update
import org.springframework.data.cassandra.core.query.where
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.*

class ChatMessageIndexCassandra(val cassandra: ReactiveCassandraTemplate,
                                val byUserRepo: ChatMessageByUserRepository,
                                val byTopicRepo: ChatMessageByTopicRepository) : ChatMessageIndexService {
    override fun add(ent: TextMessage, criteria: Map<String, String>): Mono<Void> =
            cassandra
                    .batchOps()
                    .insert(ChatMessageByUser(
                            ChatMessageByUserKey(
                                    ent.key.id,
                                    ent.key.userId,
                                    ent.key.topicId,
                                    Instant.now()
                            ),
                            ent.value,
                            ent.visible
                    ))
                    .insert(ChatMessageByTopic(
                            ChatMessageByTopicKey(
                                    ent.key.id,
                                    ent.key.userId,
                                    ent.key.topicId,
                                    Instant.now()
                            ),
                            ent.value,
                            ent.visible
                    ))
                    .execute()
                    .map {
                        if (!it.wasApplied())
                            throw ChatIndexException(ent.key.id)
                    }
                    .then()

    override fun rem(ent: TextMessage): Mono<Void> =
            cassandra
                    .batchOps()
                    .delete(Query.query(where("msg_id").`is`(ent.key.id), where("user_id").`is`(ent.key.userId)),
                            Update.empty().set("visible", false),
                            ChatMessageByUser::class.java
                    )
                    .delete(Query.query(where("msg_id").`is`(ent.key.id), where("topic_id").`is`(ent.key.topicId)),
                            Update.empty().set("visible", false),
                            ChatMessageByTopic::class.java
                    )
                    .execute()
                    .map {
                        if (!it.wasApplied())
                            throw ChatIndexException(ent.key.id)
                    }
                    .then()

    override fun findBy(query: Map<String, String>): Flux<out TextMessageKey> {
        val searchFor = query.keys.first()
        return when (searchFor) {
            TOPIC -> findByTopic(UUID.fromString(query[searchFor]))
            USER -> findByUser(UUID.fromString(query[searchFor]))
            else -> Flux.never()
        }.map {
            it.key
        }
    }

    companion object {
        const val TOPIC = "topicId"
        const val USER = "userId"
    }

    fun findByTopic(topic: UUID) = byTopicRepo.findByKeyTopicId(topic)
    fun findByUser(uid: UUID) = byUserRepo.findByKeyUserId(uid)
}