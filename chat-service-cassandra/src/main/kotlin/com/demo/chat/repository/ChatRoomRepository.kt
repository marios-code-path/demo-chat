package com.demo.chat.repository

import com.demo.chat.domain.ChatRoom
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.data.cassandra.core.query.ColumnName
import org.springframework.data.cassandra.core.query.Query
import org.springframework.data.cassandra.core.query.Update
import org.springframework.data.cassandra.core.query.where
import org.springframework.data.cassandra.repository.AllowFiltering
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

interface ChatRoomRepository :
        ReactiveCassandraRepository<ChatRoom, UUID>,
        ChatRoomRepositoryCustom {
    @AllowFiltering
    fun findByName(name: String): Flux<ChatRoom>
}

interface ChatRoomRepositoryCustom {
    fun joinRoom(uid: UUID, roomId: UUID): Mono<Boolean>
    fun leaveRoom(uid: UUID, roomId: UUID): Mono<Boolean>
}

class ChatRoomRepositoryCustomImpl(val template: ReactiveCassandraTemplate) :
        ChatRoomRepositoryCustom {

    override fun leaveRoom(uid: UUID, roomId: UUID): Mono<Boolean> =
            template
                    .update(Query.query(where("id").`is`(roomId)),
                            Update.of(listOf(Update.RemoveOp(
                                    ColumnName.from("members"),
                                    listOf(uid)))),
                            ChatRoom::class.java
                    )
                    .defaultIfEmpty(false)

    override fun joinRoom(uid: UUID, roomId: UUID): Mono<Boolean> =
            template
                    .update(Query.query(where("id").`is`(roomId)),
                            Update.of(listOf(Update.AddToOp(
                                    ColumnName.from("members"),
                                    listOf(uid),
                                    Update.AddToOp.Mode.APPEND))),
                            ChatRoom::class.java
                    )
                    .defaultIfEmpty(false)


}