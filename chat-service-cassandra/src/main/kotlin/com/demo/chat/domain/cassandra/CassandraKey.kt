package com.demo.chat.domain.cassandra

import com.datastax.driver.core.DataType
import com.demo.chat.domain.Key
import org.springframework.data.annotation.Transient
import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.CassandraType
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn
import org.springframework.data.cassandra.core.mapping.Table
import org.springframework.data.cassandra.core.mapping.UserDefinedType
import java.util.*

// TODO Issue where Key<T> must have lower bounds when applying to Cassandra UDT
@UserDefinedType("event_key_type")
data class CassandraUUIDKeyType<T>(
        @Transient
        override val id: T) : Key<T> {
        @CassandraType(type = DataType.Name.UUID)
        val identity: UUID = id as UUID // codec.decode(id)
}
