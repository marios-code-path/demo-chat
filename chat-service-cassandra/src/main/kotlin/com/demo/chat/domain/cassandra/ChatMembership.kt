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
open class ChatMembership<S>(
        @PrimaryKey
        override val key: ChatMembershipKey<S>,
        @Column("member")
        @CassandraType(type = DataType.Name.UDT, userTypeName = "event_key_type")
        override val member: CassandraUUIDKeyType<S>,
        @Column("memberOf")
        @CassandraType(type = DataType.Name.UDT, userTypeName = "event_key_type")
        override val memberOf: CassandraUUIDKeyType<S>
) : TopicMembership<S>

// TODO This is incomplete abstraction!
@Table("chat_membership_by_member")
open class ChatMembershipByMember<S>(
        @Column("id")
        override val key: CassandraUUIDKeyType<S>,
        @PrimaryKey
        override val member: ChatMembershipKeyByMember<S>,
        @Column("memberOf")
        override val memberOf: CassandraUUIDKeyType<S>
) : TopicMembership<S>

@Table("chat_membership_by_memberof")
open class ChatMembershipByMemberOf<S>(
        @Column("id")
        override val key: CassandraUUIDKeyType<S>,
        @Column("member")
        override val member: CassandraUUIDKeyType<S>,
        @PrimaryKey
        override val memberOf: ChatMembershipKeyByMemberOf<S>
) : TopicMembership<S>

@PrimaryKeyClass
data class ChatMembershipKeyByMember<S>(
        @PrimaryKeyColumn(name = "member", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
        override val id: S) : Key<S>

@PrimaryKeyClass
data class ChatMembershipKeyByMemberOf<S>(
        @PrimaryKeyColumn(name = "memberOf", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
        override val id: S) : Key<S>

@PrimaryKeyClass
data class ChatMembershipKey<S>(
        @PrimaryKeyColumn(name = "id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
        override val id: S) : Key<S>