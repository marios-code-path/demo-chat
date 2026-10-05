package com.demo.chat.persistence.cassandra.domain

import org.springframework.data.cassandra.core.cql.Ordering
import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.*
import java.time.Instant

/**
 * A row of the Cassandra backend. **It is not a domain object and not a
 * `Key`.** The store maps it under the root of its domain. See `CHAT-avduuqwp`.
 *
 * `msg_time` is a clustering column, so the key maps it as one. See
 * `CHAT-xcmpudyb`.
 */
@Table("chat_message_id")
data class ChatMessageById<T>(
    @PrimaryKey val key: ChatMessageByIdKey<T>,
    @field:Column("text") val data: String,
    @field:Column("visible") val record: Boolean
)

@PrimaryKeyClass
data class ChatMessageByIdKey<T>(
    @PrimaryKeyColumn(name = "msg_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
    val id: T,
    @field:Column("user_id")
    val from: T,
    @field:Column("topic_id")
    val dest: T,
    @PrimaryKeyColumn(name = "msg_time", type = PrimaryKeyType.CLUSTERED, ordinal = 1, ordering = Ordering.DESCENDING)
    val timestamp: Instant,
)