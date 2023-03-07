package com.demo.chat.init.commands

import com.demo.chat.domain.*
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.init.domain.BootstrapProperties
import com.demo.chat.service.composite.ChatUserService
import com.demo.chat.service.core.IKeyGenerator
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.security.AuthorizationService
import org.springframework.context.annotation.Profile
import org.springframework.shell.standard.ShellComponent
import org.springframework.shell.standard.ShellMethod
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@ShellComponent
@Profile("shell")
class InitOnceCommands<T>(
    private val userService: ChatUserService<T>,
    private val authorizationService: AuthorizationService<T, AuthMetadata<T>>,
    private val bootstrapProperties: BootstrapProperties,
    private val keyService: IKeyService<T>,
    private val typeUtil: TypeUtil<T>
) : CommandsUtil<T>(typeUtil) {

    fun generateRootKeys() {
        val rootKeys = RootKeys<T>()
        // Create key for each Domain Type
        Flux.just(
            User::class.java, Message::class.java, MessageTopic::class.java,
            TopicMembership::class.java, AuthMetadata::class.java
        )
            .flatMap { domain ->
                keyService.key(domain)
                    .map { key ->
                        rootKeys.addRootKey(domain, key)
                    }
            }
            .blockLast()


        // Assign ALL permission for SuperUser to each Root Key

        // Assign READ permission for each Root Key to Anonymous User

    }

    @ShellMethod("Bootstrap the system")
    fun bootstrap() {
        val emptyKey = Key.emptyKey(typeUtil.assignFrom(Any()))
        val identityKeys = mutableMapOf<String, Key<T>>()

        // add users
        bootstrapProperties.users.keys.forEach { identity ->
            val thisUser = bootstrapProperties.users[identity]!!

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
                ?.block()

            identityKeys[identity] = thisUserKey!!
        }

        val anonKey = identityKeys["anonymous"]!!
        val rootKey = identityKeys["root"]!!

        // set roles
        bootstrapProperties
            .users["anonymous"]!!.roles.forEach { permission ->
            authorizationService
                .authorize(
                    StringRoleAuthorizationMetadata(
                        emptyKey,
                        anonKey,
                        anonKey,
                        permission.role
                    ), true
                )
                .block()
        }

        bootstrapProperties
            .users["root"]!!.roles.forEach { permission ->
            authorizationService
                .authorize(
                    StringRoleAuthorizationMetadata(
                        emptyKey,
                        rootKey,
                        anonKey,
                        permission.role
                    ), true
                )
                .block()
        }
        println("Summary::(Save the keys for later use)")
        println(
            """
            |Anonymous Key: ${anonKey.id}
            |Root Key: ${rootKey.id}
        """.trimMargin()
        )

    }

}