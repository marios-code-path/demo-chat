package com.demo.chat.persistence.cassandra.domain

import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.*

/**
 * A row of the Cassandra backend. **It is not a domain object and not a
 * `Key`.** The store maps it under the root of its domain. See `CHAT-avduuqwp`.
 */
@Table("chat_room")
data class ChatTopic<T>(
    @PrimaryKey
    val key: ChatTopicKey<T>,
    @field:Column("name")
    val data: String,
    val active: Boolean
)


@PrimaryKeyClass
data class ChatTopicKey<T>(
    @PrimaryKeyColumn(name = "room_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
    val id: T
)