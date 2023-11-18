package com.demo.chat.service.init

import com.demo.chat.config.deploy.init.UserInitializationProperties
import com.demo.chat.domain.*
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.composite.ChatUserService
import com.demo.chat.service.security.AuthorizationService
import com.demo.chat.service.security.KeyCredential
import com.demo.chat.service.security.SecretsStore
import org.springframework.security.crypto.password.PasswordEncoder
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class InitialUsersService<T>(
    private val userService: ChatUserService<T>,
    private val authorizationService: AuthorizationService<T, AuthMetadata<T>>,
    private val secretsStore: SecretsStore<T>,
    private val initializationProperties: UserInitializationProperties,
    private val passwordDecoder: PasswordEncoder,
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
                .onErrorResume {
                    userService
                        .findByUsername(ByStringRequest(thisUser.handle))
                        .map { u -> u.key }
                        .switchIfEmpty(Mono.error(ChatException("Cannot Initialize User ${thisUser.handle}")))
                        .single()
                }
                .defaultIfEmpty(emptyKey)
                .block()!!

            identityKeys[identity] = thisUserKey

            var encodedPassword = passwordDecoder.encode(thisUser.password)
            val thisCredential = KeyCredential(thisUserKey, "${encodedPassword}")

            secretsStore
                .addCredential(thisCredential)
                .block()
        }

        rootKeys.merge(identityKeys)

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
                println("Adding Permission ${authMeta.principal.id} -> ${authMeta.target.id} : ${authMeta.permission}, ${authMeta.mute}, ${authMeta.expires}")
                authorizationService.authorize(authMeta, true)
            }.blockLast()

        return identityKeys
    }
}