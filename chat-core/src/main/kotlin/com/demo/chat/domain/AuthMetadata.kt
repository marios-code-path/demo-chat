package com.demo.chat.domain

interface AuthMetadata<T, out P> {
    val key: Key<T>
    val principal: Key<T>
    val target: Key<T>
    val permission: P
    val expires: Long

    companion object Factory {
        fun <T, P> create(k: Key<T>, ppl: Key<T>, targ: Key<T>, perm: P, exp: Long): AuthMetadata<T, P> = object : AuthMetadata<T, P> {
            override val key: Key<T>
                get() = k
            override val principal: Key<T>
                get() = ppl
            override val target: Key<T>
                get() = targ
            override val permission: P
                get() = perm
            override val expires: Long
                get() = exp
        }
    }
}

data class StringRoleAuthorizationMetadata<T>(
    override val key: Key<T>,
    override val principal: Key<T>,
    override val target: Key<T>,
    override val permission: String,
    override val expires: Long = 0L
) : AuthMetadata<T, String>