package com.demo.chat.repository.cassandra

import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.cassandra.ChatMessageTopic
import com.demo.chat.domain.cassandra.ChatMessageTopicName
import com.demo.chat.domain.cassandra.ChatTopicKey
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.data.cassandra.core.query.Query
import org.springframework.data.cassandra.core.query.Update
import org.springframework.data.cassandra.core.query.where
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import reactor.core.publisher.Mono
import java.util.*


interface TopicByNameRepository : ReactiveCassandraRepository<ChatMessageTopicName, String> {
    fun findByKeyName(name: String): Mono<out Key<UUID>>
}

interface TopicRepository :
        ReactiveCassandraRepository<ChatMessageTopic, UUID>,
        TopicRepositoryCustom {
    fun findByKeyId(id: UUID): Mono<out MessageTopic<UUID>>
}

interface TopicRepositoryCustom {
    fun add(messageTopic: MessageTopic<UUID>): Mono<Void>
    fun rem(roomKey: Key<UUID>): Mono<Void>
}

class TopicRepositoryCustomImpl(val cassandra: ReactiveCassandraTemplate) :
        TopicRepositoryCustom {
    override fun rem(key: Key<UUID>): Mono<Void> =
            cassandra
                    .update(Query.query(where("room_id").`is`(key.id)),
                            Update.empty().set("active", false),
                            ChatMessageTopic::class.java
                    )
                    .then()

    override fun add(messageTopic: MessageTopic<UUID>): Mono<Void> = cassandra
            .insert(ChatMessageTopic(
                    ChatTopicKey(
                            messageTopic.key.id
                    ),
                    messageTopic.data,
                    true))
            .then()
}