package com.demo.chat.domain

import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
data class AuthorizationRequest<T>(
    val principal: Key<T>,
    val target: Key<T>,
    val permission: String,
    val expires: Long
)
