package com.demo.chat.domain

import com.fasterxml.jackson.annotation.JsonTypeName
import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.*
import java.time.Instant
import java.util.*

@Table("chat_room")
data class ChatRoom(
        @PrimaryKey
        override val key: ChatRoomKey,
        override val members: Set<UUID>?,
        val active: Boolean,
        override val timestamp: Instant
) : Room

@PrimaryKeyClass
data class ChatRoomKey(
        @PrimaryKeyColumn(name = "room_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
        override val id: UUID,
        override val name: String
) : RoomKey

@Table("chat_room_name")
data class ChatRoomName(
        @PrimaryKey
        override val key: ChatRoomNameKey,
        override val members: Set<UUID>?,
        val active: Boolean,
        override val timestamp: Instant
) : Room

@PrimaryKeyClass
data class ChatRoomNameKey(
        @PrimaryKeyColumn(name = "room_id", type = PrimaryKeyType.CLUSTERED, ordinal = 1)
        override val id: UUID,
        @PrimaryKeyColumn(name = "name", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
        override val name: String
) : RoomKey
