package com.demo.chat.domain.cassandra

import com.demo.chat.domain.TopicMembership
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
@Table("topic_membership")
data class TopicMembership<T>(
        @PrimaryKey("id")
        override val key: T,
        @Column("member")
        override val member: T,
        @Column("memberOf")
        override val memberOf: T
) : TopicMembership<T>

@Table("topic_membership_by_member")
data class TopicMembershipByMember<T>(
        @Column("id")
        override val key: T,
        @PrimaryKey("member")
        override val member: T,
        @Column("memberOf")
        override val memberOf: T
) : TopicMembership<T>

@Table("topic_membership_by_member_of")
data class TopicMembershipByMemberOf<T>(
        @Column("id")
        override val key: T,
        @Column("member")
        override val member: T,
        @PrimaryKey("memberOf")
        override val memberOf: T
) : TopicMembership<T>
