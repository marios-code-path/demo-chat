package com.demo.chat.repository.cassandra

import com.demo.chat.domain.*
import com.demo.chat.domain.cassandra.ChatEventTopic
import com.demo.chat.domain.cassandra.ChatEventTopicName
import com.demo.chat.domain.cassandra.ChatTopicKey
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.data.cassandra.core.query.Query
import org.springframework.data.cassandra.core.query.Update
import org.springframework.data.cassandra.core.query.where
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import reactor.core.publisher.Mono
import java.util.*


interface TopicByNameRepository : ReactiveCassandraRepository<ChatEventTopicName, String> {
    fun findByKeyName(name: String): Mono<ChatEventTopicName>
}

interface TopicRepository :
        ReactiveCassandraRepository<ChatEventTopic, UUID>,
        TopicRepositoryCustom
{
    fun findByKeyId(id: UUID): Mono<ChatEventTopic>
}

interface TopicRepositoryCustom {
    fun add(eventTopic: EventTopic): Mono<Void>
    fun rem(roomKey: UUIDKey): Mono<Void>
}

class TopicRepositoryCustomImpl(val cassandra: ReactiveCassandraTemplate) :
        TopicRepositoryCustom {
    override fun rem(key: UUIDKey): Mono<Void> =
            cassandra
                    .update(Query.query(where("room_id").`is`(key.id)),
                            Update.empty().set("active", false),
                            ChatEventTopic::class.java
                    )
                    .then()

    override fun add(eventTopic: EventTopic): Mono<Void> = cassandra
            .insert(ChatEventTopic(
                    ChatTopicKey(
                            eventTopic.key.id
                    ),
                    eventTopic.name,
                    true))
            .then()
}