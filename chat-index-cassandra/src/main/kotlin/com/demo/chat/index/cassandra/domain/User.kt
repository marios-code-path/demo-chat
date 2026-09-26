package com.demo.chat.index.cassandra.domain

import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.*
import java.time.Instant


/**
 * A row of a Cassandra index. **It is not a domain object and not a `Key`.**
 * The index maps it under the root of its domain. See `CHAT-avduuqwp`.
 */
@Table("chat_user_handle")
data class ChatUserHandle<T>(
    @PrimaryKey
    val key: ChatUserHandleKey<T>,
    @field:Column("name")
    val name: String,
    @field:Column("image_uri")
    val imageUri: String,
    @field:Column("timestamp")
    val timestamp: Instant
)

@PrimaryKeyClass
data class ChatUserHandleKey<T>(
    @field:Column("user_id")
    val id: T,
    @PrimaryKeyColumn(name = "handle", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
    val handle: String
)