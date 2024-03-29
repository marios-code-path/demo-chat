package com.demo.chat.index.cassandra.domain

import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import org.springframework.data.annotation.Transient
import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.*
import java.time.Instant


@Table("chat_message_user")
class ChatMessageByUser<T>(@PrimaryKey override val key: ChatMessageByUserKey<T>,
                           @field:Column("text") override val data: String,
                           @field:Column("visible") override val record: Boolean) : Message<T, String>

@Table("chat_message_topic")
data class ChatMessageByTopic<T>(@PrimaryKey override val key: ChatMessageByTopicKey<T>,
                                 @field:Column("text") override val data: String,
                                 @field:Column("visible") override val record: Boolean) : Message<T, String>

@PrimaryKeyClass
data class ChatMessageByUserKey<T>(
    @PrimaryKeyColumn(name = "msg_id", type = PrimaryKeyType.CLUSTERED, ordinal = 1)
    override val id: T,
    @PrimaryKeyColumn(name = "user_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
    override val from: T,
    @field:Column("topic_id")
    override val dest: T,
    @field:Column("msg_time")
    override val timestamp: Instant
) : MessageKey<T> {
    @Transient
    override val empty: Boolean = false
}

@PrimaryKeyClass
data class ChatMessageByTopicKey<T>(
    @PrimaryKeyColumn(name = "msg_id", type = PrimaryKeyType.CLUSTERED, ordinal = 1)
    override val id: T,
    @field:Column("user_id")
    override val from: T,
    @PrimaryKeyColumn(name = "topic_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
    override val dest: T,
    @field:Column("msg_time")
    override val timestamp: Instant
) : MessageKey<T> {
    @Transient
    override val empty: Boolean = false
}