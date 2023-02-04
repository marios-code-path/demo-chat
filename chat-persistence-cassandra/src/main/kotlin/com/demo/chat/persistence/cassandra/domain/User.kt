package com.demo.chat.persistence.cassandra.domain

import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import org.springframework.data.annotation.Transient
import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.*
import java.time.Instant

@Table("chat_user")
data class ChatUser<T>(
    @PrimaryKey
    override val key: ChatUserKey<T>,
    @field:Column("name")
    override val name: String,
    @field:Column("handle")
    override val handle: String,
    @field:Column("image_uri")
    override val imageUri: String,
    @field:Column("timestamp")
    override val timestamp: Instant
) : User<T>

@PrimaryKeyClass
data class ChatUserKey<T>(
    @PrimaryKeyColumn(name = "user_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
    override val id: T
) : Key<T> {
    @Transient
    override val empty: Boolean = false
}