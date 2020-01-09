package com.demo.chat.domain.cassandra

import com.datastax.driver.core.DataType
import com.demo.chat.domain.Key
import com.demo.chat.domain.TopicMembership
import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.*

// memberships can be abstracted away from services
// by making every topic send it's id to the joining member
//  create boolean tables of id's seen:
// user 1 joins user 2's room
// user 2's room joins user 1's room
//  Thus
// user 1 sends 1 -> 2
// user 2 sends 2 -> 1
// now both topics look like { 1: [2], 2: [1] }
// when user 2 joins another room say 3's room
// now both topics look like { 1: [2], 2: [1,3] }
// user 3's topic looks like { 3: [2] }
// user 2 sends 2 -> 3
// now our user's topics look like { 1: [2], 2:[1], 3:[] }
// Thus the effect of having topics operate like boolean maps
// we can control this behaviour by adding a flagging the message 'invisible'
// thus to gain the effect of our above sample,
// topic membership = reduce (0, this-id && visible == 1) for each id in topic
// !! must have a user_membership_topic seperate from user_stat_topic for this to work
//
@Table("chat_membership")
open class ChatMembership<T>(
        @PrimaryKey
        override val key: ChatMembershipKey<T>,
        @Column("member")
        @CassandraType(type = DataType.Name.UDT, userTypeName = "event_key_type")
        override val member: CassandraUUIDKeyType<T>,
        @Column("memberOf")
        @CassandraType(type = DataType.Name.UDT, userTypeName = "event_key_type")
        override val memberOf: CassandraUUIDKeyType<T>
) : TopicMembership<T>

// TODO This is incomplete abstraction!
@Table("chat_membership_by_member")
open class ChatMembershipByMember<T>(
        @Column("id")
        override val key: CassandraUUIDKeyType<T>,
        @PrimaryKey
        override val member: ChatMembershipKeyByMember<T>,
        @Column("memberOf")
        override val memberOf: CassandraUUIDKeyType<T>
) : TopicMembership<T>

@Table("chat_membership_by_memberof")
open class ChatMembershipByMemberOf<T>(
        @Column("id")
        override val key: CassandraUUIDKeyType<T>,
        @Column("member")
        override val member: CassandraUUIDKeyType<T>,
        @PrimaryKey
        override val memberOf: ChatMembershipKeyByMemberOf<T>
) : TopicMembership<T>

@PrimaryKeyClass
data class ChatMembershipKeyByMember<T>(
        @PrimaryKeyColumn(name = "member", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
        override val id: T) : Key<T>

@PrimaryKeyClass
data class ChatMembershipKeyByMemberOf<T>(
        @PrimaryKeyColumn(name = "memberOf", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
        override val id: T) : Key<T>

@PrimaryKeyClass
data class ChatMembershipKey<T>(
        @PrimaryKeyColumn(name = "id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
        override val id: T) : Key<T>