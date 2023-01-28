package com.demo.chat.domain

import org.springframework.boot.context.properties.bind.ConstructorBinding

data class AuthorizationRequest<T> @ConstructorBinding constructor(
    val principal: Key<T>,
    val target: Key<T>,
    val permission: String,
    val expires: Long
)
