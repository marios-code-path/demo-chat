package com.demo.chat.domain

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import java.util.*

// I'd like to make memberships cross relational in the chat domain, thus
// I've parameterized the types for Member, and Member-Of
// Database Key = DK
// Member Key = MK
// topic Key = TK
@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
@JsonTypeName("Membership")
interface Membership<S> {
    val key: Key<out S>
    val memberOf: Key<out S>
    val member: Key<out S>

    companion object Factory {
        fun <KT> create(k: Key<out KT>, m: Key<out KT>, mof: Key<out KT>): Membership<KT> = object : Membership<KT> {
            override val key: Key<out KT>
                get() = k
            override val member: Key<out KT>
                get() = m
            override val memberOf: Key<out KT>
                get() = mof
        }
    }
}