package com.demo.chat.persistence.cassandra.domain

import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageTopic
import org.springframework.data.annotation.Transient
import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.*

@Table("chat_room")
data class ChatTopic<T>(
    @PrimaryKey
    override val key: ChatTopicKey<T>,
    @field:Column("name")
    override val data: String,
    val active: Boolean
) : MessageTopic<T>


@PrimaryKeyClass
data class ChatTopicKey<T>(
    @PrimaryKeyColumn(name = "room_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
    override val id: T
) : Key<T> {
    @Transient
    override val empty: Boolean = false
}