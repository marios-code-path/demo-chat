package com.demo.chat.domain

import org.springframework.data.cassandra.core.cql.Ordering
import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.*
import java.time.Instant
import java.util.*

@Table("chat_message")
data class ChatMessage(
        @PrimaryKey
        override val key: ChatMessageKey,
        @Column("text")
        override val value: String,
        override val visible: Boolean
) : TextMessage

@PrimaryKeyClass
class ChatMessageKey(
        @PrimaryKeyColumn(name = "msg_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
        override val id: UUID,
        @Column("user_id")
        override val userId: UUID,
        @Column("room_id")
        override val roomId: UUID,
        @PrimaryKeyColumn(name = "msg_time", type = PrimaryKeyType.CLUSTERED, ordinal = 1)
        override val timestamp: Instant
) : TextMessageKey

// ChatMessage By User
@Table("chat_message_user")
data class ChatMessageUser(
        @PrimaryKey
        override val key: ChatMessageUserKey,
        @Column("text")
        override val value: String,
        override val visible: Boolean
) : TextMessage

@PrimaryKeyClass
data class ChatMessageUserKey(
        @PrimaryKeyColumn(name = "msg_id", type = PrimaryKeyType.CLUSTERED, ordinal = 1)
        override val id: UUID,
        @PrimaryKeyColumn(name = "user_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
        override val userId: UUID,
        @Column("room_id")
        override val roomId: UUID,
        @Column("msg_time")
        override val timestamp: Instant
) : TextMessageKey

// ChatMessage By User
@Table("chat_message_room")
data class ChatMessageRoom(
        @PrimaryKey
        override val key: ChatMessageRoomKey,
        @Column("text")
        override val value: String,
        override val visible: Boolean
) : TextMessage

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
) : TextMessageKey