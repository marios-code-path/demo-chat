package com.demo.chat.domain

import org.springframework.data.cassandra.core.cql.Ordering
import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.*
import java.time.Instant
import java.util.*

open class ChatMessageType<T : TextMessageKey>(
        @PrimaryKey
        override val key: T,
        @Column("text")
        override val value: String,
        @Column("visible")
        override val visible: Boolean
) : Message<T, String>

@PrimaryKeyClass
data class ChatMessageKey(
        @PrimaryKeyColumn(name = "msg_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
        override val id: UUID,
        @Column("user_id")
        override val userId: UUID,
        @Column("topic_id")
        override val topicId: UUID,
        @PrimaryKeyColumn(name = "msg_time", type = PrimaryKeyType.CLUSTERED, ordinal = 1)
        override val timestamp: Instant
) : TextMessageKey

@PrimaryKeyClass
data class ChatMessageByUserKey(
        @PrimaryKeyColumn(name = "msg_id", type = PrimaryKeyType.CLUSTERED, ordinal = 1)
        override val id: UUID,
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
        override val id: UUID,
        @PrimaryKeyColumn(name = "user_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
        override val userId: UUID,
        @Column("topic_id")
        override val topicId: UUID,
        @PrimaryKeyColumn(name = "msg_time", type = PrimaryKeyType.CLUSTERED, ordinal = 2, ordering = Ordering.DESCENDING)
        override val timestamp: Instant
) : TextMessageKey

@Table("chat_message_user")
class ChatMessageByUser(key: ChatMessageByUserKey,
                        value: String,
                        visible: Boolean) : ChatMessageType<ChatMessageByUserKey>(key, value, visible)

@Table("chat_message")
class ChatMessage(key: ChatMessageKey,
                  value: String,
                  visible: Boolean) : ChatMessageType<ChatMessageKey>(key, value, visible)

@Table("chat_message_topic")
class ChatMessageByTopic(key: ChatMessageByTopicKey,
                         value: String,
                         visible: Boolean) : ChatMessageType<ChatMessageByTopicKey>(key, value, visible)