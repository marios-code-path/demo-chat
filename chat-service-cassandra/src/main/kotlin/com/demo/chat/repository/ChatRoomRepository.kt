package com.demo.chat.repository

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
    fun saveRoom(room: ChatRoom): Mono<ChatRoom>
    fun saveRooms(rooms: Flux<ChatRoom>): Flux<ChatRoom>
    fun joinRoom(uid: UUID, roomId: UUID): Mono<Void>
    fun leaveRoom(uid: UUID, roomId: UUID): Mono<Void>
    fun roomSize(roomId: UUID): Mono<RoomInfo>
}

class ChatRoomRepositoryCustomImpl(val template: ReactiveCassandraTemplate) :
        ChatRoomRepositoryCustom {
    override fun saveRooms(rooms: Flux<ChatRoom>): Flux<ChatRoom> =
            rooms
                    .flatMap {
                        saveRoom(it)
                    }


    override fun saveRoom(room: ChatRoom): Mono<ChatRoom> = template
            .batchOps()
            .insert(room)
            .insert(
                    ChatRoomName(
                            ChatRoomNameKey(
                                    room.key.roomId,
                                    room.key.name),
                            room.members,
                            room.timestamp
                    )
            )
            .execute()
            .thenReturn(room)

    override fun leaveRoom(uid: UUID, roomId: UUID): Mono<Void> = template
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
            template
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

    override fun roomSize(roomId: UUID): Mono<RoomInfo> =
            template
                    .select(Query.query(where("room_id").`is`(roomId)), ChatRoom::class.java)
                    .map {
                        it.members
                    }
                    .defaultIfEmpty(Collections.emptySet())
                    .zipWith(
                            template
                                    .select(Query.query(where("room_id").`is`(roomId)), ChatMessage::class.java)
                                    .count()
                    )
                    .map {
                        RoomInfo(it.t1.size, -1, it.t2.toInt())
                    }
                    .single()
}