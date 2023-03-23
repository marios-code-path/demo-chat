package com.demo.chat.config.deploy.init

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding

@ConditionalOnProperty("app.users.create", havingValue = "true")
@EnableConfigurationProperties(InitializationProperties::class)
@ConfigurationProperties("app.init")
data class InitializationProperties @ConstructorBinding constructor(
    val initialRoles: InitalRoles,
    val initialUsers: Map<String, UserDefinition>
)

data class InitalRoles @ConstructorBinding constructor(
    val rolesAllowed: Array<String>,
    val wildcard: String,
    val roles: Array<RoleDefinition>
)

data class UserDefinition @ConstructorBinding constructor(
    val name: String,
    val handle: String,
    val imageUri: String,
)

data class RoleDefinition @ConstructorBinding constructor(
    val user: String,
    val target: String,
    val role: String,
)