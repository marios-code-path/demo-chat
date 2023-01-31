package com.demo.chat.domain.cassandra

import com.demo.chat.domain.TopicMembership
import org.springframework.data.cassandra.core.mapping.Column
import org.springframework.data.cassandra.core.mapping.PrimaryKey
import org.springframework.data.cassandra.core.mapping.Table

@Table("topic_membership_by_member")
data class TopicMembershipByMember<T>(
    @field:Column("id")
    override val key: T,
    @PrimaryKey("member")
    override val member: T,
    //@field:Column("memberOf")
    override val memberOf: T
) : TopicMembership<T>

@Table("topic_membership_by_member_of")
data class TopicMembershipByMemberOf<T>(
    @field:Column("id")
    override val key: T,
    @field:Column("member")
    override val member: T,
    @PrimaryKey
    override val memberOf: T
) : TopicMembership<T>
