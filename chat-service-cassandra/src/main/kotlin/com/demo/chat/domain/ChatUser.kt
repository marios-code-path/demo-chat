package com.demo.chat.domain

import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.PrimaryKey
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn
import org.springframework.data.cassandra.core.mapping.Table
import java.util.*

@Table("chat_user")
data class ChatUser(
        @PrimaryKey
        @PrimaryKeyColumn(type = PrimaryKeyType.PARTITIONED, ordinal = 1)
        var id: UUID,
        val handle: String,
        val name: String,
        val timestamp: Date
)
