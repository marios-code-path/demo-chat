package com.demo.chat.domain.cassandra

import com.datastax.driver.core.DataType
import com.demo.chat.domain.Key
import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.CassandraType
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn
import org.springframework.data.cassandra.core.mapping.Table
import org.springframework.data.cassandra.core.mapping.UserDefinedType
import java.util.*

@UserDefinedType("event_key_type")
data class CassandraUUIDKeyType<T>(
        @CassandraType(type = DataType.Name.UUID)
        override val id: T) : Key<T>

@Table("keys")
data class CassandraKey<T>(
        @PrimaryKeyColumn(name = "id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
        override val id: T,
        val kind: String
) : Key<T>