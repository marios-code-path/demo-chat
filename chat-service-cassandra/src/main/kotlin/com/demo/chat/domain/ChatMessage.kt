package com.demo.chat.domain

import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.PrimaryKey
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn
import org.springframework.data.cassandra.core.mapping.Table
import java.time.Instant
import java.util.*

@Table("chat_message")
data class ChatMessage(
        @PrimaryKey
        var key: ChatMessageKey,
        val text: String,
        val visible: Boolean
)

@PrimaryKeyClass
data class ChatMessageKey(
        @PrimaryKeyColumn(name = "id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
        val id: UUID,
        @PrimaryKeyColumn(name = "user_id", type = PrimaryKeyType.PARTITIONED, ordinal = 2)
        val userId: UUID,
        @PrimaryKeyColumn(name = "room_id", type = PrimaryKeyType.PARTITIONED, ordinal = 1)
        val roomId: UUID,
        @PrimaryKeyColumn(name = "msg_time", type = PrimaryKeyType.CLUSTERED, ordinal = 3)
        val timestamp: Instant
)