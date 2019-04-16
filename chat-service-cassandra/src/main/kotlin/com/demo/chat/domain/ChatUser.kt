package com.demo.chat.domain

import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.*
import java.time.Instant
import java.util.*

interface User<T : UserKey> {
    val key: T
    val name: String
    val timestamp: Instant
}

interface UserKey {
    var userId: UUID
    val handle: String
}

@Table("chat_user")
data class ChatUser(
        @PrimaryKey
        override val key: ChatUserKey,
        @Column("name")
        override val name: String,
        @Column("timestamp")
        override val timestamp: Instant
) : User<ChatUserKey>

@PrimaryKeyClass
data class ChatUserKey(
        @PrimaryKeyColumn(name="user_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
        override var userId: UUID,
        @Column("handle")
        override val handle: String
) : UserKey


@Table("chat_user_handle")
data class ChatUserHandle(
        @PrimaryKey
        override val key: ChatUserHandleKey,
        @Column("name")
        override val name: String,
        @Column("timestamp")
        override val timestamp: Instant
) : User<ChatUserHandleKey>

@PrimaryKeyClass
data class ChatUserHandleKey(
        @Column("user_id")
        override var userId: UUID,
        @PrimaryKeyColumn(name = "handle", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
        override val handle: String
) : UserKey