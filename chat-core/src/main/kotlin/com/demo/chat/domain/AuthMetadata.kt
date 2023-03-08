package com.demo.chat.domain

interface AuthMetadata<T> : KeyBearer<T> {
    override val key: Key<T>
    val principal: Key<T>
    val target: Key<T>
    val permission: String
    val mute: Boolean
    val expires: Long

    companion object Factory {
        @Deprecated("Use muted constructor instead")
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
                override val mute: Boolean
                    get() = false
                override val expires: Long
                    get() = exp
            }
        fun <T> create(key: Key<T>, principal: Key<T>, target: Key<T>, perm: String, muted: Boolean, exp: Long): AuthMetadata<T> =
            object : AuthMetadata<T> {
                override val key: Key<T>
                    get() = key
                override val principal: Key<T>
                    get() = principal
                override val target: Key<T>
                    get() = target
                override val permission: String
                    get() = perm
                override val mute: Boolean
                    get() = muted
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
    override val mute: Boolean = false,
    override val expires: Long = 0L
) : AuthMetadata<T> {
    constructor(key: Key<T>, principal: Key<T>, target: Key<T>, perm: String, exp: Long) : this(key, principal, target, perm, false, exp)
}