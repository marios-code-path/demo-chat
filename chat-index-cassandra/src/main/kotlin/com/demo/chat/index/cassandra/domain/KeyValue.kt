package com.demo.chat.index.cassandra.domain

import com.demo.chat.domain.Key
import org.springframework.data.annotation.Transient
import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.PrimaryKey
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn
import org.springframework.data.cassandra.core.mapping.Table

/**
 * The read side of the key-value index. The partition is the field and the
 * value, so one query reads one partition.
 */
@Table("kv_pair_index")
data class ChatKeyValueIndex<T>(
    @PrimaryKey
    val key: ChatKeyValueIndexKey<T>,
)

@PrimaryKeyClass
data class ChatKeyValueIndexKey<T>(
    @PrimaryKeyColumn(name = "field", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
    val field: String,
    @PrimaryKeyColumn(name = "value", type = PrimaryKeyType.PARTITIONED, ordinal = 1)
    val value: String,
    @PrimaryKeyColumn(name = "id", type = PrimaryKeyType.CLUSTERED, ordinal = 2)
    override val id: T,
) : Key<T> {
    @Transient
    override val empty: Boolean = false
}

/**
 * The same rows keyed by the entity id.
 *
 * rem() receives the entity key alone. Without this table it could not name
 * the partitions that hold its rows, and index removal would silently leave
 * them. That failure already exists on the topic index, where rem builds a
 * row with an empty name and never matches.
 */
@Table("kv_pair_index_by_id")
data class ChatKeyValueIndexById<T>(
    @PrimaryKey
    val key: ChatKeyValueIndexByIdKey<T>,
)

@PrimaryKeyClass
data class ChatKeyValueIndexByIdKey<T>(
    @PrimaryKeyColumn(name = "id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
    override val id: T,
    @PrimaryKeyColumn(name = "field", type = PrimaryKeyType.CLUSTERED, ordinal = 1)
    val field: String,
    @PrimaryKeyColumn(name = "value", type = PrimaryKeyType.CLUSTERED, ordinal = 2)
    val value: String,
) : Key<T> {
    @Transient
    override val empty: Boolean = false
}
