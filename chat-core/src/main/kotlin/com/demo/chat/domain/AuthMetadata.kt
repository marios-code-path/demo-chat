package com.demo.chat.domain

import java.time.Instant

interface AuthMetadata<T> {
    val key: Key<T>
    val principal: Key<T>
    val target: Key<T>
    val permission: String
    val expires: Long
    //val timestamp: Instant

    companion object Factory {
        fun <T> create(k: Key<T>, ppl: Key<T>, targ: Key<T>, perm: String, exp: Long): AuthMetadata<T> = object : AuthMetadata<T> {
            override val key: Key<T>
                get() = k
            override val principal: Key<T>
                get() = ppl
            override val target: Key<T>
                get() = targ
            override val permission: String
                get() = perm
            override val expires: Long
                get() = exp
//            override val timestamp: Instant
//                get() = ts
        }
    }
}

data class StringRoleAuthorizationMetadata<T>(
    override val key: Key<T>,
    override val principal: Key<T>,
    override val target: Key<T>,
    override val permission: String,
    override val expires: Long = 0L
) : AuthMetadata<T>