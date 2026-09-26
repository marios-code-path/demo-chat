package com.demo.chat.index.cassandra.domain

import org.springframework.data.cassandra.core.mapping.*

/**
 * A row of a Cassandra index. **It is not a domain object and not a `Key`.**
 * The index maps it under the root of its domain. See `CHAT-avduuqwp`.
 */
@Table("auth_metadata_principal")
data class AuthMetadataByPrincipal<T>(
    @field:Column("id")
    val keyId: T,
    @field:Column("target")
    val targetId: T,
    @PrimaryKey("principal")
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

@Table("auth_metadata_target")
data class AuthMetadataByTarget<T>(
    @field:Column("id")
    val keyId: T,
    @PrimaryKey("target")
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
