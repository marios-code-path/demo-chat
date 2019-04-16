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
        var key: T
        val text: String
        val visible: Boolean
}

@Table("chat_message")
data class ChatMessage(
         @PrimaryKey
         var key: ChatMessageKey,
         val text: String,
         val visible: Boolean
)

@PrimaryKeyClass
data class ChatMessageKey(
        @PrimaryKeyColumn(name = "msg_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
         val id: UUID,
        @Column("user_id")
         val userId: UUID,
        @Column("room_id")
         val roomId: UUID,
        @PrimaryKeyColumn(name = "msg_time", type = PrimaryKeyType.CLUSTERED, ordinal = 1)
         val timestamp: Instant
)

// ChatMessage By User
@Table("chat_message_user")
data class ChatMessageUser(
        @PrimaryKey
         var key: ChatMessageUserKey,
         val text: String,
         val visible: Boolean
)

@PrimaryKeyClass
data class ChatMessageUserKey(
        @PrimaryKeyColumn(name = "msg_id", type = PrimaryKeyType.CLUSTERED, ordinal = 1)
         val id: UUID,
        @PrimaryKeyColumn(name = "user_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
         val userId: UUID,
        @Column("room_id")
         val roomId: UUID,
        @Column("msg_time")
         val timestamp: Instant
)

// ChatMessage By User
@Table("chat_message_room")
data class ChatMessageRoom(
        @PrimaryKey
        override var key: ChatMessageRoomKey,
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