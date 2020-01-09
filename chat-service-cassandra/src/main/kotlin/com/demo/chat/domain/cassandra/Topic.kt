package com.demo.chat.domain.cassandra

import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageTopic
import org.springframework.data.annotation.Transient
import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.*

@Table("chat_room")
data class ChatTopic<K>(
        @PrimaryKey
        override val key: ChatTopicKey<K>,
        @Column("name")
        override val data: String,
        val active: Boolean
) : MessageTopic<K>

@Table("chat_room_name")
data class ChatTopicName<K>(
        @PrimaryKey
        override val key: ChatTopicNameKey<K>,
        val active: Boolean
) : MessageTopic<K> {
    @Transient
    override val data: String = key.name
}

@PrimaryKeyClass
data class ChatTopicKey<K>(
        @PrimaryKeyColumn(name = "room_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
        override val id: K
) : Key<K>

@PrimaryKeyClass
data class ChatTopicNameKey<K>(
        @PrimaryKeyColumn(name = "room_id", type = PrimaryKeyType.CLUSTERED, ordinal = 1)
        override val id: K,
        @PrimaryKeyColumn(name = "name", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
        val name: String
) : Key<K>