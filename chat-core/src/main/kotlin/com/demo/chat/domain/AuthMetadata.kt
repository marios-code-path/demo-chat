package com.demo.chat.domain

interface AuthMetadata<T, out P> {
    val key: Key<T>
    val principal: Key<T>
    val target: Key<T>
    val permission: P
    val expires: Long
}

data class StringRoleAuthorizationMetadata<T>(
    override val key: Key<T>,
    override val principal: Key<T>,
    override val target: Key<T>,
    override val permission: String,
    override val expires: Long = 0L
) : AuthMetadata<T, String>