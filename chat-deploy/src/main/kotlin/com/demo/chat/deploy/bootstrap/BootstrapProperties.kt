package com.demo.chat.deploy.bootstrap

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding

@ConditionalOnProperty("app.bootstrap.init")
@ConfigurationProperties("app.init")
data class BootstrapProperties @ConstructorBinding constructor(
    val roles: BootstrapRoles,
    val users: Map<String, BootstrapUser>
)

data class BootstrapRoles @ConstructorBinding constructor(
    val rolesAllowed: Array<String>,
    val wildcard: String,
    val initialRoles: Array<RoleDefinition>
)

data class BootstrapUser @ConstructorBinding constructor(
    val name: String,
    val handle: String,
    val imageUri: String,
)

data class RoleDefinition @ConstructorBinding constructor(
    val user: String,
    val target: String,
    val role: String,
)