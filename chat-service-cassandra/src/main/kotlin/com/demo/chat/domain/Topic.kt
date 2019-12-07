package com.demo.chat.domain

import org.springframework.data.annotation.Transient
import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.*
import java.util.*

@Table("chat_room")
data class ChatEventTopic(
        @PrimaryKey
        override val key: ChatTopicKey,
        override val name: String,
        val active: Boolean
) : EventTopic

@Table("chat_room_name")
data class ChatEventTopicName(
        @PrimaryKey
        override val key: ChatRoomNameKey,
        val active: Boolean
) : EventTopic
{
        @Transient
        override val name: String = key.name
}

@PrimaryKeyClass
data class ChatTopicKey(
        @PrimaryKeyColumn(name = "room_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
        override val id: UUID
) : TopicKey

@PrimaryKeyClass
data class ChatRoomNameKey(
        @PrimaryKeyColumn(name = "room_id", type = PrimaryKeyType.CLUSTERED, ordinal = 1)
        override val id: UUID,
        @PrimaryKeyColumn(name = "name", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
        val name: String
) : TopicKey
