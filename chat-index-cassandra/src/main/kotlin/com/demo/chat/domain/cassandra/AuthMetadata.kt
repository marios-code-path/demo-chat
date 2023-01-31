package com.demo.chat.domain.cassandra

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.Key
import org.springframework.data.annotation.Transient
import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.*

@Table("auth_metadata_principal")
data class AuthMetadataByPrincipal<T>(
    @field:Column("id")
    val keyId: T,
    @field:Column("target")
    val targetId: T,
    @PrimaryKey("principal")
    val principalId: T,
    @field:Column("permission")
    override val permission: String,
    @field:Column("expires")
    override val expires: Long
) : AuthMetadata<T> {
    @Transient
    override val principal: Key<T> = Key.funKey(principalId)
    @Transient
    override val target: Key<T> = Key.funKey(targetId)
    @Transient
    override val key: Key<T> = Key.funKey(keyId)
}

@Table("auth_metadata_target")
data class AuthMetadataByTarget<T>(
    @field:Column("id")
    val keyId: T,
    @PrimaryKey("target")
    val targetId: T,
    @field:Column("principal")
    val principalId: T,
    @field:Column("permission")
    override val permission: String,
    @field:Column("expires")
    override val expires: Long
) : AuthMetadata<T> {
    @Transient
    override val principal: Key<T> = Key.funKey(principalId)
    @Transient
    override val target: Key<T> = Key.funKey(targetId)
    @Transient
    override val key: Key<T> = Key.funKey(keyId)
}
