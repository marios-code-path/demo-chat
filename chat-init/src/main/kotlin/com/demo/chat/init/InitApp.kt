package com.demo.chat.init

import com.demo.chat.domain.*
import com.demo.chat.init.domain.BootstrapProperties
import com.demo.chat.service.composite.ChatUserService
import com.demo.chat.service.security.AuthorizationService
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.*
import reactor.core.publisher.Mono

/**
 * Test Class
 * TODO: turn this into a command 'bootstrap'
 */
@Profile("init")
@Configuration
class InitApp{

    // TODO: NOTE - the anonymous key used here should be ignored.
    // This program will generate the anonymous key and admin keys
    @Bean
    fun <T> initOnce(
        userService: ChatUserService<T>,
        authorizationService: AuthorizationService<T, AuthMetadata<T>, AuthMetadata<T>>,
        bootstrapProperties: BootstrapProperties,
        typeUtil: TypeUtil<T>
    ): CommandLineRunner = CommandLineRunner {

        val emptyKey = Key.emptyKey(typeUtil.assignFrom(Any()))
        val identityKeys = mutableMapOf<String, Key<T>>()

        // add users
        bootstrapProperties.users.keys.forEach { identity ->
            val thisUser = bootstrapProperties.users[identity]!!

            val thisUserKey = Mono.from(userService.addUser(
                UserCreateRequest(
                    thisUser.name,
                    thisUser.handle,
                    thisUser.imageUri
                )
            ))
                .defaultIfEmpty(emptyKey)
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
        println("""
            |Anonymous Key: ${anonKey.id}
            |Root Key: ${rootKey.id}
        """.trimMargin())
    }
}