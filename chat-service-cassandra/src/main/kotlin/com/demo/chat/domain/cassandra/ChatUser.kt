package com.demo.chat.domain.cassandra

import com.demo.chat.domain.User
import com.demo.chat.domain.UserKey
import org.springframework.data.annotation.Transient
import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.*
import java.time.Instant
import java.util.*

@Table("chat_user")
data class ChatUser(
        @PrimaryKey
        override val key: ChatUserKey,
        @Column("name")
        override val name: String,
        @Column("handle")
        override val handle: String,
        @Column("image_uri")
        override val imageUri: String,
        @Column("timestamp")
        override val timestamp: Instant
) : User

@PrimaryKeyClass
data class ChatUserKey(
        @PrimaryKeyColumn(name = "user_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
        override val id: UUID
) : UserKey

@Table("chat_user_handle")
data class ChatUserHandle(
        @PrimaryKey
        override val key: ChatUserHandleKey,
        @Column("name")
        override val name: String,
        @Column("image_uri")
        override val imageUri: String,
        @Column("timestamp")
        override val timestamp: Instant
) : User {
    @Transient
    override val handle: String = key.handle
}

@PrimaryKeyClass
data class ChatUserHandleKey(
        @Column("user_id")
        override val id: UUID,
        @PrimaryKeyColumn(name = "handle", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
        val handle: String
) : UserKey