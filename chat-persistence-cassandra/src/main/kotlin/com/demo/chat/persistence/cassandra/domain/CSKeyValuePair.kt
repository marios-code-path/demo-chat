package com.demo.chat.persistence.cassandra.domain

import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.*

/**
 * A row of the Cassandra backend. **It is not a domain object and not a
 * `Key`.** The store maps it under the root of its domain. See `CHAT-avduuqwp`.
 */
@Table("kv_pair")
data class CSKeyValuePair<T>(
    @PrimaryKey
    val key: KVKey<T>,
    @field:Column("vdata")
    val data: String
)

@PrimaryKeyClass
data class KVKey<T>(
    @PrimaryKeyColumn(name = "id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
    val id: T
)
