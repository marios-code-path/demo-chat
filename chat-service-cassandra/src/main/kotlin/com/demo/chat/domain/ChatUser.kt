package com.demo.chat.domain

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
        @Column("timestamp")
        override val timestamp: Instant
) : User<UserKey>

@PrimaryKeyClass
data class ChatUserKey(
        @PrimaryKeyColumn(name="user_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
        override val userId: UUID,
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
) : User<UserKey>

@PrimaryKeyClass
data class ChatUserHandleKey(
        @Column("user_id")
        override val userId: UUID,
        @PrimaryKeyColumn(name = "handle", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
        override val handle: String
) : UserKey