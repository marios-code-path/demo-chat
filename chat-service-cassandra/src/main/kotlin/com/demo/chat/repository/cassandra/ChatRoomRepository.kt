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


interface ChatRoomNameRepository : ReactiveCassandraRepository<ChatEventTopicName, String> {
    fun findByKeyName(name: String): Mono<ChatEventTopicName>
}

interface ChatRoomRepository :
        ReactiveCassandraRepository<ChatEventTopic, UUID>,
        ChatRoomRepositoryCustom
{
    fun findByKeyId(id: UUID): Mono<ChatEventTopic>
}

interface ChatRoomRepositoryCustom {
    fun add(eventTopic: EventTopic): Mono<Void>
    fun rem(roomKey: UUIDKey): Mono<Void>
    @Deprecated("join/leave not part of topic tracking")
    fun join(uid: UUID, roomId: UUID): Mono<Void>
    @Deprecated("join/leave not part of topic tracking")
    fun leave(uid: UUID, roomId: UUID): Mono<Void>
    @Deprecated("message count not supported per topic")
    fun messageCount(roomId: UUID): Mono<Int>
}

class ChatRoomRepositoryCustomImpl(val cassandra: ReactiveCassandraTemplate) :
        ChatRoomRepositoryCustom {
    override fun rem(roomKey: UUIDKey): Mono<Void> =
            cassandra
                    .update(Query.query(where("room_id").`is`(roomKey.id)),
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

    override fun leave(uid: UUID, roomId: UUID): Mono<Void> = cassandra
            .update(Query.query(where("room_id").`is`(roomId)),
                    Update.of(listOf(Update.RemoveOp(
                            ColumnName.from("members"),
                            listOf(uid)))),
                    ChatEventTopic::class.java
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
                            ChatEventTopic::class.java
                    )
                    .map {
                        if (!it)
                            throw RoomJoinLeaveException
                    }
                    .then()

    override fun messageCount(roomId: UUID): Mono<Int> =
            Mono.empty()
}