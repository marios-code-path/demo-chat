package com.demo.chat.domain.cassandra

import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.*
import java.time.Instant

open class ChatMessage<T>(
        @PrimaryKey
        override val key: MessageKey<T>,
        @Column("text")
        override val data: String,
        @Column("visible")
        override val record: Boolean
) : Message<T, String>

@Table("chat_message_user")
class ChatMessageByUser<T>(key: ChatMessageByUserKey<T>,
                           value: String,
                           visible: Boolean) : ChatMessage<T>(key, value, visible)

@Table("chat_message_id")
class ChatMessageById<T>(key: ChatMessageByIdKey<T>,
                         value: String,
                         visible: Boolean) : ChatMessage<T>(key, value, visible)

@Table("chat_message_topic")
class ChatMessageByTopic<T>(key: ChatMessageByTopicKey<T>,
                            value: String,
                            visible: Boolean) : ChatMessage<T>(key, value, visible)

@PrimaryKeyClass
data class ChatMessageByIdKey<T>(
        @PrimaryKeyColumn(name = "msg_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
        override val id: T,
        @Column("user_id")
        override val from: T,
        @Column("topic_id")
        override val dest: T,
        @Column("msg_time")
        override val timestamp: Instant
) : MessageKey<T>

@PrimaryKeyClass
data class ChatMessageByUserKey<T>(
        @PrimaryKeyColumn(name = "msg_id", type = PrimaryKeyType.CLUSTERED, ordinal = 1)
        override val id: T,
        @PrimaryKeyColumn(name = "user_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
        override val from: T,
        @Column("topic_id")
        override val dest: T,
        @Column("msg_time")
        override val timestamp: Instant
) : MessageKey<T>

@PrimaryKeyClass
data class ChatMessageByTopicKey<T>(
        @PrimaryKeyColumn(name = "msg_id", type = PrimaryKeyType.CLUSTERED, ordinal = 1)
        override val id: T,
        @Column("user_id")
        override val from: T,
        @PrimaryKeyColumn(name = "topic_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
        override val dest: T,
        @Column("msg_time")
        override val timestamp: Instant
) : MessageKey<T>