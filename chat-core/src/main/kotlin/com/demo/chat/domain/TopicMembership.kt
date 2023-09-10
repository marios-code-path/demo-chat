package com.demo.chat.domain

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName

// I'd like to make memberships cross relational in the chat domain, thus
// I've parameterized the types for Member, and Member-Of
// Database Key = DK
// Member Key = MK
// topic Key = TK
@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
@JsonTypeName("membership")
interface TopicMembership<T> {
    val key: T   // Key<T> !!!
    val memberOf: T
    val member: T

    companion object Factory {
        fun <T> create(k: T, m: T, mOf: T): TopicMembership<T> = object : TopicMembership<T> {
            override val key: T
                get() = k
            override val member: T
                get() = m
            override val memberOf: T
                get() = mOf
        }
    }
}