package com.demo.chat.domain.cassandra

import com.demo.chat.domain.TextMessage
import com.demo.chat.domain.TextMessageKey
import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.*
import java.time.Instant
import java.util.*

open class ChatMessage<K : TextMessageKey>(
        @PrimaryKey
        override val key: K,
        @Column("text")
        override val value: String,
        @Column("visible")
        override val visible: Boolean
) : TextMessage


@Table("chat_message_user")
class ChatMessageByUser(key: ChatMessageByUserKey,
                        value: String,
                        visible: Boolean) : ChatMessage<ChatMessageByUserKey>(key, value, visible)

@Table("chat_message_id")
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
        override val id: UUID,
        @Column("user_id")
        override val userId: UUID,
        @Column("topic_id")
        override val topicId: UUID,
        @Column("msg_time")
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
        @Column("user_id")
        override val userId: UUID,
        @PrimaryKeyColumn(name = "topic_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
        override val topicId: UUID,
        @Column("msg_time")
        override val timestamp: Instant
) : TextMessageKey