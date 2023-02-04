package com.demo.chat.index.cassandra.domain

import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageTopic
import org.springframework.data.annotation.Transient
import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.*


@Table("chat_room_name")
data class ChatTopicName<T>(
    @PrimaryKey
    override val key: ChatTopicNameKey<T>,
    val active: Boolean
) : MessageTopic<T> {
    @Transient
    override val data: String = key.name
}

@PrimaryKeyClass
data class ChatTopicNameKey<T>(
    @PrimaryKeyColumn(name = "room_id", type = PrimaryKeyType.CLUSTERED, ordinal = 1)
    override val id: T,
    @PrimaryKeyColumn(name = "name", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
    val name: String
) : Key<T> {
    @Transient
    override val empty: Boolean = false
}