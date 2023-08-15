package com.demo.chat.persistence.cassandra.domain

import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import org.springframework.data.annotation.Transient
import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.*

@Table("kv_pair")
data class CSKeyValuePair<T>(
    @PrimaryKey
    override val key: KVKey<T>,
    @field:Column("vdata")
    override val data: String
) : KeyValuePair<T, Any>

@PrimaryKeyClass
data class KVKey<T>(
    @PrimaryKeyColumn(name = "id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
    override val id: T
) : Key<T> {
    @Transient
    override val empty: Boolean = false
}