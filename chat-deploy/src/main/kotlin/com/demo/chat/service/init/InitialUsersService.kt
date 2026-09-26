package com.demo.chat.service.init

import com.demo.chat.config.deploy.init.UserInitializationProperties
import com.demo.chat.domain.knownkey.ChatIdentity
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

        loadIdentities(rootKeys, identityKeys)

        val initialRoles: MutableSet<AuthMetadata<T>> = mutableSetOf()

        // get role definitions
        initializationProperties.initialRoles.roles.forEach { permission ->
            val user = rootKeys.byName(permission.user)
            val target = rootKeys.byName(permission.target)
            if (user != null && target != null) {
                initialRoles.add(
                    StringRoleAuthorizationMetadata(
                        emptyKey,
                        user,
                        target,
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

    /**
     * This method loads the two identities from the initial users. Each
     * initial user name must parse to a [ChatIdentity]. Both identities must be
     * present. See `CHAT-avduuqwp`.
     */
    private fun loadIdentities(rootKeys: RootKeys<T>, identityKeys: Map<String, Key<T>>) {
        val unknown = identityKeys.keys.filter { ChatIdentity.parse(it) == null }
        if (unknown.isNotEmpty()) throw ChatException("An initial user names an unknown identity: $unknown")
        val admin = identityKeys[ChatIdentity.ADMIN.wireName]
            ?: throw ChatException("The initial users do not name the ${ChatIdentity.ADMIN.wireName} identity.")
        val anon = identityKeys[ChatIdentity.ANON.wireName]
            ?: throw ChatException("The initial users do not name the ${ChatIdentity.ANON.wireName} identity.")
        rootKeys.loadIdentities(admin, anon)
    }
}