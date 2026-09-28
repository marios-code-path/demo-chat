package com.demo.chat.index.cassandra.domain

import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.*

/**
 * A row of a Cassandra index. **It is not a domain object and not a `Key`.**
 * The index maps it under the root of its domain. See `CHAT-avduuqwp`.
 *
 * The grant id is a clustering column. One target holds many grants, and one
 * principal holds many grants. Without it a second grant replaces the first
 * row. `CHAT-rmxxtwtu` holds that defect.
 */
@Table("auth_metadata_principal")
data class AuthMetadataByPrincipal<T>(
    @PrimaryKey
    val key: AuthMetadataByPrincipalKey<T>,
    @field:Column("target")
    val targetId: T,
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
data class AuthMetadataByPrincipalKey<T>(
    @PrimaryKeyColumn(name = "principal", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
    val principalId: T,
    @PrimaryKeyColumn(name = "id", type = PrimaryKeyType.CLUSTERED, ordinal = 1)
    val id: T
)

@Table("auth_metadata_target")
data class AuthMetadataByTarget<T>(
    @PrimaryKey
    val key: AuthMetadataByTargetKey<T>,
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
data class AuthMetadataByTargetKey<T>(
    @PrimaryKeyColumn(name = "target", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
    val targetId: T,
    @PrimaryKeyColumn(name = "id", type = PrimaryKeyType.CLUSTERED, ordinal = 1)
    val id: T
)

/**
 * The same rows keyed by the grant id.
 *
 * `rem` receives the grant key alone. `CoreAuthorizationService` removes the
 * domain row before it calls the index, so the removal cannot read the target
 * and the principal from the domain store. This table carries both.
 * See `CHAT-rmxxtwtu` and the `kv_pair_index_by_id` precedent.
 */
@Table("auth_metadata_by_id")
data class AuthMetadataById<T>(
    @PrimaryKey("id")
    val keyId: T,
    @field:Column("target")
    val targetId: T,
    @field:Column("principal")
    val principalId: T,
    @field:Column("target_root")
    val targetRoot: T,
    @field:Column("principal_root")
    val principalRoot: T
)
