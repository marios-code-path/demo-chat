package com.demo.chat.domain

import com.datastax.driver.core.DataType
import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.*
import java.util.*

@Table("chat_membership")
open class ChatMembership(
        @PrimaryKey
        override val key: MemberShipKey,
        @Column("member")
        @CassandraType(type = DataType.Name.UDT, userTypeName = "event_key_type")
        override val member: DSEventKey,
        @Column("memberOf")
        @CassandraType(type = DataType.Name.UDT, userTypeName = "event_key_type")
        override val memberOf: DSEventKey
) : RoomMembership

// TODO This is incomplete abstraction!
@Table("chat_membership_by_member")
open class ChatMembershipByMember(
        @Column("id")
        override val key: DSEventKey,
        @PrimaryKey
        override val member: MemberShipKeyByMember,
        @Column("memberOf")
        override val memberOf: DSEventKey
) : RoomMembership

@Table("chat_membership_by_memberof")
open class ChatMembershipByMemberOf(
        @Column("id")
        override val key: DSEventKey,
        @Column("member")
        override val member: DSEventKey,
        @PrimaryKey
        override val memberOf: MemberShipKeyByMemberOf
) : RoomMembership

@PrimaryKeyClass
data class MemberShipKeyByMember(
        @PrimaryKeyColumn(name = "member", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
        override val id: UUID) : EventKey

@PrimaryKeyClass
data class MemberShipKeyByMemberOf(
        @PrimaryKeyColumn(name = "memberOf", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
        override val id: UUID) : EventKey

@PrimaryKeyClass
data class MemberShipKey(
        @PrimaryKeyColumn(name = "id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
        override val id: UUID) : EventKey


@UserDefinedType("event_key_type")
data class DSEventKey(
        @CassandraType(type = DataType.Name.UUID)
        override val id: UUID) : EventKey