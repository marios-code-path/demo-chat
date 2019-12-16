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
interface Membership<KT : UUIDKey> {
    val key: KT
    val memberOf: KT
    val member: KT

    companion object Factory {
        fun <KT : UUIDKey> create(k: KT, mof: KT, m: KT): Membership<KT> = object : Membership<KT> {
            override val key: KT
                get() = k
            override val member: KT
                get() = m
            override val memberOf: KT
                get() = mof
        }
    }
}

interface TopicMembership : Membership<UUIDKey> {
    companion object Factory {
        fun create(k: UUIDKey, mof: UUIDKey, m: UUIDKey): TopicMembership = object : TopicMembership {
            override val key: UUIDKey
                get() = k
            override val member: UUIDKey
                get() = m
            override val memberOf: UUIDKey
                get() = mof
        }
    }
}