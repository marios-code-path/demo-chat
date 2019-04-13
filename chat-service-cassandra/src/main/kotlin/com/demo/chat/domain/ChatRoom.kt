package com.demo.chat.domain

import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.PrimaryKey
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn
import org.springframework.data.cassandra.core.mapping.Table
import java.io.Serializable
import java.util.*


@Table("chat_room")
data class ChatRoom(
        @PrimaryKey
        @PrimaryKeyColumn(type = PrimaryKeyType.PARTITIONED, ordinal = 1)
        val id: UUID,
        val name: String,
        val members: Set<UUID>?,
        val timestamp: Date
) : Serializable

//@Table("chat_room")
//data class ChatRoom(
//        @PrimaryKey
//        val key : ChatRoomKey,
//        val members: Set<UUID>,
//        val timestamp: Date
//)
//
//@PrimaryKeyClass
//data class ChatRoomKey(
//        @PrimaryKeyColumn(type = PrimaryKeyType.PARTITIONED, ordinal = 1)
//        var id: UUID?,
//        @PrimaryKeyColumn(type = PrimaryKeyType.CLUSTERED, ordinal = 2)
//        val name: String
//
//)