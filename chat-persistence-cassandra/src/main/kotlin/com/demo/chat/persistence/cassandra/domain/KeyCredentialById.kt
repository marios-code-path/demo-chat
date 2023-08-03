package com.demo.chat.persistence.cassandra.domain

import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyDataPair
import org.springframework.data.annotation.Transient
import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.*

@Table("chat_secret")
data class KeyCredentialById<T>(
    @PrimaryKey
    override val key: CredKey<T>,
    @field:Column("data")
    override val data: String
) : KeyDataPair<T, String>

@PrimaryKeyClass
data class CredKey<T>(
    @PrimaryKeyColumn(name = "id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
    override val id: T,
    val kind: String
) : Key<T> {
    @Transient
    override val empty: Boolean = false
}