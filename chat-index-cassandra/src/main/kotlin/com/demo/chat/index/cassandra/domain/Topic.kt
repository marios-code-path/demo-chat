package com.demo.chat.index.cassandra.domain

import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.*

/**
 * A row of a Cassandra index. **It is not a domain object and not a `Key`.**
 * The index maps it under the root of its domain. See `CHAT-avduuqwp`.
 */
@Table("chat_room_name")
data class ChatTopicName<T>(
    @PrimaryKey
    val key: ChatTopicNameKey<T>,
    val active: Boolean
)

@PrimaryKeyClass
data class ChatTopicNameKey<T>(
    @PrimaryKeyColumn(name = "room_id", type = PrimaryKeyType.CLUSTERED, ordinal = 1)
    val id: T,
    @PrimaryKeyColumn(name = "name", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
    val name: String
)
