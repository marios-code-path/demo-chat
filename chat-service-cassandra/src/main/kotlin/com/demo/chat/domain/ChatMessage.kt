package com.demo.chat.domain

import org.springframework.data.cassandra.core.cql.Ordering
import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.*
import java.time.Instant
import java.util.*

interface MessageKey {
        val id: UUID
        val userId: UUID
        val roomId: UUID
        val timestamp: Instant
}

interface Message<T : MessageKey> {
        val key: T
        val text: String
        val visible: Boolean
}

@Table("chat_message")
data class ChatMessage(
         @PrimaryKey
         override val key: ChatMessageKey,
         override val text: String,
         override val visible: Boolean
) : Message<MessageKey>

@PrimaryKeyClass
data class ChatMessageKey(
        @PrimaryKeyColumn(name = "msg_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
        override val id: UUID,
        @Column("user_id")
        override  val userId: UUID,
        @Column("room_id")
        override  val roomId: UUID,
        @PrimaryKeyColumn(name = "msg_time", type = PrimaryKeyType.CLUSTERED, ordinal = 1)
        override  val timestamp: Instant
) : MessageKey

// ChatMessage By User
@Table("chat_message_user")
data class ChatMessageUser(
        @PrimaryKey
        override val key: ChatMessageUserKey,
        override val text: String,
        override val visible: Boolean
) : Message<ChatMessageUserKey>

@PrimaryKeyClass
data class ChatMessageUserKey(
        @PrimaryKeyColumn(name = "msg_id", type = PrimaryKeyType.CLUSTERED, ordinal = 1)
        override  val id: UUID,
        @PrimaryKeyColumn(name = "user_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
        override  val userId: UUID,
        @Column("room_id")
        override  val roomId: UUID,
        @Column("msg_time")
        override  val timestamp: Instant
) : MessageKey

// ChatMessage By User
@Table("chat_message_room")
data class ChatMessageRoom(
        @PrimaryKey
        override val key: ChatMessageRoomKey,
        override val text: String,
        override val visible: Boolean
) : Message<ChatMessageRoomKey>

@PrimaryKeyClass
data class ChatMessageRoomKey(
        @PrimaryKeyColumn(name = "msg_id", type = PrimaryKeyType.CLUSTERED, ordinal = 1)
        override val id: UUID,
        @PrimaryKeyColumn(name = "user_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
        override val userId: UUID,
        @Column("room_id")
        override val roomId: UUID,
        @PrimaryKeyColumn(name = "msg_time", type = PrimaryKeyType.CLUSTERED, ordinal = 2, ordering = Ordering.DESCENDING)
        override val timestamp: Instant
) : MessageKey