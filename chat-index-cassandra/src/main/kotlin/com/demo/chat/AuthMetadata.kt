package com.demo.chat

import com.demo.chat.domain.Key
import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn

class AuthMetadata {
}



@PrimaryKeyClass
data class AuthMetadataPrincipalKey<T>(
    @PrimaryKeyColumn(name = "principal", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
    override val id: T
) : Key<T>

@PrimaryKeyClass
data class AuthMetadataTargetKey<T>(
    @PrimaryKeyColumn(name = "target", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
    override val id: T
) : Key<T>