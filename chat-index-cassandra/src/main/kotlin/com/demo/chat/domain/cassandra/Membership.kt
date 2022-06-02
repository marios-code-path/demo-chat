package com.demo.chat.domain.cassandra

import com.demo.chat.domain.TopicMembership
import org.springframework.data.cassandra.core.mapping.Column
import org.springframework.data.cassandra.core.mapping.PrimaryKey
import org.springframework.data.cassandra.core.mapping.Table

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
