package com.demo.chat.repository.cassandra

import com.demo.chat.domain.*
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.data.cassandra.core.query.ColumnName
import org.springframework.data.cassandra.core.query.Query
import org.springframework.data.cassandra.core.query.Update
import org.springframework.data.cassandra.core.query.where
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*


interface ChatRoomNameRepository : ReactiveCassandraRepository<ChatRoomName, String> {
    fun findByKeyName(name: String): Flux<ChatRoomName>
}

interface ChatRoomRepository :
        ReactiveCassandraRepository<ChatRoom, UUID>,
        ChatRoomRepositoryCustom {
    fun findByKeyRoomId(id: UUID): Mono<ChatRoom>
}

interface ChatRoomRepositoryCustom {
    fun saveRoom(room: Room): Mono<Void>
    fun remRoom(roomKey: RoomKey): Mono<Void>
    fun joinRoom(uid: UUID, roomId: UUID): Mono<Void>
    fun leaveRoom(uid: UUID, roomId: UUID): Mono<Void>
    fun messageCount(roomId: UUID): Mono<Int>

}

class ChatRoomRepositoryCustomImpl(val cassandra: ReactiveCassandraTemplate) :
        ChatRoomRepositoryCustom {
    override fun remRoom(roomKey: RoomKey): Mono<Void> =
            cassandra
                    .batchOps()
                    .update(Query.query(where("room_id").`is`(roomKey.roomId)),
                            Update.empty().set("active", false),
                            ChatRoom::class.java
                    )
                    .update(Query.query(where("room_id").`is`(roomKey.roomId)),
                            Update.empty().set("active", false),
                            ChatRoomName::class.java
                    )
                    .execute()
                    .map {
                        if (!it.wasApplied())
                            throw ChatException("Cannot De-Active Room")
                    }
                    .then(
                    )

    override fun saveRoom(room: Room): Mono<Void> = cassandra
            .batchOps()
            .insert(
                    ChatRoomName(
                            ChatRoomNameKey(
                                    room.key.roomId,
                                    room.key.name),
                            emptySet(),
                            true,
                            room.timestamp
                    )
            )
            .insert(ChatRoom(
                    ChatRoomKey(
                            room.key.roomId,
                            room.key.name
                    ),
                    emptySet(),
                    true,
                    room.timestamp
            ))
            .execute()
            .then()

    override fun leaveRoom(uid: UUID, roomId: UUID): Mono<Void> = cassandra
            .update(Query.query(where("room_id").`is`(roomId)),
                    Update.of(listOf(Update.RemoveOp(
                            ColumnName.from("members"),
                            listOf(uid)))),
                    ChatRoom::class.java
            )
            .map {
                if (!it)
                    throw RoomJoinLeaveException
            }
            .then()

    override fun joinRoom(uid: UUID, roomId: UUID): Mono<Void> =
            cassandra
                    .update(Query.query(where("room_id").`is`(roomId)),
                            Update.of(listOf(Update.AddToOp(
                                    ColumnName.from("members"),
                                    listOf(uid),
                                    Update.AddToOp.Mode.APPEND))),
                            ChatRoom::class.java
                    )
                    .map {
                        if (!it)
                            throw RoomJoinLeaveException
                    }
                    .then()

    override fun messageCount(roomId: UUID): Mono<Int> =
            cassandra
                    .select(Query.query(where("room_id").`is`(roomId)), ChatRoom::class.java)
                    .map {
                        it.members
                    }
                    .defaultIfEmpty(Collections.emptySet())
                    .zipWith(
                            cassandra
                                    .select(Query.query(where("room_id").`is`(roomId)), ChatMessageById::class.java)
                                    .count()
                    )
                    .map {
                        it.t1.size
                    }
                    .single()
}