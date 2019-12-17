package com.demo.chat.domain.cassandra

import com.demo.chat.domain.TextMessage
import com.demo.chat.domain.UserMessageKey
import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.*
import java.time.Instant

open class ChatMessage<T>(
        @PrimaryKey
        override val key: UserMessageKey<T>,
        @Column("text")
        override val data: String,
        @Column("visible")
        override val visible: Boolean
) : TextMessage<T>

@Table("chat_message_user")
class ChatMessageByUser<T>(key: UserMessageKey<T>,
                           value: String,
                           visible: Boolean) : ChatMessage<T>(key, value, visible)

@Table("chat_message_id")
class ChatMessageById<T>(key: UserMessageKey<T>,
                         value: String,
                         visible: Boolean) : ChatMessage<T>(key, value, visible)

@Table("chat_message_topic")
class ChatMessageByTopic<T>(key: UserMessageKey<T>,
                            value: String,
                            visible: Boolean) : ChatMessage<T>(key, value, visible)

@PrimaryKeyClass
data class ChatMessageByIdKey<T>(
        @PrimaryKeyColumn(name = "msg_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
        override val id: T,
        @Column("user_id")
        override val userId: T,
        @Column("topic_id")
        override val dest: T,
        @Column("msg_time")
        override val timestamp: Instant
) : UserMessageKey<T>

@PrimaryKeyClass
data class ChatMessageByUserKey<T>(
        @PrimaryKeyColumn(name = "msg_id", type = PrimaryKeyType.CLUSTERED, ordinal = 1)
        override val id: T,
        @PrimaryKeyColumn(name = "user_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
        override val userId: T,
        @Column("topic_id")
        override val dest: T,
        @Column("msg_time")
        override val timestamp: Instant
) : UserMessageKey<T>

@PrimaryKeyClass
data class ChatMessageByTopicKey<T>(
        @PrimaryKeyColumn(name = "msg_id", type = PrimaryKeyType.CLUSTERED, ordinal = 1)
        override val id: T,
        @Column("user_id")
        override val userId: T,
        @PrimaryKeyColumn(name = "topic_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
        override val dest: T,
        @Column("msg_time")
        override val timestamp: Instant
) : UserMessageKey<T>