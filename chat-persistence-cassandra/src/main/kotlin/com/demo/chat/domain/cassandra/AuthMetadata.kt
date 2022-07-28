package com.demo.chat.domain.cassandra

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.Key
import org.springframework.data.annotation.Transient
import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.*

@Table("auth_metadata")
data class AuthMetadataById<T>(
    @PrimaryKey
    override val key: AuthMetadataIdKey<T>,
    @Column("target")
    val targetId: T,
    @Column("principal")
    val principalId: T,
    @Column("permission")
    override val permission: String,
    @Column("expires")
    override val expires: Long
) : AuthMetadata<T> {
    @Transient
    override val principal: Key<T> = Key.funKey(principalId)

    @Transient
    override val target: Key<T> = Key.funKey(targetId)
}

@PrimaryKeyClass
data class AuthMetadataIdKey<T>(
    @PrimaryKeyColumn(name = "id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
    override val id: T
) : Key<T> {
    @Transient
    override val empty: Boolean = false
}