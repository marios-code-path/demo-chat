package com.demo.chat.repository.cassandra

import com.demo.chat.domain.*
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.data.cassandra.core.query.ColumnName
import org.springframework.data.cassandra.core.query.Query
import org.springframework.data.cassandra.core.query.Update
import org.springframework.data.cassandra.core.query.where
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import reactor.core.publisher.Mono
import java.util.*


interface ChatRoomNameRepository : ReactiveCassandraRepository<ChatTopicName, String> {
    fun findByKeyName(name: String): Mono<ChatTopicName>
}

interface ChatRoomRepository :
        ReactiveCassandraRepository<ChatTopic, UUID>,
        ChatRoomRepositoryCustom
{
    fun findByKeyId(id: UUID): Mono<ChatTopic>
}

interface ChatRoomRepositoryCustom {
    fun add(topic: Topic): Mono<Void>
    fun rem(roomKey: EventKey): Mono<Void>
    @Deprecated("join/leave not part of topic tracking")
    fun join(uid: UUID, roomId: UUID): Mono<Void>
    @Deprecated("join/leave not part of topic tracking")
    fun leave(uid: UUID, roomId: UUID): Mono<Void>
    @Deprecated("message count not supported per topic")
    fun messageCount(roomId: UUID): Mono<Int>
}

class ChatRoomRepositoryCustomImpl(val cassandra: ReactiveCassandraTemplate) :
        ChatRoomRepositoryCustom {
    override fun rem(roomKey: EventKey): Mono<Void> =
            cassandra
                    .update(Query.query(where("room_id").`is`(roomKey.id)),
                            Update.empty().set("active", false),
                            ChatTopic::class.java
                    )
                    .then()

    override fun add(topic: Topic): Mono<Void> = cassandra
            .insert(ChatTopic(
                    ChatTopicKey(
                            topic.key.id
                    ),
                    topic.name,
                    true))
            .then()

    override fun leave(uid: UUID, roomId: UUID): Mono<Void> = cassandra
            .update(Query.query(where("room_id").`is`(roomId)),
                    Update.of(listOf(Update.RemoveOp(
                            ColumnName.from("members"),
                            listOf(uid)))),
                    ChatTopic::class.java
            )
            .map {
                if (!it)
                    throw RoomJoinLeaveException
            }
            .then()

    override fun join(uid: UUID, roomId: UUID): Mono<Void> =
            cassandra
                    .update(Query.query(where("room_id").`is`(roomId)),
                            Update.of(listOf(Update.AddToOp(
                                    ColumnName.from("members"),
                                    listOf(uid),
                                    Update.AddToOp.Mode.APPEND))),
                            ChatTopic::class.java
                    )
                    .map {
                        if (!it)
                            throw RoomJoinLeaveException
                    }
                    .then()

    override fun messageCount(roomId: UUID): Mono<Int> =
            Mono.empty()
}