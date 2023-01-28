package com.demo.chat.init.domain

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding

@ConfigurationProperties("app.init")
data class BootstrapProperties @ConstructorBinding constructor(
    val roles: BootstrapRoles,
    val users: Map<String, BootstrapUser>
)

data class BootstrapRoles @ConstructorBinding constructor(
    val rolesAllowed: Array<String>,
    val wildcard: String
)

data class BootstrapUser @ConstructorBinding constructor(
    val name: String,
    val handle: String,
    val imageUri: String,
    val roles: Array<RoleDefinition>
)

data class RoleDefinition @ConstructorBinding constructor(
    val role: String,
    val user: String
)