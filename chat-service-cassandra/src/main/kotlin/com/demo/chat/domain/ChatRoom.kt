package com.demo.chat.domain

import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.PrimaryKey
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn
import org.springframework.data.cassandra.core.mapping.Table
import java.io.Serializable
import java.time.Instant
import java.util.*


@Table("chat_room")
data class ChatRoom(
        @PrimaryKey
        @PrimaryKeyColumn(type = PrimaryKeyType.PARTITIONED, ordinal = 1)
        val id: UUID,
        val name: String,
        val members: Set<UUID>?,
        val timestamp: Instant
)