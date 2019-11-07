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
interface Membership<KT : EventKey> {
    val key: KT
    val memberOf: KT
    val member: KT

    companion object Factory {
        fun <KT : EventKey> create(k: KT, mof: KT, m: KT): Membership<KT> = object : Membership<KT> {
            override val key: KT
                get() = k
            override val member: KT
                get() = m
            override val memberOf: KT
                get() = mof
        }
    }
}

interface RoomMembership : Membership<EventKey> {
    companion object Factory {
        fun create(k: EventKey, mof: EventKey, m: EventKey): RoomMembership = object : RoomMembership {
            override val key: EventKey
                get() = k
            override val member: EventKey
                get() = m
            override val memberOf: EventKey
                get() = mof
        }
    }
}