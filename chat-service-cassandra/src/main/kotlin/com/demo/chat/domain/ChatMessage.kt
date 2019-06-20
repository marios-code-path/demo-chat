package com.demo.chat.domain

import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.*
import java.time.Instant
import java.util.*

open class ChatMessage<T : TextMessageKey>(
        @PrimaryKey
        override val key: T,
        @Column("text")
        override val value: String,
        @Column("visible")
        override val visible: Boolean
) : Message<T, String>


@Table("chat_message_user")
class ChatMessageByUser(key: ChatMessageByUserKey,
                        value: String,
                        visible: Boolean) : ChatMessage<ChatMessageByUserKey>(key, value, visible)

@Table("chat_message")
class ChatMessageById(key: ChatMessageByIdKey,
                      value: String,
                      visible: Boolean) : ChatMessage<ChatMessageByIdKey>(key, value, visible)

@Table("chat_message_topic")
class ChatMessageByTopic(key: ChatMessageByTopicKey,
                         value: String,
                         visible: Boolean) : ChatMessage<ChatMessageByTopicKey>(key, value, visible)

@PrimaryKeyClass
data class ChatMessageByIdKey(
        @PrimaryKeyColumn(name = "msg_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
        override val msgId: UUID,
        @Column("user_id")
        override val userId: UUID,
        @Column("topic_id")
        override val topicId: UUID,
        override val timestamp: Instant
) : TextMessageKey

@PrimaryKeyClass
data class ChatMessageByUserKey(
        @PrimaryKeyColumn(name = "msg_id", type = PrimaryKeyType.CLUSTERED, ordinal = 1)
        override val msgId: UUID,
        @PrimaryKeyColumn(name = "user_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
        override val userId: UUID,
        @Column("topic_id")
        override val topicId: UUID,
        @Column("msg_time")
        override val timestamp: Instant
) : TextMessageKey

@PrimaryKeyClass
data class ChatMessageByTopicKey(
        @PrimaryKeyColumn(name = "msg_id", type = PrimaryKeyType.CLUSTERED, ordinal = 1)
        override val msgId: UUID,
        @Column("topic_id")
        override val userId: UUID,
        @PrimaryKeyColumn(name = "topic_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
        override val topicId: UUID,
        override val timestamp: Instant
) : TextMessageKey