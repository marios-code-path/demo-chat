package com.demo.chat.init.domain

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConfigurationProperties("app.init")
@ConstructorBinding
data class BootstrapProperties(
    val roles: BootstrapRoles,
    val users: Map<String, BootstrapUser>
)

@ConstructorBinding
data class BootstrapRoles(
    val rolesAllowed: Array<String>,
    val wildcard: String
)

@ConstructorBinding
data class BootstrapUser(
    val name: String,
    val handle: String,
    val imageUri: String,
    val roles: Array<String>
)