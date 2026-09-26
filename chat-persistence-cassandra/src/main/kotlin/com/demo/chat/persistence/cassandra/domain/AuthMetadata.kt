package com.demo.chat.persistence.cassandra.domain

import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.*

/**
 * A row of the Cassandra backend. **It is not a domain object and not a
 * `Key`.** The store maps it under the root of its domain. See `CHAT-avduuqwp`.
 */
@Table("auth_metadata")
data class AuthMetadataById<T>(
    @PrimaryKey
    val key: AuthMetadataIdKey<T>,
    @field:Column("target")
    val targetId: T,
    @field:Column("principal")
    val principalId: T,
    @field:Column("target_root")
    val targetRoot: T,
    @field:Column("principal_root")
    val principalRoot: T,
    @field:Column("permission")
    val permission: String,
    @field:Column("mute")
    val mute: Boolean,
    @field:Column("expires")
    val expires: Long
)

@PrimaryKeyClass
data class AuthMetadataIdKey<T>(
    @PrimaryKeyColumn(name = "id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
    val id: T
)
