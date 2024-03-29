package com.demo.chat.persistence.cassandra.domain

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
data class TopicMembershipByKey<T>(
        @PrimaryKey("id")
        override val key: T,
        @field:Column("member")
        override val member: T,
        @field:Column("member_of")
        override val memberOf: T
) : TopicMembership<T>