package com.demo.chat.persistence.cassandra.domain

import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.*
import java.time.Instant

/**
 * A row of the Cassandra backend. **It is not a domain object and not a
 * `Key`.** The store maps it under the root of its domain. See `CHAT-avduuqwp`.
 */
@Table("chat_user")
data class ChatUser<T>(
    @PrimaryKey
    val key: ChatUserKey<T>,
    @field:Column("name")
    val name: String,
    @field:Column("handle")
    val handle: String,
    @field:Column("image_uri")
    val imageUri: String,
    @field:Column("timestamp")
    val timestamp: Instant
)

@PrimaryKeyClass
data class ChatUserKey<T>(
    @PrimaryKeyColumn(name = "user_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
    val id: T
)