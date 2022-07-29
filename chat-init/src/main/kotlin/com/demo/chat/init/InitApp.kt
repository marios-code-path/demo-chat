package com.demo.chat.init

import com.demo.chat.UserCreateRequest
import com.demo.chat.client.rsocket.config.RSocketClientProperties
import com.demo.chat.domain.*
import com.demo.chat.domain.serializers.DefaultChatJacksonModules
import com.demo.chat.init.domain.BootstrapProperties
import com.demo.chat.secure.rsocket.UnprotectedConnection
import com.demo.chat.service.edge.ChatUserService
import com.demo.chat.service.security.AuthorizationService
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.rsocket.RSocketRequesterAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Profile
import org.springframework.security.authentication.AuthenticationManager

/**
 * Test Class
 */
@Profile("init")
@SpringBootApplication
@EnableConfigurationProperties(RSocketClientProperties::class, BootstrapProperties::class)
@Import(
    RSocketRequesterAutoConfiguration::class,
    DefaultChatJacksonModules::class,
    UnprotectedConnection::class
)
class InitApp {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<InitApp>(*args)
        }
    }

    @Bean
    fun <T> initOnce(
        userService: ChatUserService<T>,
        authorizationService: AuthorizationService<T, AuthMetadata<T>, AuthMetadata<T>>,
        authenticationManager: AuthenticationManager,
        bootstrapProperties: BootstrapProperties,
        typeUtil: TypeUtil<T>
    ): CommandLineRunner = CommandLineRunner {
        val emptyKey = Key.emptyKey(typeUtil.assignFrom(Any()))
        val identityKeys = mutableMapOf<String, Key<T>>()

        // add users
        bootstrapProperties.users.keys.forEach { identity ->
            val thisUser = bootstrapProperties.users[identity]!!

            identityKeys[identity] = userService.addUser(
                UserCreateRequest(
                    thisUser.name,
                    thisUser.handle,
                    thisUser.imageUri
                )
            ).block()!!
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
        println("Initialization Complete.")
    }
}