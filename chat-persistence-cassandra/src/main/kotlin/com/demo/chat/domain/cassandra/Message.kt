package com.demo.chat.domain.cassandra

import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import org.springframework.data.annotation.Transient
import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.*
import java.time.Instant

@Table("chat_message_id")
data class ChatMessageById<T>(
    @PrimaryKey override val key: ChatMessageByIdKey<T>,
    @field:Column("text") override val data: String,
    @field:Column("visible") override val record: Boolean
) : Message<T, String>

@PrimaryKeyClass
data class ChatMessageByIdKey<T>(
    @PrimaryKeyColumn(name = "msg_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
    override val id: T,
    @field:Column("user_id")
    override val from: T,
    @field:Column("topic_id")
    override val dest: T,
    @field:Column("msg_time")
    override val timestamp: Instant,
) : MessageKey<T> {
    @Transient
    override val empty: Boolean = false
}