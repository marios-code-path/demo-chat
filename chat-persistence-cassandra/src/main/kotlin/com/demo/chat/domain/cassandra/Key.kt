package com.demo.chat.domain.cassandra

import com.demo.chat.domain.Key
import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn
import org.springframework.data.cassandra.core.mapping.Table

@Table("keys")
data class CSKey<T>(
        @PrimaryKeyColumn(name = "id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
        override val id: T,
        val kind: String
) : Key<T>