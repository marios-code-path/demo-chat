package com.demo.chat.domain.cassandra

import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageTopic
import org.springframework.data.annotation.Transient
import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.PrimaryKey
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn
import org.springframework.data.cassandra.core.mapping.Table
import java.util.*

@Table("chat_room")
data class ChatMessageTopic(
        @PrimaryKey
        override val key: ChatTopicKey,
        override val data: String,
        val active: Boolean
) : MessageTopic<UUID>

@Table("chat_room_name")
data class ChatMessageTopicName(
        @PrimaryKey
        override val key: ChatRoomNameKey,
        val active: Boolean
) : MessageTopic<UUID>
{
        @Transient
        override val data: String = key.name
}

@PrimaryKeyClass
data class ChatTopicKey(
        @PrimaryKeyColumn(name = "room_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
        override val id: UUID
) : Key<UUID>

@PrimaryKeyClass
data class ChatRoomNameKey(
        @PrimaryKeyColumn(name = "room_id", type = PrimaryKeyType.CLUSTERED, ordinal = 1)
        override val id: UUID,
        @PrimaryKeyColumn(name = "name", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
        val name: String
) : Key<UUID>