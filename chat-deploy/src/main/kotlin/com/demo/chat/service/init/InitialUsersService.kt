package com.demo.chat.service.init

import com.demo.chat.config.deploy.init.UserInitializationProperties
import com.demo.chat.domain.*
import com.demo.chat.domain.knownkey.Admin
import com.demo.chat.domain.knownkey.Anon
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.composite.ChatUserService
import com.demo.chat.service.security.AuthorizationService
import com.demo.chat.service.security.KeyCredential
import com.demo.chat.service.security.SecretsStore
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class InitialUsersService<T>(
    private val userService: ChatUserService<T>,
    private val authorizationService: AuthorizationService<T, AuthMetadata<T>>,
    private val secretsStore: SecretsStore<T>,
    private val initializationProperties: UserInitializationProperties,
    private val typeUtil: TypeUtil<T>,
) {

    fun initializeUsers(rootKeys: RootKeys<T>): Map<String, Key<T>> {
        val emptyKey = Key.emptyKey(typeUtil.assignFrom(Any()))
        val identityKeys = mutableMapOf<String, Key<T>>()

        // add users
        initializationProperties.initialUsers.keys.forEach { identity ->
            val thisUser = initializationProperties.initialUsers[identity]!!

            val thisUserKey = Mono.from(
                userService.addUser(
                    UserCreateRequest(
                        thisUser.name,
                        thisUser.handle,
                        thisUser.imageUri
                    )
                )
            )
                .defaultIfEmpty(emptyKey)
                .onErrorResume {
                    userService
                        .findByUsername(ByStringRequest(thisUser.handle))
                        .map { u -> u.key }
                        .last()
                }
                .block()

            identityKeys[identity] = thisUserKey!!
        }

        val mapOfKeys = mutableMapOf<String, Key<T>>()

        val anonKey = identityKeys["Anon"]!!
        val adminKey = identityKeys["Admin"]!!

        secretsStore
            .addCredential(KeyCredential(adminKey, "changeme"))
            .block()

        mapOfKeys[Admin::class.java.simpleName] = adminKey
        mapOfKeys[Anon::class.java.simpleName] = anonKey
        rootKeys.merge(mapOfKeys)

        val initialRoles: MutableSet<AuthMetadata<T>> = mutableSetOf()

        // get role definitions
        initializationProperties.initialRoles.roles.forEach { permission ->
            if (rootKeys.hasKey(permission.target) && rootKeys.hasKey(permission.user)) {
                initialRoles.add(
                    StringRoleAuthorizationMetadata(
                        emptyKey,
                        rootKeys.getRootKey(permission.user)!!,
                        rootKeys.getRootKey(permission.target)!!,
                        permission.role,
                    )
                )
            } else {
                println("Missing root key for ${permission.user} or ${permission.target}")
            }
        }

        // set permissions
        Flux.fromIterable(initialRoles)
            .flatMap { authMeta ->
                authorizationService.authorize(authMeta, true)
            }.blockLast()

        return mapOfKeys
    }
}