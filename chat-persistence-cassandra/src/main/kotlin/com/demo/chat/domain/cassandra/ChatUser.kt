package com.demo.chat.domain.cassandra

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
    @Column("name")
    override val name: String,
    @Column("handle")
    override val handle: String,
    @Column("image_uri")
    override val imageUri: String,
    @Column("timestamp")
    override val timestamp: Instant
) : User<T>

@PrimaryKeyClass
data class ChatUserKey<T>(
    @PrimaryKeyColumn(name = "user_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
    override val id: T
) : Key<T>

@Table("chat_user_handle")
data class ChatUserHandle<T>(
    @PrimaryKey
    override val key: ChatUserHandleKey<T>,
    @Column("name")
    override val name: String,
    @Column("image_uri")
    override val imageUri: String,
    @Column("timestamp")
    override val timestamp: Instant
) : User<T> {
    @Transient
    override val handle: String = key.handle
}

@PrimaryKeyClass
data class ChatUserHandleKey<T>(
    @Column("user_id")
    override val id: T,
    @PrimaryKeyColumn(name = "handle", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
    val handle: String
) : Key<T>