package com.demo.chat.domain

import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.PrimaryKey
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn
import org.springframework.data.cassandra.core.mapping.Table
import java.time.Instant
import java.util.*

interface Room<T : RoomKey> {
    val key: T
    val members: Set<UUID>?
    val timestamp: Instant

}

interface RoomKey {
    val roomId: UUID
    val name: String
}

@Table("chat_room")
data class ChatRoom(
        @PrimaryKey
        override val key: ChatRoomKey,
        override val members: Set<UUID>?,
        override val timestamp: Instant
) : Room<ChatRoomKey>

@PrimaryKeyClass
data class ChatRoomKey(
        @PrimaryKeyColumn(name = "room_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
        override val roomId: UUID,
        override val name: String
) : RoomKey

@Table("chat_room_name")
data class ChatRoomName(
        @PrimaryKey
        override val key: ChatRoomNameKey,
        override val members: Set<UUID>?,
        override val timestamp: Instant
) : Room<ChatRoomNameKey>

@PrimaryKeyClass
data class ChatRoomNameKey(
        @PrimaryKeyColumn(name = "room_id", type = PrimaryKeyType.CLUSTERED, ordinal = 1)
        override val roomId: UUID,
        @PrimaryKeyColumn(name = "name", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
        override val name: String
) : RoomKey
