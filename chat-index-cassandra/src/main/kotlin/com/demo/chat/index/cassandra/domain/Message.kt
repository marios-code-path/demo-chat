package com.demo.chat.index.cassandra.domain

import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.*
import java.time.Instant

/**
 * A row of a Cassandra index. **It is not a domain object and not a `Key`.**
 * The index maps it under the root of its domain. See `CHAT-avduuqwp`.
 */
@Table("chat_message_user")
class ChatMessageByUser<T>(@PrimaryKey val key: ChatMessageByUserKey<T>,
                           @field:Column("text") val data: String,
                           @field:Column("visible") val record: Boolean)

@Table("chat_message_topic")
data class ChatMessageByTopic<T>(@PrimaryKey val key: ChatMessageByTopicKey<T>,
                                 @field:Column("text") val data: String,
                                 @field:Column("visible") val record: Boolean)

@PrimaryKeyClass
data class ChatMessageByUserKey<T>(
    @PrimaryKeyColumn(name = "msg_id", type = PrimaryKeyType.CLUSTERED, ordinal = 1)
    val id: T,
    @PrimaryKeyColumn(name = "user_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
    val from: T,
    @field:Column("topic_id")
    val dest: T,
    @field:Column("msg_time")
    val timestamp: Instant
)

@PrimaryKeyClass
data class ChatMessageByTopicKey<T>(
    @PrimaryKeyColumn(name = "msg_id", type = PrimaryKeyType.CLUSTERED, ordinal = 1)
    val id: T,
    @field:Column("user_id")
    val from: T,
    @PrimaryKeyColumn(name = "topic_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
    val dest: T,
    @field:Column("msg_time")
    val timestamp: Instant
)
