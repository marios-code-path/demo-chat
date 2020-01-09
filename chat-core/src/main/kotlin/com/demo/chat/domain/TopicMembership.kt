package com.demo.chat.domain

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName

// I'd like to make memberships cross relational in the chat domain, thus
// I've parameterized the types for Member, and Member-Of
// Database Key = DK
// Member Key = MK
// topic Key = TK
@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
@JsonTypeName("Membership")
interface TopicMembership<S> {
    val key: Key<S>
    val memberOf: Key<S>
    val member: Key<S>

    companion object Factory {
        fun <T> create(k: Key<T>, m: Key<T>, mof: Key<T>): TopicMembership<T> = object : TopicMembership<T> {
            override val key: Key<T>
                get() = k
            override val member: Key<T>
                get() = m
            override val memberOf: Key<T>
                get() = mof
        }
    }
}