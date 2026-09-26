package com.demo.chat.persistence.cassandra.domain

import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn
import org.springframework.data.cassandra.core.mapping.Table

/**
 * A row of the key registry. It records the root of each minted id. **It is
 * not a `Key`.** See `CHAT-avduuqwp`.
 */
@Table("keys")
data class CSKeyRow<T>(
    @PrimaryKeyColumn(name = "id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
    val id: T,
    val root: T,
)
