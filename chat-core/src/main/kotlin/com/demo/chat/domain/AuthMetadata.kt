package com.demo.chat.domain

interface AuthMetadata<T> : KeyBearer<T> {
    override val key: Key<T>
    val principal: Key<T>
    val target: Key<T>
    val permission: String
    val expires: Long

    companion object Factory {
        fun <T> create(key: Key<T>, principal: Key<T>, target: Key<T>, perm: String, exp: Long): AuthMetadata<T> =
            object : AuthMetadata<T> {
                override val key: Key<T>
                    get() = key
                override val principal: Key<T>
                    get() = principal
                override val target: Key<T>
                    get() = target
                override val permission: String
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
) : AuthMetadata<T>