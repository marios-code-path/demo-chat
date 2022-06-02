package com.demo.chat.domain.cassandra

import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.*
import java.time.Instant

@Table("chat_message_id")
data class ChatMessageById<T>( @PrimaryKey override val key: ChatMessageByIdKey<T>,
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