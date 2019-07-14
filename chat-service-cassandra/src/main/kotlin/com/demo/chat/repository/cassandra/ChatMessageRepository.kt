package com.demo.chat.repository.cassandra

import com.demo.chat.domain.*
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.data.cassandra.core.query.Query
import org.springframework.data.cassandra.core.query.Update
import org.springframework.data.cassandra.core.query.where
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

interface ChatMessageByUserRepository : ReactiveCassandraRepository<ChatMessageByUser, UUID> {
    fun findByKeyUserId(userId: UUID): Flux<ChatMessageByUser>
}

interface ChatMessageByTopicRepository : ReactiveCassandraRepository<ChatMessageByTopic, UUID> {
    fun findByKeyTopicId(topicId: UUID): Flux<ChatMessageByTopic>
}

interface ChatMessageRepository : ChatMessageRepositoryCustom, ReactiveCassandraRepository<ChatMessageById, UUID> {
    fun findByKeyMsgId(id: UUID): Mono<ChatMessageById>
}

interface ChatMessageRepositoryCustom {
    fun rem(key: TextMessageKey): Mono<Void>
    fun add(msg: TextMessage): Mono<Void>
}

class ChatMessageRepositoryCustomImpl(val cassandra: ReactiveCassandraTemplate)
    : ChatMessageRepositoryCustom {
    override fun rem(key: TextMessageKey): Mono<Void> =
            cassandra
                    .batchOps()
                    .update(Query.query(where("msg_id").`is`(key.msgId)),
                            Update.empty().set("visible", false),
                            ChatMessageByUser::class.java
                    )
                    .update(Query.query(where("msg_id").`is`(key.msgId)),
                            Update.empty().set("visible", false),
                            ChatMessageByTopic::class.java
                    )
                    .update(Query.query(where("msg_id").`is`(key.msgId)),
                            Update.empty().set("visible", false),
                            ChatMessageById::class.java
                    )
                    .execute()
                    .map {
                        if (!it.wasApplied())
                            throw ChatException("Cannot Disable Message")
                    }
                    .then()

    override fun add(msg: TextMessage): Mono<Void> =
            cassandra
                    .batchOps()
                    .insert(ChatMessageById(
                            ChatMessageByIdKey(
                                    msg.key.msgId,
                                    msg.key.userId,
                                    msg.key.topicId,
                                    msg.key.timestamp
                            ),
                            msg.value,
                            msg.visible
                    ))
                    .insert(ChatMessageByUser(
                            ChatMessageByUserKey(
                                    msg.key.msgId,
                                    msg.key.userId,
                                    msg.key.topicId,
                                    msg.key.timestamp
                            ),
                            msg.value,
                            msg.visible

                    ))
                    .insert(ChatMessageByTopic(
                            ChatMessageByTopicKey(
                                    msg.key.msgId,
                                    msg.key.userId,
                                    msg.key.topicId,
                                    msg.key.timestamp
                            ),
                            msg.value,
                            msg.visible
                    ))
                    .execute()
                    .map {
                        if (!it.wasApplied())
                            throw ChatException("Cannot Add Message")
                    }
                    .then()
}