package com.demo.chat.domain.cassandra

import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.*
import java.time.Instant

@Table("chat_message_user")
class ChatMessageByUser<T>(@PrimaryKey override val key: ChatMessageByUserKey<T>,
                           @Column("text") override val data: String,
                           @Column("visible") override val record: Boolean) : Message<T, String>


@Table("chat_message_id")
data class ChatMessageById<T>( @PrimaryKey override val key: ChatMessageByIdKey<T>,
                               @Column("text") override val data: String,
                               @Column("visible") override val record: Boolean) : Message<T, String>

@Table("chat_message_topic")
data class ChatMessageByTopic<T>(@PrimaryKey override val key: ChatMessageByTopicKey<T>,
                                 @Column("text") override val data: String,
                                 @Column("visible") override val record: Boolean) : Message<T, String>

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