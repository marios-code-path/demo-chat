package com.demo.chat.domain.cassandra

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.Key
import org.springframework.data.cassandra.core.mapping.Column
import org.springframework.data.cassandra.core.mapping.PrimaryKey
import org.springframework.data.cassandra.core.mapping.Table

//@Table("auth_metadata")
//data class AuthMeta<T>(
//    @PrimaryKey("id")
//    override val key: Key<T>,
//    @Column("principal")
//    override val principal: Key<T>,
//    @Column("target")
//    override val target: Key<T>,
//    @Column("permission")
//    override val permission: String,
//    @Column("expires")
//    override val expires: Long
//) : AuthMetadata<T>