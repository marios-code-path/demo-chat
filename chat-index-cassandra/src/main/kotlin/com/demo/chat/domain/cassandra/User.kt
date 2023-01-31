package com.demo.chat.domain.cassandra

import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import org.springframework.data.annotation.Transient
import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.*
import java.time.Instant


@Table("chat_user_handle")
data class ChatUserHandle<T>(
    @PrimaryKey
    override val key: ChatUserHandleKey<T>,
    @field:Column("name")
    override val name: String,
    @field:Column("image_uri")
    override val imageUri: String,
    @field:Column("timestamp")
    override val timestamp: Instant
) : User<T> {
    @Transient
    override val handle: String = key.handle
}

@PrimaryKeyClass
data class ChatUserHandleKey<T>(
    @field:Column("user_id")
    override val id: T,
    @PrimaryKeyColumn(name = "handle", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
    val handle: String
) : Key<T> {
    @Transient
    override val empty: Boolean = false
}