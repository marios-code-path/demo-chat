package com.demo.chat.domain

import com.datastax.driver.core.DataType
import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.CassandraType
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn
import org.springframework.data.cassandra.core.mapping.Table
import org.springframework.data.cassandra.core.mapping.UserDefinedType
import java.util.*

@UserDefinedType("event_key_type")
data class CassandraEventKeyType(
        @CassandraType(type = DataType.Name.UUID)
        override val id: UUID) : EventKey

@Table("keys")
data class CassandraEventKey(
        @PrimaryKeyColumn(name = "id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
        override val id: UUID,
        val kind: String
) : EventKey
